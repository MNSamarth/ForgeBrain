package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the analytics/feedback-loop layer (see {@code com.forgebrain.backend.analytics}
 * and {@code ReelAnalyticsServiceImpl}). Bound from {@code forgebrain.analytics.*}.
 *
 * @param snapshotsDirectory                  where one {@code <jobId>.json} {@code
 *                                             ReelOutcomeSnapshot} is written per completed job
 * @param reportsDirectory                    where generated {@link
 *                                             com.forgebrain.backend.analytics.AnalyticsReport}s
 *                                             are written, as both JSON and markdown
 * @param performanceScoreSmoothing           exponential-moving-average weight (0-1) given to a
 *                                             new review score when updating {@code
 *                                             MemoryState.TopicRecord.performanceScore} — {@code
 *                                             0.5} means the new score counts as much as
 *                                             everything before it combined
 * @param trendSignificanceThreshold           minimum difference between a topic's first-half and
 *                                             second-half average review score before {@code
 *                                             AnalyticsAggregator} calls it {@code IMPROVING}/
 *                                             {@code DECLINING} rather than {@code STABLE}
 * @param revisionPriorityHighThreshold        {@code revisionPriorityScore} at or above this
 *                                             marks a topic {@code NEEDS_REVISIT} with {@code
 *                                             HIGH} memory priority
 * @param strongPerformerApprovalRateThreshold approval rate a topic's aggregate must clear
 *                                             (alongside an average review score of at least
 *                                             0.8) to be marked {@code LOW} memory priority after
 *                                             a successful publish
 * @param cooldownDaysOnRejection              days added to {@code
 *                                             MemoryState.TopicRecord.avoidUntil} the moment a
 *                                             reel for that topic is {@code REJECTED}
 */
@ConfigurationProperties(prefix = "forgebrain.analytics")
public record AnalyticsConfig(
        String snapshotsDirectory,
        String reportsDirectory,
        String snapshotVersion,
        double performanceScoreSmoothing,
        double trendSignificanceThreshold,
        double revisionPriorityHighThreshold,
        double strongPerformerApprovalRateThreshold,
        int cooldownDaysOnRejection
) {
}
