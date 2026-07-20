package com.forgebrain.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.pipeline.StageExecutionSummary;
import com.forgebrain.backend.runtime.RuntimeReport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ArtifactValidator} — per this mission's Part 4 ("verify required fields
 * and consistency"). Pure, fixture-based; no Spring context, no real pipeline execution.
 */
class ArtifactValidatorTest {

    // -------------------------------------------------------------------------- RuntimeReport

    private static RuntimeReport.RuntimeConfigSnapshot configSnapshot() {
        return new RuntimeReport.RuntimeConfigSnapshot(1, 1, 1, "manual", 0.7, true, false, false, false, "flash",
                "pro", "flash", "pro");
    }

    private static RuntimeReport runtimeReport(String runtimeId, Instant start, Instant end, int requested,
            int completed, int failed, List<RuntimeReport.ReelExecutionSummary> executions,
            RuntimeReport.RuntimeConfigSnapshot snapshot) {
        return new RuntimeReport(runtimeId, start, end, Duration.between(start, end), requested, completed, failed,
                executions, "1 READY", null, List.of(), List.of(), List.of(), snapshot);
    }

    @Test
    void aWellFormedRuntimeReportHasNoViolations() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(30);
        RuntimeReport.ReelExecutionSummary execution = new RuntimeReport.ReelExecutionSummary("job-1", "topic-1",
                "Title", com.forgebrain.backend.job.ReelJob.Status.COMPLETED, "APPROVED", "READY", 1, null);

        RuntimeReport report = runtimeReport("run-1", start, end, 1, 1, 0, List.of(execution), configSnapshot());

