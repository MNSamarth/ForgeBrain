package com.forgebrain.backend.services;

import static com.forgebrain.backend.services.ReviewFixtures.reviewResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.Topic.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ReelAnalyticsServiceImpl} — per this mission's Part 7 ("analytics
 * snapshot creation", "memory feedback updates", "report generation", "ingestion from job
 * completion", "handling failed and skipped reels"). No Spring context, no real platform
 * credentials; memory is a lightweight in-memory fake so the feedback path is verified without
 * file I/O.
 */
class ReelAnalyticsServiceImplTest {

    @TempDir
    Path tempDir;

    private static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private ReelAnalyticsServiceImpl serviceWith(FakeMemoryService memoryService) {
        AnalyticsConfig config = new AnalyticsConfig(tempDir.resolve("snapshots").toString(),
                tempDir.resolve("reports").toString(), "1.0.0", 0.5, 0.05, 0.6, 0.8, 14);
        return new ReelAnalyticsServiceImpl(objectMapper(), config, memoryService);
    }

    @Test
    void recordsASnapshotFileForACompletedApprovedPublishedJob() {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = approvedPublishedJob();
        ReelJobReport report = reportFor(job, reviewResult(ReviewResult.Verdict.APPROVED), readyPublishingResult());

        ReelOutcomeSnapshot snapshot = service.recordOutcome(job, report, null, null, null);

        assertThat(snapshot.outcome()).isEqualTo(ReelOutcomeSnapshot.Outcome.PUBLISHED);
        assertThat(snapshot.jobId()).isEqualTo(job.jobId());
        assertThat(Files.isRegularFile(tempDir.resolve("snapshots").resolve(job.jobId() + ".json"))).isTrue();
    }

    @Test
    void feedsAnApprovedPublishedOutcomeBackIntoMemoryAsPostedWithAPerformanceScore() {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = approvedPublishedJob();
        ReelJobReport report = reportFor(job, reviewResult(ReviewResult.Verdict.APPROVED), readyPublishingResult());

        service.recordOutcome(job, report, null, null, null);

        MemoryState.TopicRecord updated = memory.records.get("topic-1");
        assertThat(updated).isNotNull();
        assertThat(updated.status()).isEqualTo(Status.POSTED);
        assertThat(updated.performanceScore()).isEqualTo(0.8);
    }

    @Test
    void feedsARejectedOutcomeBackIntoMemoryAsNeedsRevisitWithACooldown() {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "The Topic")
                .withReviewResult(reviewResult(ReviewResult.Verdict.REJECTED))
                .withPublishingStatus("SKIPPED_NOT_APPROVED").completed();
        ReelJobReport report = reportFor(job, reviewResult(ReviewResult.Verdict.REJECTED), null);

        service.recordOutcome(job, report, null, null, null);

