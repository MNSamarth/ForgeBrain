package com.forgebrain.backend.analytics;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One reel's real pipeline-execution outcome — distinct from {@link PerformanceSnapshot}, which
 * captures real <em>audience</em> metrics from an external platform (still not active, since no
 * publishing integration posts anywhere real yet). This snapshot instead captures what the
 * pipeline itself already knows the moment a {@link com.forgebrain.backend.job.ReelJob}
 * completes or fails: how it was reviewed, whether it published, and what it cost to make —
 * the feedback signal available today, without waiting on real-world engagement data.
 *
 * @param jobId               the {@link com.forgebrain.backend.job.ReelJob} this snapshot summarizes
 * @param renderDurationSeconds the rendered video's duration, or {@code null} if the job never
 *                               reached {@code RENDER_EXECUTION}
 * @param reviewScore         {@link ReviewResult#score()} at review time, or {@code null} if the
 *                             job never reached {@code REVIEWING}
 * @param publishStatus       {@link com.forgebrain.backend.job.ReelJob#publishingStatus()} verbatim
 *                             ({@code "READY"}, {@code "PARTIAL_FAILURE"}, {@code "FAILED"},
 *                             {@code "SKIPPED_NOT_APPROVED"}, or {@code null})
 * @param outputArtifactRefs  {@link com.forgebrain.backend.job.ReelJob#outputFiles()} verbatim
 * @param outcome             the single deterministic classification this snapshot's aggregation
 *                             logic groups by — see {@link Outcome}
 * @param warningCount        {@code ReelJobReport.warnings().size()}
 * @param fallbackUsed        {@code true} if any stage used a documented fallback
 * @param retryCount          always {@code 0} today — the job layer has no automatic retry
 *                             mechanism yet (see {@code ReelJob}'s {@code pipelineRunId} javadoc);
 *                             reserved so a future retry-with-history feature has somewhere to
 *                             report into without another schema change
 */
public record ReelOutcomeSnapshot(
        String snapshotId,
        String jobId,
        String topicId,
        String topicTitle,
        ContentStrategy.HookType hookType,
        ContentStrategy.TeachingStyle teachingStyle,
        List<Script.Platform> platformTargets,
        Double renderDurationSeconds,
        ReviewResult.Verdict reviewVerdict,
        Double reviewScore,
        String publishStatus,
        Map<String, String> outputArtifactRefs,
        Outcome outcome,
        int warningCount,
        boolean fallbackUsed,
        List<String> fallbackStages,
        int retryCount,
        String failureReason,
        Instant jobCreatedAt,
        Instant capturedAt,
        String snapshotVersion
) {

    /**
     * A single deterministic outcome bucket, derived from {@link
     * com.forgebrain.backend.job.ReelJob#status()} and {@link ReviewResult#verdict()} — see
     * {@code ReelAnalyticsServiceImpl} for the exact derivation rule.
     */
    public enum Outcome {
        PUBLISHED, PUBLISH_FAILED, NEEDS_REVISION, REJECTED, FAILED
    }
}
