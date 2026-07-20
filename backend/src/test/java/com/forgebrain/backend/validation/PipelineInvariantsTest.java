package com.forgebrain.backend.validation;

import static com.forgebrain.backend.services.ReviewFixtures.reviewResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.pipeline.StageExecutionSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PipelineInvariants} — per this mission's Part 2 ("reusable assertions").
 * Pure, fixture-based; no Spring context, no real pipeline execution.
 */
class PipelineInvariantsTest {

    private static StageExecutionSummary stage(String name, boolean success) {
        return new StageExecutionSummary(name, Duration.ofSeconds(1), success, "n/a", "ok", false, "n/a",
                success ? null : "boom");
    }

    private static ReelJobReport report(String status, List<StageExecutionSummary> stages,
            ReviewResult reviewResult, PublishingResult publishingResult) {
        return new ReelJobReport("job-1", "run-1", "topic-1", Instant.now(), Instant.now(), Duration.ofSeconds(30),
                stages, status, Map.of(), List.of(), List.of(), List.of(), "valid", "packaged", reviewResult,
                publishingResult);
    }

    private static PublishingResult publishingResult(PublishingResult.Status status) {
        return new PublishingResult("pub-1", "job-1", "topic-1", null, "ref.json", List.of(), status, List.of(),
                Instant.now());
    }

    // -------------------------------------------------------------------- stageRunsAtMostOnce

    @Test
    void noViolationWhenEveryStageRunsOnce() {
        ReelJobReport report = report("COMPLETED", List.of(stage("AI_PIPELINE", true), stage("VOICE", true)), null,
                null);

        assertThat(PipelineInvariants.stageRunsAtMostOnce(report)).isEmpty();
    }

    @Test
    void flagsAStageThatRanMoreThanOnce() {
        ReelJobReport report = report("FAILED",
                List.of(stage("AI_PIPELINE", true), stage("AI_PIPELINE", false)), null, null);

        assertThat(PipelineInvariants.stageRunsAtMostOnce(report)).anyMatch(v -> v.contains("AI_PIPELINE"));
    }

    // ---------------------------------------------------------------- stageOrderingIsCanonical

    @Test
    void noViolationForAPrefixOfTheCanonicalOrder() {
        ReelJobReport report = report("FAILED",
                List.of(stage("AI_PIPELINE", true), stage("VOICE", true), stage("SUBTITLES", false)), null, null);

        assertThat(PipelineInvariants.stageOrderingIsCanonical(report)).isEmpty();
    }

    @Test
    void flagsStagesExecutedOutOfOrder() {
        ReelJobReport report = report("FAILED", List.of(stage("VOICE", true), stage("AI_PIPELINE", true)), null,
                null);

        assertThat(PipelineInvariants.stageOrderingIsCanonical(report)).isNotEmpty();
    }

    @Test
    void flagsAnUnknownStageName() {
        ReelJobReport report = report("FAILED", List.of(stage("NOT_A_REAL_STAGE", true)), null, null);

        assertThat(PipelineInvariants.stageOrderingIsCanonical(report)).anyMatch(v -> v.contains("unknown stage"));
    }

    // ------------------------------------------------------------------- requiredArtifactsPresent

    @Test
    void noViolationWhenACompletedJobHasEveryRequiredArtifact() {
        ReelJob job = completedJobWithArtifacts(Map.of("video", "v", "thumbnail", "t", "subtitles", "s", "metadata",
                "m", "report", "r"));

        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
    }

