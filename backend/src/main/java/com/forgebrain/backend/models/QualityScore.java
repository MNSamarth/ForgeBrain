package com.forgebrain.backend.models;

import java.time.Instant;
import java.util.List;

/**
 * A six-dimension quality evaluation of a finished {@link VideoPackage}, consumed by the
 * Reviewer as one input to its approval decision — never the decision itself (see
 * reviewer/quality-scoring-spec.md Section 1). Mirrors {@code reviewer/quality-scoring-schema.json}.
 *
 * @see <a href="../../../../../../../../reviewer/quality-scoring-schema.json">reviewer/quality-scoring-schema.json</a>
 */
public record QualityScore(
        String scoreId,
        String topicId,
        String basedOnVideoPackageId,
        Dimensions dimensions,
        double overallScore,
        Dimensions scoringWeightsUsed,
        List<DimensionNote> dimensionNotes,
        String scoringVersion,
        Instant generatedAt
) {

    /**
     * Reused for both {@code dimensions} (0-1 scores) and {@code scoringWeightsUsed} (weights
     * summing to a caller-defined total) — see quality-scoring-schema.json, which does the same.
     */
    public record Dimensions(
            double technicalAccuracy,
            double pacingFit,
            double hookStrength,
            double educationalClarity,
            double productionPolish,
            double brandConsistency
    ) {
    }

    public record DimensionNote(Dimension dimension, String note) {
    }

    public enum Dimension {
        TECHNICAL_ACCURACY, PACING_FIT, HOOK_STRENGTH, EDUCATIONAL_CLARITY, PRODUCTION_POLISH, BRAND_CONSISTENCY
    }
}
