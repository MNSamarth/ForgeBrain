package com.forgebrain.backend.analytics;

import java.time.Instant;

/**
 * {@link ReelOutcomeSnapshot}s for one topic, rolled up into the signals {@code
 * AnalyticsMemoryFeedback} writes back into {@code MemoryState.TopicRecord} and {@code
 * AnalyticsReport} ranks topics by. See {@code AnalyticsAggregator} for the exact formulas.
 *
 * @param sampleSize             how many snapshots contributed, including failed jobs
 * @param averageReviewScore     mean {@link ReelOutcomeSnapshot#reviewScore()} across snapshots
 *                               that reached review; {@code 0.0} if none did
 * @param approvalRate           share of reviewed snapshots with verdict {@code APPROVED}
 * @param rejectionRate          share of reviewed snapshots with verdict {@code REJECTED}
 * @param revisionRate           share of reviewed snapshots with verdict {@code NEEDS_REVISION}
 * @param fallbackRate           share of all snapshots (including failures) that used a fallback
 * @param trendDirection         see {@link Trend}
 * @param revisionPriorityScore  0-1, higher means more urgent to revisit; see {@code
 *                               AnalyticsAggregator#aggregateTopic}
 */
public record TopicPerformanceAggregate(
        String topicId,
        String topicTitle,
        int sampleSize,
        double averageReviewScore,
        double approvalRate,
        double rejectionRate,
        double revisionRate,
        double fallbackRate,
        Trend trendDirection,
        double revisionPriorityScore,
        Instant lastUpdated
) {

    /**
     * Compares the average review score of the first half of a topic's snapshots (chronological)
     * against the second half. {@code INSUFFICIENT_DATA} below two reviewed snapshots — this
     * aggregator never guesses a direction from a single data point.
     */
    public enum Trend {
        IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA
    }
}
