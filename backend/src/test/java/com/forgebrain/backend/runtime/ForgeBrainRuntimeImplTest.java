package com.forgebrain.backend.runtime;

import static com.forgebrain.backend.services.ReviewFixtures.platformUploadConfig;
import static com.forgebrain.backend.services.ReviewFixtures.reviewResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.ReviewerConfig;
import com.forgebrain.backend.config.RuntimeConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobService;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.services.MemoryService;
import com.forgebrain.backend.services.ReelAnalyticsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ForgeBrainRuntimeImpl} — per this mission's Part 8 ("multi-reel tests",
 * "failure recovery tests", "configuration tests"). Every collaborator ({@link
 * ReelJobService}, {@link ReelAnalyticsService}, {@link MemoryService},
 * {@link RuntimeReportWriter}) is mocked — no real pipeline, no real network, no real ffmpeg. See
 * {@link ForgeBrainRuntimeIntegrationTest} for the real, full-stack proof.
 */
class ForgeBrainRuntimeImplTest {

    private ReelJobService reelJobService;
    private ReelAnalyticsService reelAnalyticsService;
    private MemoryService memoryService;
    private RuntimeReportWriter reportWriter;

    @BeforeEach
    void setUp() {
        reelJobService = mock(ReelJobService.class);
        reelAnalyticsService = mock(ReelAnalyticsService.class);
        memoryService = mock(MemoryService.class);
        reportWriter = mock(RuntimeReportWriter.class);
        when(reportWriter.write(any())).thenReturn("/tmp/report.json");
        when(reelAnalyticsService.generateReport(any(), any())).thenReturn(sampleAnalyticsReport());
    }

    private ForgeBrainRuntimeImpl runtime(RuntimeConfig config) {
        return new ForgeBrainRuntimeImpl(config, reelJobService, reelAnalyticsService, memoryService,
                reviewerConfig(), platformUploadConfig(), vertexAiConfig(), cloudStorageConfig(), reportWriter);
    }

    private static RuntimeConfig config(int dailyReelCount, int parallelism, int maxRetriesPerReel) {
        return new RuntimeConfig(dailyReelCount, parallelism, maxRetriesPerReel, 0, "manual");
    }

    private static ReviewerConfig reviewerConfig() {
        QualityScore.Dimensions weights = new QualityScore.Dimensions(0.25, 0.15, 0.15, 0.15, 0.1, 0.05, 0.05, 0.05,
                0.05);
        return new ReviewerConfig(0.7, 0.5, 3.0, 1.25, 20.0, weights, "1.0.0", "1.0.0");
    }

    private static VertexAiConfig vertexAiConfig() {
        return new VertexAiConfig("demo-project", "us-central1", "flash-research", "pro-lesson", "pro-script",
                "flash-director", 0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048,
                "application/json", "gemini-2.5-pro", 0.4, 2048, "application/json");
    }

    private static CloudStorageConfig cloudStorageConfig() {
        return new CloudStorageConfig(false, "", "", "", "reels", "");
    }

