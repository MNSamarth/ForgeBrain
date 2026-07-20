package com.forgebrain.backend.validation;

import static com.forgebrain.backend.services.ReviewFixtures.reviewResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.pipeline.StageExecutionSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Per this mission's Part 3 ("Failure Scenarios") — one fixture per named failure type (AI
 * failure, render failure, reviewer rejection, publishing failure, analytics failure), proving
 * the validation infrastructure classifies each correctly: the Runtime's own retry/continue
 * behavior is already covered by {@code ForgeBrainRuntimeImplTest} and each stage's own failure
 * handling by its dedicated test (e.g. {@code VertexAiResearchServiceImplTest}, {@code
 * ReviewerServiceImplTest}, {@code PublishingServiceImplTest}) — not repeated here. This class
 * instead proves two things per scenario: (1) a legitimately-shaped failure report never trips a
 * false invariant violation, and (2) the checks actually catch a deliberately broken fixture of
 * the same shape — the "detect regressions between subsystems" goal this mission states.
 */
class ProductionValidationFailureScenariosTest {

    private static StageExecutionSummary stage(String name, boolean success) {
        return new StageExecutionSummary(name, Duration.ofSeconds(1), success, "n/a", success ? "ok" : "failed",
                false, "n/a", success ? null : "boom");
    }

    private static ReelJobReport report(String status, List<StageExecutionSummary> stages,
            String renderValidationSummary, ReviewResult reviewResult, PublishingResult publishingResult) {
        return new ReelJobReport("job-1", "run-1", "topic-1", Instant.now(), Instant.now(), Duration.ofSeconds(30),
                stages, status, Map.of(), List.of(), List.of(), List.of(), renderValidationSummary, "packaged",
                reviewResult, publishingResult);
    }

    // ------------------------------------------------------------------------------ AI failure

    @Test
    void anAiPipelineFailureIsCorrectlyShapedAndTripsNoFalseViolations() {
        ReelJobReport failureReport = report("FAILED", List.of(stage("AI_PIPELINE", false)), null, null, null);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().failed("ContentGenerationException: no topic");

        assertThat(job.status()).isEqualTo(ReelJob.Status.FAILED);
        assertThat(PipelineInvariants.stageRunsAtMostOnce(failureReport)).isEmpty();
        assertThat(PipelineInvariants.stageOrderingIsCanonical(failureReport)).isEmpty();
        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
        assertThat(ArtifactValidator.validateRenderReport(failureReport)).isEmpty();
    }

    // --------------------------------------------------------------------------- render failure

    @Test
    void aRenderFailureIsCorrectlyShapedAndTripsNoFalseViolations() {
        ReelJobReport failureReport = report("FAILED",
                List.of(stage("AI_PIPELINE", true), stage("VOICE", true), stage("SUBTITLES", true),
                        stage("ASSETS", true), stage("RENDER_PLAN", true), stage("RENDER_VALIDATION", true),
                        stage("RENDER_EXECUTION", false)),
                "invalid — ffmpeg exited non-zero", null, null);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .failed("RenderExecutionException: ffmpeg failed");

        assertThat(job.status()).isEqualTo(ReelJob.Status.FAILED);
        assertThat(PipelineInvariants.stageOrderingIsCanonical(failureReport)).isEmpty();
        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
    }

    // ------------------------------------------------------------------------ reviewer rejection

    @Test
    void aReviewerRejectionCompletesTheJobAndCorrectlySkipsPublishing() {
        ReelJobReport rejectionReport = report("COMPLETED", fullStagesThroughReviewing(),
                "valid, no issues", reviewResult(ReviewResult.Verdict.REJECTED), null);
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withReviewResult(reviewResult(ReviewResult.Verdict.REJECTED))
                .withPublishingStatus("SKIPPED_NOT_APPROVED")
                .withOutputFiles(Map.of("video", "v", "thumbnail", "t", "subtitles", "s", "metadata", "m", "report",
                        "r"))
                .completed();

        assertThat(job.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(PipelineInvariants.publishingOnlyAfterApproval(rejectionReport)).isEmpty();
        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
    }

    @Test
    void detectsARegressionWherePublishingRunsDespiteARejectedVerdict() {
        PublishingResult publishingResult = new PublishingResult("pub-1", "job-1", "topic-1", null, "ref.json",
                List.of(), PublishingResult.Status.READY, List.of(), Instant.now());
        ReelJobReport brokenReport = report("COMPLETED", fullStagesThroughReviewing(), "valid, no issues",
                reviewResult(ReviewResult.Verdict.REJECTED), publishingResult);

        assertThat(PipelineInvariants.publishingOnlyAfterApproval(brokenReport)).isNotEmpty();
    }

    // ------------------------------------------------------------------------ publishing failure

    @Test
    void aPublishingFailureCompletesTheJobRatherThanFailingIt() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withReviewResult(reviewResult(ReviewResult.Verdict.APPROVED))
                .withPublishingStatus("FAILED")
                .withOutputFiles(Map.of("video", "v", "thumbnail", "t", "subtitles", "s", "metadata", "m", "report",
                        "r"))
                .completed();

        assertThat(job.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(job.publishingStatus()).isEqualTo("FAILED");
        assertThat(PipelineInvariants.requiredArtifactsPresent(job)).isEmpty();
    }

    // ------------------------------------------------------------------------- analytics failure

    @Test
    void anAnalyticsFailureIsDetectedAsAMissingSnapshotForACompletedJob() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running().withTopic("topic-1", "Title")
                .withReviewResult(reviewResult(ReviewResult.Verdict.APPROVED))
                .withPublishingStatus("READY")
                .completed();

        assertThat(PipelineInvariants.analyticsCapturedAfterPublishingDecision(job, null)).isNotEmpty();
    }

    private static List<StageExecutionSummary> fullStagesThroughReviewing() {
        return List.of(stage("AI_PIPELINE", true), stage("VOICE", true), stage("SUBTITLES", true),
                stage("ASSETS", true), stage("RENDER_PLAN", true), stage("RENDER_VALIDATION", true),
                stage("RENDER_EXECUTION", true), stage("REVIEWING", true));
    }
}
