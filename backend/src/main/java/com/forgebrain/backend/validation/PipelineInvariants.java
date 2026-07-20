package com.forgebrain.backend.validation;

import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.pipeline.StageExecutionSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reusable, pure assertions over the pipeline's own durable records — no service dependencies, no
 * Spring, no real pipeline execution required to exercise them. Each method returns the list of
 * violations found ({@code List.of()} means the invariant held), so the same check can be used
 * both as a test assertion (e.g. {@code assertThat(PipelineInvariants.stageRunsAtMostOnce(report)).isEmpty()})
 * and as an input to {@link com.forgebrain.backend.validation.ProductionReadinessReport}.
 *
 * <p>These check the pipeline's existing output shapes — {@link ReelJobReport}, {@link ReelJob},
 * {@link ReelOutcomeSnapshot}, {@link MemoryState.TopicRecord} — they never call a service or
 * re-run any stage.
 */
public final class PipelineInvariants {

    /** The order every {@link StageExecutionSummary#stageName()} is expected to appear in. */
    private static final List<String> CANONICAL_STAGE_ORDER = List.of(
            "AI_PIPELINE", "VOICE", "SUBTITLES", "ASSETS", "RENDER_PLAN", "RENDER_VALIDATION",
            "RENDER_EXECUTION", "REVIEWING", "PUBLISHING");

    private static final List<String> REQUIRED_ARTIFACT_KEYS = List.of(
            "video", "thumbnail", "subtitles", "metadata", "report");

    private PipelineInvariants() {
    }

    /** Every stage executes at most once per reel. */
    public static List<String> stageRunsAtMostOnce(ReelJobReport report) {
        return report.stageResults().stream()
                .collect(Collectors.groupingBy(StageExecutionSummary::stageName, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> "Stage '" + entry.getKey() + "' executed " + entry.getValue()
                        + " time(s) for job '" + report.jobId() + "'; expected at most once.")
                .toList();
    }

    /** Stages execute in {@link #CANONICAL_STAGE_ORDER}, never out of order, never unknown. */
    public static List<String> stageOrderingIsCanonical(ReelJobReport report) {
        List<String> violations = new ArrayList<>();
        int lastIndex = -1;
        for (StageExecutionSummary stage : report.stageResults()) {
            int index = CANONICAL_STAGE_ORDER.indexOf(stage.stageName());
            if (index < 0) {
                violations.add("Job '" + report.jobId() + "': unknown stage '" + stage.stageName()
                        + "' is not part of the canonical pipeline order.");
                continue;
            }
            if (index <= lastIndex) {
                violations.add("Job '" + report.jobId() + "': stage '" + stage.stageName()
                        + "' executed out of canonical order.");
            }
            lastIndex = index;
        }
        return violations;
    }

    /** A {@link ReelJob.Status#COMPLETED} job has every required output artifact reference. */
    public static List<String> requiredArtifactsPresent(ReelJob job) {
        if (job.status() != ReelJob.Status.COMPLETED) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        for (String key : REQUIRED_ARTIFACT_KEYS) {
            String value = job.outputFiles().get(key);
            if (value == null || value.isBlank()) {
                violations.add("Completed job '" + job.jobId() + "' is missing required artifact '" + key + "'.");
            }
        }
        return violations;
    }

    /** Publishing only ever runs for an {@code APPROVED} review verdict. */
    public static List<String> publishingOnlyAfterApproval(ReelJobReport report) {
        if (report.publishingResult() == null) {
            return List.of();
        }
        ReviewResult.Verdict verdict = report.reviewResult() == null ? null : report.reviewResult().verdict();
        if (verdict != ReviewResult.Verdict.APPROVED) {
            return List.of("Job '" + report.jobId() + "': publishing ran with review verdict " + verdict
                    + " instead of APPROVED.");
        }
        return List.of();
    }

    /**
     * An analytics snapshot is only ever captured once the job's publishing decision (a real
     * outcome, or an explicit skip) has already been recorded — and, when one exists, it agrees
     * with the job's own {@code publishingStatus}.
     */
    public static List<String> analyticsCapturedAfterPublishingDecision(ReelJob job, ReelOutcomeSnapshot snapshot) {
        if (snapshot == null) {
            return List.of("No analytics snapshot was captured for job '" + job.jobId() + "'.");
        }
        List<String> violations = new ArrayList<>();
        if (job.status() == ReelJob.Status.COMPLETED && job.publishingStatus() == null) {
            violations.add("Job '" + job.jobId() + "' completed but never recorded a publishing decision.");
        }
        if (!Objects.equals(snapshot.publishStatus(), job.publishingStatus())) {
            violations.add("Analytics snapshot publish_status '" + snapshot.publishStatus()
                    + "' does not match job '" + job.jobId() + "' publishingStatus '" + job.publishingStatus()
                    + "'.");
        }
        return violations;
    }

    /**
     * Once a topic has an analytics snapshot with a review score, its memory record reflects a
     * performance score — the memory feedback loop's write-side ran, not just the read-side.
     */
    public static List<String> memoryReflectsAnalyticsOutcome(ReelOutcomeSnapshot snapshot,
            MemoryState.TopicRecord topicRecordAfter) {
        if (snapshot.topicId() == null) {
            return List.of();
        }
        if (topicRecordAfter == null) {
            return List.of("No memory record exists for topic '" + snapshot.topicId()
                    + "' after its analytics snapshot was captured.");
        }
        if (snapshot.reviewScore() != null && topicRecordAfter.performanceScore() == null) {
            return List.of("Memory record for topic '" + snapshot.topicId() + "' has no performance_score even"
                    + " though its analytics snapshot (job '" + snapshot.jobId() + "') had a review score.");
        }
        return List.of();
    }
}
