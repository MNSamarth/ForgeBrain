package com.forgebrain.backend.analytics;

import java.time.Instant;
import java.util.List;

/**
 * A readable rollup of every {@link ReelOutcomeSnapshot} captured within a time window — written
 * to disk as both JSON and a short markdown summary by {@code ReelAnalyticsServiceImpl}.
 *
 * @param topPerformingTopics       up to 5 topics with the highest {@code averageReviewScore},
 *                                  highest first
 * @param weakTopics                up to 5 topics with the lowest {@code averageReviewScore},
 *                                  lowest first
 * @param topicsWithDecliningTrend  every topic whose {@code trendDirection} is {@code DECLINING}
 *                                  — this report's answer to "topic drift"
 * @param recommendedRevisitTopics  topic ids whose {@code revisionPriorityScore} cleared {@code
 *                                  AnalyticsConfig#revisionPriorityHighThreshold()}, highest
 *                                  priority first — the same signal {@code
 *                                  AnalyticsMemoryFeedback} uses to flag a topic {@code
 *                                  NEEDS_REVISIT}, surfaced here for human review
 */
public record AnalyticsReport(
        String reportId,
        Instant windowStart,
        Instant windowEnd,
        int totalReelsAnalyzed,
        List<TopicPerformanceAggregate> topPerformingTopics,
        List<TopicPerformanceAggregate> weakTopics,
        List<TopicPerformanceAggregate> topicsWithDecliningTrend,
        List<DimensionPerformanceAggregate> hookTypePerformance,
        List<DimensionPerformanceAggregate> teachingStylePerformance,
        List<DimensionPerformanceAggregate> platformPerformance,
        ReviewTrendSummary reviewTrends,
        PublishReadinessTrendSummary publishReadinessTrends,
        List<String> recommendedRevisitTopics,
        Instant generatedAt
) {

    public record ReviewTrendSummary(
            double averageReviewScore,
            double approvalRate,
            double rejectionRate,
            double revisionRate
    ) {
    }

    public record PublishReadinessTrendSummary(
            int publishedCount,
            int publishFailedCount,
            int needsRevisionCount,
            int rejectedCount,
            int failedCount
    ) {
    }
}
