package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.shared.PipelineStage;
import java.time.Instant;
import java.util.List;

/**
 * The Reviewer's final-gate decision, combining hard safety gates with {@link QualityScore}'s
 * scored judgment. A non-empty {@code hardGateViolations} always forces {@code
 * verdict == REJECTED}, regardless of how well the reel scored elsewhere — hard gates are
 * never averaged against quality scores (see reviewer/reviewer-spec.md Section 3). Mirrors
 * {@code reviewer/reviewer-schema.json}.
 *
 * @see <a href="../../../../../../../../reviewer/reviewer-schema.json">reviewer/reviewer-schema.json</a>
 */
public record ReviewResult(
        String reviewId,
        String topicId,
        String basedOnVideoPackageId,
        String basedOnQualityScoreId,
        Verdict verdict,
        List<String> hardGateViolations,
        List<ReviewIssue> issues,
        ConfidenceNotes confidenceNotes,
        String reviewerVersion,
        Instant reviewedAt
) {

    public enum Verdict {
        APPROVED, NEEDS_REVISION, REJECTED
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
