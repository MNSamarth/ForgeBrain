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
     * summing to a caller-defined total) — see quality-scoring-schema.json, which does the same,
     * and {@link com.forgebrain.backend.config.ReviewerConfig#dimensionWeights()}, which supplies
     * the weights. The original six dimensions ({@code technicalAccuracy} through {@code
     * brandConsistency}) are unchanged; {@code visualReadability}, {@code subtitleQuality}, and
     * {@code retentionPotential} were added for the Reviewer stage (see {@code
     * com.forgebrain.backend.services.QualityScorer}) to cover subtitle legibility, subtitle
     * timing correctness, and overall watch-through likelihood, none of which the original six
     * dimensions measured.
     */
    public record Dimensions(
            double technicalAccuracy,
            double pacingFit,
            double hookStrength,
            double educationalClarity,
            double productionPolish,
            double brandConsistency,
            double visualReadability,
            double subtitleQuality,
            double retentionPotential
    ) {
    }

    public record DimensionNote(Dimension dimension, String note) {
    }

    public enum Dimension {
        TECHNICAL_ACCURACY, PACING_FIT, HOOK_STRENGTH, EDUCATIONAL_CLARITY, PRODUCTION_POLISH, BRAND_CONSISTENCY,
        VISUAL_READABILITY, SUBTITLE_QUALITY, RETENTION_POTENTIAL
    }
}