        assertThat(ArtifactValidator.validateRuntimeReport(report)).isEmpty();
    }

    @Test
    void flagsABlankRuntimeId() {
        Instant now = Instant.now();
        RuntimeReport report = runtimeReport("", now, now, 0, 0, 0, List.of(), configSnapshot());

        assertThat(ArtifactValidator.validateRuntimeReport(report)).anyMatch(v -> v.contains("runtimeId"));
    }

    @Test
    void flagsCompletedAtBeforeStartedAt() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(5);
        RuntimeReport report = runtimeReport("run-1", start, end, 0, 0, 0, List.of(), configSnapshot());

        assertThat(ArtifactValidator.validateRuntimeReport(report)).anyMatch(v -> v.contains("before startedAt"));
    }

    @Test
    void flagsReelCountsThatExceedReelsRequested() {
        Instant now = Instant.now();
        RuntimeReport report = runtimeReport("run-1", now, now, 1, 2, 0, List.of(), configSnapshot());

        assertThat(ArtifactValidator.validateRuntimeReport(report)).isNotEmpty();
    }

    @Test
    void flagsAMissingConfigSnapshot() {
        Instant now = Instant.now();
        RuntimeReport report = runtimeReport("run-1", now, now, 0, 0, 0, List.of(), null);

        assertThat(ArtifactValidator.validateRuntimeReport(report)).anyMatch(v -> v.contains("configSnapshot"));
    }

    // --------------------------------------------------------------------------- render report

    private static StageExecutionSummary stage(String name, boolean success) {
        return new StageExecutionSummary(name, Duration.ofSeconds(1), success, "n/a", "ok", false, "n/a",
                success ? null : "boom");
    }

    private static ReelJobReport jobReport(String status, List<StageExecutionSummary> stages,
            String renderValidationSummary) {
        return new ReelJobReport("job-1", "run-1", "topic-1", Instant.now(), Instant.now(), Duration.ofSeconds(30),
                stages, status, Map.of(), List.of(), List.of(), List.of(), renderValidationSummary, "packaged", null,
                null);
    }

    @Test
    void aCompletedJobWithAValidationSummaryAndARenderStageHasNoViolations() {
        ReelJobReport report = jobReport("COMPLETED", List.of(stage("RENDER_EXECUTION", true)), "valid, no issues");

        assertThat(ArtifactValidator.validateRenderReport(report)).isEmpty();
    }

    @Test
    void flagsACompletedJobWithNoRenderValidationSummary() {
        ReelJobReport report = jobReport("COMPLETED", List.of(stage("RENDER_EXECUTION", true)), null);

        assertThat(ArtifactValidator.validateRenderReport(report)).anyMatch(v -> v.contains("renderValidationSummary"));
    }

    @Test
    void flagsACompletedJobWithNoRenderExecutionStage() {
        ReelJobReport report = jobReport("COMPLETED", List.of(stage("AI_PIPELINE", true)), "valid");

        assertThat(ArtifactValidator.validateRenderReport(report)).anyMatch(v -> v.contains("RENDER_EXECUTION"));
    }

    @Test
    void flagsACompletedJobWhoseRenderExecutionStageActuallyFailed() {
        ReelJobReport report = jobReport("COMPLETED", List.of(stage("RENDER_EXECUTION", false)), "invalid");

        assertThat(ArtifactValidator.validateRenderReport(report)).isNotEmpty();
    }

    @Test
    void noViolationForAFailedJobMissingARenderStage() {
        ReelJobReport report = jobReport("FAILED", List.of(stage("AI_PIPELINE", false)), null);

        assertThat(ArtifactValidator.validateRenderReport(report)).isEmpty();
    }

    // ---------------------------------------------------------------------- publishing package

    private static PublishingPackage publishingPackage(String packageId, String reviewVerdict, String title,
            List<PublishingPackage.PlatformVariant> variants, String videoFileUri) {
        PublishingMetadata metadata = title == null ? null
                : new PublishingMetadata(title, "desc", List.of(), List.of(), "Education", "en-US");
        return new PublishingPackage(packageId, "job-1", "topic-1", "Title", "review-1", reviewVerdict, "video-pkg-1",
                videoFileUri, "thumb.jpg", "subs.srt", metadata, variants,
                new PublishingPackage.Scheduling(PublishingPackage.Scheduling.Status.READY, null), null, "1.0.0",
                Instant.now());
    }

    @Test
    void aWellFormedApprovedPackageHasNoViolations() {
        PublishingPackage pkg = publishingPackage("pkg-1", "APPROVED", "A Title",
                List.of(new PublishingPackage.PlatformVariant(com.forgebrain.backend.models.Script.Platform.YOUTUBE_SHORTS,
                        new PublishingMetadata("A Title", "desc", List.of(), List.of(), "Education", "en-US"))),
                "reel.mp4");

        assertThat(ArtifactValidator.validatePublishingPackage(pkg)).isEmpty();
    }

    @Test
    void flagsAPackageBuiltFromANonApprovedVerdict() {
        PublishingPackage pkg = publishingPackage("pkg-1", "REJECTED", "A Title", List.of(), "reel.mp4");

        assertThat(ArtifactValidator.validatePublishingPackage(pkg)).anyMatch(v -> v.contains("REJECTED"));
    }

    @Test
    void flagsAPackageWithNoPlatformVariants() {
        PublishingPackage pkg = publishingPackage("pkg-1", "APPROVED", "A Title", List.of(), "reel.mp4");

        assertThat(ArtifactValidator.validatePublishingPackage(pkg)).anyMatch(v -> v.contains("platform variants"));
    }

    @Test
    void flagsAPackageWithNoVideoFileReference() {
        PublishingPackage pkg = publishingPackage("pkg-1", "APPROVED", "A Title", List.of(), "");

        assertThat(ArtifactValidator.validatePublishingPackage(pkg)).anyMatch(v -> v.contains("video file"));
    }

    // ----------------------------------------------------------------------- analytics report

    private static AnalyticsReport analyticsReport(String reportId, Instant windowStart, Instant windowEnd,
            int totalReelsAnalyzed) {
        return new AnalyticsReport(reportId, windowStart, windowEnd, totalReelsAnalyzed, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), new AnalyticsReport.ReviewTrendSummary(0, 0, 0, 0),
                new AnalyticsReport.PublishReadinessTrendSummary(0, 0, 0, 0, 0), List.of(), Instant.now());
    }

    @Test
    void aWellFormedAnalyticsReportHasNoViolations() {
        Instant start = Instant.now();
        AnalyticsReport report = analyticsReport("report-1", start, start.plusSeconds(60), 3);

        assertThat(ArtifactValidator.validateAnalyticsReport(report)).isEmpty();
    }

    @Test
    void flagsAWindowEndBeforeWindowStart() {
        Instant start = Instant.now();
        AnalyticsReport report = analyticsReport("report-1", start, start.minusSeconds(60), 0);

        assertThat(ArtifactValidator.validateAnalyticsReport(report)).anyMatch(v -> v.contains("windowEnd"));
    }

    @Test
    void flagsABlankReportId() {
        Instant start = Instant.now();
        AnalyticsReport report = analyticsReport("", start, start.plusSeconds(1), 0);

        assertThat(ArtifactValidator.validateAnalyticsReport(report)).anyMatch(v -> v.contains("reportId"));
    }
}