        MemoryState.TopicRecord updated = memory.records.get("topic-1");
        assertThat(updated.status()).isEqualTo(Status.NEEDS_REVISIT);
        assertThat(updated.priority()).isEqualTo(MemoryState.Priority.HIGH);
        assertThat(updated.avoidUntil()).isNotNull();
    }

    @Test
    void aFailedJobIsCapturedAsAFailedSnapshotWithoutTouchingMemoryStatus() {
        FakeMemoryService memory = new FakeMemoryService();
        memory.records.put("topic-1", new MemoryState.TopicRecord("topic-1", "The Topic", Status.IN_PROGRESS,
                Topic.Difficulty.BEGINNER, 1, Instant.now(), null, 0, null, null, null, MemoryState.Priority.NORMAL,
                null, List.of(), null));
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "The Topic")
                .failed("RenderExecutionException: boom");
        ReelJobReport report = new ReelJobReport("job-1", "run-1", "topic-1", Instant.now(), Instant.now(),
                Duration.ofSeconds(5), List.of(), "FAILED", Map.of(), List.of(), List.of("boom"), List.of(), null,
                "n/a", null, null);

        ReelOutcomeSnapshot snapshot = service.recordOutcome(job, report, null, null, null);

        assertThat(snapshot.outcome()).isEqualTo(ReelOutcomeSnapshot.Outcome.FAILED);
        assertThat(snapshot.failureReason()).contains("boom");
        // A pipeline failure isn't a quality signal — status/priority are left exactly as they were.
        assertThat(memory.records.get("topic-1").status()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void skipsMemoryFeedbackWhenTheJobNeverReachedTopicSelection() {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().failed("AI_PIPELINE failed before topic selection");
        ReelJobReport report = new ReelJobReport("job-1", "run-1", null, Instant.now(), Instant.now(),
                Duration.ofSeconds(1), List.of(), "FAILED", Map.of(), List.of(), List.of("boom"), List.of(), null,
                "n/a", null, null);

        ReelOutcomeSnapshot snapshot = service.recordOutcome(job, report, null, null, null);

        assertThat(snapshot.topicId()).isNull();
        assertThat(memory.updateTopicRecordCalls).isZero();
    }

    @Test
    void generateReportAggregatesSnapshotsWithinTheWindowAndWritesJsonAndMarkdown() {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob approved = approvedPublishedJob();
        service.recordOutcome(approved, reportFor(approved, reviewResult(ReviewResult.Verdict.APPROVED),
                readyPublishingResult()), null, null, null);

        Instant now = Instant.now();
        AnalyticsReport report = service.generateReport(now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));

        assertThat(report.totalReelsAnalyzed()).isEqualTo(1);
        assertThat(Files.isRegularFile(tempDir.resolve("reports").resolve(report.reportId() + ".json"))).isTrue();
        assertThat(Files.isRegularFile(tempDir.resolve("reports").resolve(report.reportId() + ".md"))).isTrue();
    }

    @Test
    void findAllRoundTripsPersistedSnapshotsThroughJson() throws IOException {
        FakeMemoryService memory = new FakeMemoryService();
        ReelAnalyticsServiceImpl service = serviceWith(memory);
        ReelJob job = approvedPublishedJob();
        service.recordOutcome(job, reportFor(job, reviewResult(ReviewResult.Verdict.APPROVED),
                readyPublishingResult()), null, null, null);

        List<ReelOutcomeSnapshot> all = service.findAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).jobId()).isEqualTo(job.jobId());
        assertThat(all.get(0).outcome()).isEqualTo(ReelOutcomeSnapshot.Outcome.PUBLISHED);
    }

    private static ReelJob approvedPublishedJob() {
        return ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "The Topic")
                .withReviewResult(reviewResult(ReviewResult.Verdict.APPROVED)).withPublishingStatus("READY")
                .completed();
    }

    private static ReelJobReport reportFor(ReelJob job, ReviewResult reviewResult, PublishingResult publishingResult) {
        return new ReelJobReport(job.jobId(), job.pipelineRunId(), job.topicId(), Instant.now(), Instant.now(),
                Duration.ofSeconds(40), List.of(), "COMPLETED", Map.of(), List.of(), List.of(), List.of(), "valid",
                "packaged", reviewResult, publishingResult);
    }

    private static PublishingResult readyPublishingResult() {
        return new PublishingResult("pub-1", "job-1", "topic-1", null, "package.json",
                List.of(new PlatformPublishOutcome(Script.Platform.YOUTUBE_SHORTS, true, true, "payload.json",
                        "dry-run, no real upload was performed", Instant.now())),
                PublishingResult.Status.READY, List.of(), Instant.now());
    }

    /** Minimal in-memory {@link MemoryService} fake — this test only ever needs topic records. */
    private static final class FakeMemoryService implements MemoryService {
        private final Map<String, MemoryState.TopicRecord> records = new HashMap<>();
        private int updateTopicRecordCalls = 0;

        @Override
        public MemoryState loadCurrentState() {
            return new MemoryState("1.0.0", "java", Instant.now(), null, List.of(), List.of(), List.of(),
                    Map.copyOf(records), new MemoryState.GlobalStats(0, null, null));
        }

        @Override
        public MemoryState.TopicRecord getTopicRecord(String topicId) {
            return records.get(topicId);
        }

        @Override
        public void updateTopicRecord(String topicId, MemoryState.TopicRecord record) {
            updateTopicRecordCalls++;
            records.put(topicId, record);
        }

        @Override
        public void updateQueue(List<MemoryState.QueueEntry> queue) {
        }

        @Override
        public void recordUsedHook(String topicId, String hookContent) {
        }

        @Override
        public void recordUsedExample(String topicId, String exampleContent) {
        }

        @Override
        public void markTopicInProgress(String topicId, String title, Topic.Difficulty difficulty) {
        }
    }
}