    @Test
    void flagsAMissingRequiredArtifactOnACompletedJob() {
        ReelJob job = completedJobWithArtifacts(Map.of("video", "v", "subtitles", "s", "metadata", "m", "report",
                "r"));

        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).anyMatch(v -> v.contains("thumbnail"));
    }

    @Test
    void noViolationForAFailedJobEvenWithoutArtifacts() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().failed("boom");

        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
    }

    private static ReelJob completedJobWithArtifacts(Map<String, String> artifacts) {
        return ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withOutputFiles(artifacts).completed();
    }

    // ------------------------------------------------------------------ publishingOnlyAfterApproval

    @Test
    void noViolationWhenPublishingNeverRan() {
        ReelJobReport report = report("COMPLETED", List.of(), reviewResult(ReviewResult.Verdict.NEEDS_REVISION),
                null);

        assertThat(PipelineInvariants.publishingOnlyAfterApproval(report)).isEmpty();
    }

    @Test
    void noViolationWhenPublishingRanAfterApproval() {
        ReelJobReport report = report("COMPLETED", List.of(), reviewResult(ReviewResult.Verdict.APPROVED),
                publishingResult(PublishingResult.Status.READY));

        assertThat(PipelineInvariants.publishingOnlyAfterApproval(report)).isEmpty();
    }

    @Test
    void flagsPublishingThatRanWithoutApproval() {
        ReelJobReport report = report("COMPLETED", List.of(), reviewResult(ReviewResult.Verdict.REJECTED),
                publishingResult(PublishingResult.Status.READY));

        assertThat(PipelineInvariants.publishingOnlyAfterApproval(report)).isNotEmpty();
    }

    @Test
    void flagsPublishingThatRanWithNoReviewResultAtAll() {
        ReelJobReport report = report("COMPLETED", List.of(), null, publishingResult(PublishingResult.Status.READY));

        assertThat(PipelineInvariants.publishingOnlyAfterApproval(report)).isNotEmpty();
    }

    // ------------------------------------------------------ analyticsCapturedAfterPublishingDecision

    @Test
    void flagsAMissingAnalyticsSnapshot() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withPublishingStatus("READY").completed();

        assertThat(PipelineInvariants.analyticsCapturedAfterPublishingDecision(job, null)).isNotEmpty();
    }

    @Test
    void noViolationWhenTheSnapshotsPublishStatusMatchesTheJob() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withPublishingStatus("READY").completed();
        ReelOutcomeSnapshot snapshot = snapshot(job, "READY");

        assertThat(PipelineInvariants.analyticsCapturedAfterPublishingDecision(job, snapshot)).isEmpty();
    }

    @Test
    void flagsAMismatchBetweenTheSnapshotAndTheJobsPublishingStatus() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withPublishingStatus("READY").completed();
        ReelOutcomeSnapshot snapshot = snapshot(job, "FAILED");

        assertThat(PipelineInvariants.analyticsCapturedAfterPublishingDecision(job, snapshot)).isNotEmpty();
    }

    private static ReelOutcomeSnapshot snapshot(ReelJob job, String publishStatus) {
        return new ReelOutcomeSnapshot(UUID.randomUUID().toString(), job.jobId(), job.topicId(), job.topicTitle(),
                ContentStrategy.HookType.MYTH, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS), 40.0, ReviewResult.Verdict.APPROVED, 0.8, publishStatus,
                Map.of(), ReelOutcomeSnapshot.Outcome.PUBLISHED, 0, false, List.of(), 0, null, Instant.now(),
                Instant.now(), "1.0.0");
    }

    // ------------------------------------------------------------- memoryReflectsAnalyticsOutcome

    @Test
    void noViolationWhenTheSnapshotHasNoTopic() {
        ReelOutcomeSnapshot snapshot = new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-1", null, null,
                null, null, List.of(), null, null, null, null, Map.of(), ReelOutcomeSnapshot.Outcome.FAILED, 0,
                false, List.of(), 0, "boom", Instant.now(), Instant.now(), "1.0.0");

        assertThat(PipelineInvariants.memoryReflectsAnalyticsOutcome(snapshot, null)).isEmpty();
    }

    @Test
    void flagsAMissingMemoryRecordAfterAnalyticsRan() {
        ReelOutcomeSnapshot snapshot = snapshotForTopic("topic-1", 0.8);

        assertThat(PipelineInvariants.memoryReflectsAnalyticsOutcome(snapshot, null)).isNotEmpty();
    }

    @Test
    void flagsAMemoryRecordMissingAPerformanceScoreWhenTheSnapshotHadAReviewScore() {
        ReelOutcomeSnapshot snapshot = snapshotForTopic("topic-1", 0.8);
        MemoryState.TopicRecord record = topicRecord("topic-1", null);

        assertThat(PipelineInvariants.memoryReflectsAnalyticsOutcome(snapshot, record)).isNotEmpty();
    }

    @Test
    void noViolationWhenMemoryReflectsTheSnapshotsReviewScore() {
        ReelOutcomeSnapshot snapshot = snapshotForTopic("topic-1", 0.8);
        MemoryState.TopicRecord record = topicRecord("topic-1", 0.8);

        assertThat(PipelineInvariants.memoryReflectsAnalyticsOutcome(snapshot, record)).isEmpty();
    }

    private static ReelOutcomeSnapshot snapshotForTopic(String topicId, Double reviewScore) {
        return new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-1", topicId, "Title",
                ContentStrategy.HookType.MYTH, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS), 40.0, ReviewResult.Verdict.APPROVED, reviewScore, "READY",
                Map.of(), ReelOutcomeSnapshot.Outcome.PUBLISHED, 0, false, List.of(), 0, null, Instant.now(),
                Instant.now(), "1.0.0");
    }

    private static MemoryState.TopicRecord topicRecord(String topicId, Double performanceScore) {
        return new MemoryState.TopicRecord(topicId, "Title", Topic.Status.POSTED, Topic.Difficulty.BEGINNER, 1,
                Instant.now(), Instant.now(), 0, performanceScore, null, null, MemoryState.Priority.NORMAL, null,
                List.of(), null);
    }
}
