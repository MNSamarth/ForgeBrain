package com.forgebrain.backend.validation;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.runtime.RuntimeReport;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural/consistency checks over the four artifact shapes this mission's Part 4 names:
 * runtime report ({@link com.forgebrain.backend.runtime.RuntimeReport}), render report (the
 * render-related portion of {@link ReelJobReport}), publishing package ({@link
 * PublishingPackage}), and analytics report ({@link AnalyticsReport}). Each method returns the
 * violations found — {@code List.of()} means the artifact is well-formed — mirroring {@link
 * PipelineInvariants}'s shape so both can feed the same {@link ProductionReadinessReport}.
 */
public final class ArtifactValidator {

    private ArtifactValidator() {
    }

    public static List<String> validateRuntimeReport(RuntimeReport report) {
        List<String> violations = new ArrayList<>();
        if (isBlank(report.runtimeId())) {
            violations.add("RuntimeReport.runtimeId is blank.");
        }
        if (report.startedAt() == null || report.completedAt() == null) {
            violations.add("RuntimeReport is missing startedAt/completedAt.");
        } else if (report.completedAt().isBefore(report.startedAt())) {
            violations.add("RuntimeReport.completedAt is before startedAt.");
        }
        if (report.reelsRequested() < 0 || report.reelsCompleted() < 0 || report.reelsFailed() < 0) {
            violations.add("RuntimeReport has a negative reel count.");
        }
        if (report.reelsCompleted() + report.reelsFailed() > report.reelsRequested()) {
            violations.add("RuntimeReport.reelsCompleted + reelsFailed (" + (report.reelsCompleted()
                    + report.reelsFailed()) + ") exceeds reelsRequested (" + report.reelsRequested() + ").");
        }
        if (report.reelExecutions() == null || report.reelExecutions().size() != report.reelsCompleted()
                + report.reelsFailed()) {
            violations.add("RuntimeReport.reelExecutions size does not match reelsCompleted + reelsFailed.");
        }
        if (report.configSnapshot() == null) {
            violations.add("RuntimeReport is missing its configSnapshot.");
        }
        return violations;
    }

    public static List<String> validateRenderReport(ReelJobReport report) {
        List<String> violations = new ArrayList<>();
        boolean renderExecuted = report.stageResults().stream()
                .anyMatch(stage -> stage.stageName().equals("RENDER_EXECUTION"));
        if ("COMPLETED".equals(report.status())) {
            if (isBlank(report.renderValidationSummary())) {
                violations.add("Completed job '" + report.jobId() + "' has no renderValidationSummary.");
            }
            if (!renderExecuted) {
                violations.add("Completed job '" + report.jobId() + "' has no RENDER_EXECUTION stage result.");
            }
        }
        boolean renderFailed = report.stageResults().stream()
                .anyMatch(stage -> stage.stageName().equals("RENDER_EXECUTION") && !stage.success());
        if (renderFailed && "COMPLETED".equals(report.status())) {
            violations.add("Job '" + report.jobId() + "' reports COMPLETED despite a failed RENDER_EXECUTION"
                    + " stage.");
        }
        return violations;
    }

    public static List<String> validatePublishingPackage(PublishingPackage publishingPackage) {
        List<String> violations = new ArrayList<>();
        if (isBlank(publishingPackage.packageId())) {
            violations.add("PublishingPackage.packageId is blank.");
        }
        if (!"APPROVED".equals(publishingPackage.reviewVerdict())) {
            violations.add("PublishingPackage '" + publishingPackage.packageId() + "' was built from review"
                    + " verdict '" + publishingPackage.reviewVerdict() + "' instead of APPROVED.");
        }
        if (publishingPackage.metadata() == null || isBlank(publishingPackage.metadata().title())) {
            violations.add("PublishingPackage '" + publishingPackage.packageId() + "' has no metadata title.");
        }
        if (publishingPackage.platformVariants() == null || publishingPackage.platformVariants().isEmpty()) {
            violations.add("PublishingPackage '" + publishingPackage.packageId() + "' has no platform variants.");
        }
        if (isBlank(publishingPackage.videoFileUri())) {
            violations.add("PublishingPackage '" + publishingPackage.packageId() + "' has no video file"
                    + " reference.");
        }
        return violations;
    }

    public static List<String> validateAnalyticsReport(AnalyticsReport report) {
        List<String> violations = new ArrayList<>();
        if (isBlank(report.reportId())) {
            violations.add("AnalyticsReport.reportId is blank.");
        }
        if (report.windowStart() == null || report.windowEnd() == null) {
            violations.add("AnalyticsReport is missing windowStart/windowEnd.");
        } else if (report.windowEnd().isBefore(report.windowStart())) {
            violations.add("AnalyticsReport.windowEnd is before windowStart.");
        }
        if (report.totalReelsAnalyzed() < 0) {
            violations.add("AnalyticsReport.totalReelsAnalyzed is negative.");
        }
        if (report.reviewTrends() == null) {
            violations.add("AnalyticsReport is missing reviewTrends.");
        }
        if (report.publishReadinessTrends() == null) {
            violations.add("AnalyticsReport is missing publishReadinessTrends.");
        }
        return violations;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
