package com.forgebrain.backend.config;

import com.forgebrain.backend.models.QualityScore;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reviewer thresholds and scoring weights — deliberately external configuration, not fixed in
 * code, per reviewer/quality-scoring-spec.md Section 6 ("Configurable thresholds... both external
 * configuration, not fixed in either schema"). Bound from {@code forgebrain.reviewer.*} in
 * application.yml.
 *
 * @param approvalThreshold                  minimum {@code QualityScore.overallScore} for an
 *                                            {@code APPROVED} verdict; below this (with no
 *                                            individual dimension below {@code dimensionFloor}
 *                                            either) still yields {@code NEEDS_REVISION}
 * @param dimensionFloor                     minimum score any single dimension may have without
 *                                            forcing {@code NEEDS_REVISION}, regardless of
 *                                            {@code overallScore}
 * @param durationMismatchToleranceSeconds   how far {@code VideoPackage.durationSeconds} may
 *                                            drift from the storyboard's planned duration before
 *                                            {@code production_polish} is penalized
 * @param scriptLengthToleranceRatio         {@code Script.estimatedDurationSeconds} may exceed
 *                                            {@code Script.targetDurationSeconds} by up to this
 *                                            ratio before it counts as "script too long"
 * @param maxSubtitleReadingCharsPerSecond   subtitle segments read faster than this
 *                                            (characters ÷ segment duration) are flagged as
 *                                            unreadable
 * @param dimensionWeights                   the weight applied to each {@link
 *                                            QualityScore.Dimensions} field when computing
 *                                            {@code overallScore} — reuses {@code Dimensions}
 *                                            itself, exactly as {@code QualityScore
 *                                            .scoringWeightsUsed} already does
 * @param reviewerVersion                    stamped onto every {@code ReviewResult}
 * @param scoringVersion                     stamped onto every {@code QualityScore}
 */
@ConfigurationProperties(prefix = "forgebrain.reviewer")
public record ReviewerConfig(
        double approvalThreshold,
        double dimensionFloor,
        double durationMismatchToleranceSeconds,
        double scriptLengthToleranceRatio,
        double maxSubtitleReadingCharsPerSecond,
        QualityScore.Dimensions dimensionWeights,
        String reviewerVersion,
        String scoringVersion
) {
}
