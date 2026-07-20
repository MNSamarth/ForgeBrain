package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.shared.PipelineStage;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The Reviewer's final-gate decision, combining hard safety gates with {@link QualityScore}'s
 * scored judgment. A non-empty {@code hardGateViolations} always forces {@code
 * verdict == REJECTED}, regardless of how well the reel scored elsewhere — hard gates are
 * never averaged against quality scores (see reviewer/reviewer-spec.md Section 3). Mirrors
 * {@code reviewer/reviewer-schema.json}, extended with {@code jobId}, {@code score}, {@code
 * categoryScores}, {@code warnings}, {@code errors}, {@code recommendedAction}, and {@code
 * reviewerNotes} — fields the job orchestration layer and a future publishing gate need that
 * predate this repository having a job system at all.
 *
 * @param jobId              the {@link com.forgebrain.backend.job.ReelJob} this review evaluated,
 *                            or {@code null} when the reviewer ran outside the job layer
 * @param score              equal to {@code QualityScore.overallScore} at the time of review,
 *                            duplicated here so a persisted {@code ReviewResult} is self-contained
 *                            without a second lookup
 * @param categoryScores     a flattened, human-readable view of {@code QualityScore.dimensions}
 *                            (e.g. {@code "hook_strength" -> 0.62}), for the same reason as
 *                            {@code score}
 * @param warnings           non-blocking issue descriptions (every {@link ReviewIssue} of
 *                            {@code MAJOR} or {@code MINOR} severity), flattened to plain strings
 * @param errors             blocking issue descriptions — {@code hardGateViolations} plus any
 *                            {@code BLOCKING}-severity {@link ReviewIssue}, flattened to plain
 *                            strings
 * @param recommendedAction  the concrete next step a caller should take; see {@link
 *                            RecommendedAction}
 * @param reviewerNotes      a one-line, deterministic summary of why this verdict was reached
 * @see <a href="../../../../../../../../reviewer/reviewer-schema.json">reviewer/reviewer-schema.json</a>
 */
public record ReviewResult(
        String reviewId,
        String jobId,
        String topicId,
        String basedOnVideoPackageId,
        String basedOnQualityScoreId,
        Verdict verdict,
        double score,
        Map<String, Double> categoryScores,
        List<String> hardGateViolations,
        List<ReviewIssue> issues,
        List<String> warnings,
        List<String> errors,
        RecommendedAction recommendedAction,
        String reviewerNotes,
        ConfidenceNotes confidenceNotes,
        String reviewerVersion,
        Instant reviewedAt
) {

    public enum Verdict {
        APPROVED, NEEDS_REVISION, REJECTED
    }

    /**
     * The concrete action a caller (job orchestration today, a future publishing gate later)
     * should take. Derived deterministically from {@link Verdict} plus how many distinct {@link
     * ReviewIssue#suggestedStageToRevisit()} values were involved — see {@code
     * ReviewerServiceImpl} for the exact rule.
     */
    public enum RecommendedAction {
        /** {@code verdict == APPROVED}: ready to move on to publishing package preparation. */
        APPROVE,
        /** {@code verdict == REJECTED}: a hard safety gate was violated; do not publish. */
        REJECT,
        /**
         * {@code verdict == NEEDS_REVISION} with every issue pointing at one identifiable
         * upstream stage: rerunning just that stage is likely to fix everything found.
         */
        REGENERATE_SECTION,
        /**
         * {@code verdict == NEEDS_REVISION} with issues spanning multiple stages, or none
         * identifiable: a targeted rerun isn't clearly sufficient, so the whole pipeline should
         * run again.
         */
        REGENERATE_FULL
    }

    /**
     * @param suggestedStageToRevisit which pipeline stage's output is the likely place to fix
     *                                this issue (see docs/PIPELINE.md), or {@code null} for an
     *                                issue noted for visibility that doesn't require rework.
     */
    public record ReviewIssue(Severity severity, String description, PipelineStage suggestedStageToRevisit) {
        public enum Severity {
            BLOCKING, MAJOR, MINOR
        }
    }
}