    private static AnalyticsReport sampleAnalyticsReport() {
        Instant now = Instant.now();
        return new AnalyticsReport("report-1", now, now, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), new AnalyticsReport.ReviewTrendSummary(0, 0, 0, 0),
                new AnalyticsReport.PublishReadinessTrendSummary(0, 0, 0, 0, 0), List.of(), now);
    }

    private static ReelJob completedJob(String jobId, String topicId, String publishingStatus) {
        return ReelJob.queued(jobId, "run-" + jobId).running().withTopic(topicId, "Title for " + topicId)
                .withReviewResult(reviewResult(ReviewResult.Verdict.APPROVED))
                .withPublishingStatus(publishingStatus)
                .completed();
    }

    private static ReelJob failedJob(String jobId, String reason) {
        return ReelJob.queued(jobId, "run-" + jobId).running().failed(reason);
    }

    @Test
    void dispatchesExactlyTheConfiguredReelCountWhenEverySlotSucceedsOnTheFirstAttempt() {
        when(reelJobService.submitJob())
                .thenReturn(completedJob("job-1", "topic-1", "READY"))
                .thenReturn(completedJob("job-2", "topic-2", "READY"))
                .thenReturn(completedJob("job-3", "topic-3", "READY"));

        RuntimeReport report = runtime(config(3, 1, 0)).run();

        assertThat(report.reelsRequested()).isEqualTo(3);
        assertThat(report.reelsCompleted()).isEqualTo(3);
        assertThat(report.reelsFailed()).isZero();
        assertThat(report.reelExecutions()).hasSize(3);
        assertThat(report.reelExecutions()).allMatch(r -> r.attempts() == 1);
        verify(reelJobService, times(3)).submitJob();
    }

    @Test
    void retriesAFailingReelSlotThenSucceeds() {
        when(reelJobService.submitJob())
                .thenReturn(failedJob("job-1a", "boom"))
                .thenReturn(completedJob("job-1b", "topic-1", "READY"))
                .thenReturn(completedJob("job-2", "topic-2", "READY"));

        RuntimeReport report = runtime(config(2, 1, 1)).run();

        assertThat(report.reelsCompleted()).isEqualTo(2);
        assertThat(report.reelsFailed()).isZero();
        assertThat(report.reelExecutions().get(0).attempts()).isEqualTo(2);
        assertThat(report.reelExecutions().get(0).jobId()).isEqualTo("job-1b");
        verify(reelJobService, times(3)).submitJob();
    }

    @Test
    void continuesToTheNextReelSlotWhenOneSlotExhaustsAllRetries() {
        when(reelJobService.submitJob())
                .thenReturn(failedJob("job-1a", "boom 1"))
                .thenReturn(failedJob("job-1b", "boom 2"))
                .thenReturn(completedJob("job-2", "topic-2", "READY"));

        RuntimeReport report = runtime(config(2, 1, 1)).run();

        assertThat(report.reelsCompleted()).isEqualTo(1);
        assertThat(report.reelsFailed()).isEqualTo(1);
        assertThat(report.reelExecutions()).hasSize(2);
        assertThat(report.errors()).anyMatch(e -> e.contains("exhausted"));
        verify(reelJobService, times(3)).submitJob();
    }

    @Test
    void publishStatusSummaryTalliesAcrossEveryCompletedReel() {
        when(reelJobService.submitJob())
                .thenReturn(completedJob("job-1", "topic-1", "READY"))
                .thenReturn(completedJob("job-2", "topic-2", "READY"))
                .thenReturn(completedJob("job-3", "topic-3", "SKIPPED_NOT_APPROVED"));

        RuntimeReport report = runtime(config(3, 1, 0)).run();

        assertThat(report.publishStatusSummary()).contains("2 READY").contains("1 SKIPPED_NOT_APPROVED");
    }

    @Test
    void reportEchoesTheLiveConfigurationFromEveryDedicatedConfigDomainWithoutDuplicatingIt() {
        when(reelJobService.submitJob()).thenReturn(completedJob("job-1", "topic-1", "READY"));

        RuntimeReport report = runtime(config(1, 1, 0)).run();

        RuntimeReport.RuntimeConfigSnapshot snapshot = report.configSnapshot();
        assertThat(snapshot.reviewApprovalThreshold()).isEqualTo(0.7);
        assertThat(snapshot.publishDryRunOnly()).isTrue();
        assertThat(snapshot.cloudStorageEnabled()).isFalse();
        assertThat(snapshot.researchModel()).isEqualTo("flash-research");
        assertThat(snapshot.lessonModel()).isEqualTo("pro-lesson");
        assertThat(snapshot.runtimeMode()).isEqualTo("manual");
        assertThat(snapshot.dailyReelCount()).isEqualTo(1);
    }

    @Test
    void anAnalyticsSummaryFailureIsRecordedAsAWarningRatherThanFailingTheWholeRun() {
        when(reelJobService.submitJob()).thenReturn(completedJob("job-1", "topic-1", "READY"));
        when(reelAnalyticsService.generateReport(any(), any())).thenThrow(new RuntimeException("disk full"));

        RuntimeReport report = runtime(config(1, 1, 0)).run();

        assertThat(report.analyticsSummary()).isNull();
        assertThat(report.warnings()).anyMatch(w -> w.contains("analytics summary"));
        assertThat(report.reelsCompleted()).isEqualTo(1);
    }

    @Test
    void memoryUpdatesReflectTheFinalTopicRecordForEveryDistinctTopicTouched() {
        when(reelJobService.submitJob()).thenReturn(completedJob("job-1", "topic-1", "READY"));
        MemoryState.TopicRecord record = new MemoryState.TopicRecord("topic-1", "Title", Topic.Status.POSTED,
                Topic.Difficulty.BEGINNER, 1, Instant.now(), Instant.now(), 0, 0.85, null, null,
                MemoryState.Priority.LOW, null, List.of(), "notes");
        when(memoryService.getTopicRecord("topic-1")).thenReturn(record);

        RuntimeReport report = runtime(config(1, 1, 0)).run();

        assertThat(report.memoryUpdates()).hasSize(1);
        assertThat(report.memoryUpdates().get(0).topicId()).isEqualTo("topic-1");
        assertThat(report.memoryUpdates().get(0).status()).isEqualTo("POSTED");
        assertThat(report.memoryUpdates().get(0).performanceScore()).isEqualTo(0.85);
    }

    @Test
    void zeroDailyReelCountProducesAnEmptyButValidReport() {
        RuntimeReport report = runtime(config(0, 1, 0)).run();

        assertThat(report.reelsRequested()).isZero();
        assertThat(report.reelsCompleted()).isZero();
        assertThat(report.reelExecutions()).isEmpty();
        verify(reelJobService, times(0)).submitJob();
    }

    @Test
    void theRuntimeReportIsAlwaysWritten() {
        when(reelJobService.submitJob()).thenReturn(completedJob("job-1", "topic-1", "READY"));

        runtime(config(1, 1, 0)).run();

        verify(reportWriter, times(1)).write(any());
    }
}
