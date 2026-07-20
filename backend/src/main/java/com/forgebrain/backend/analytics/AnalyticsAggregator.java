package com.forgebrain.backend.analytics;

import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic, non-AI aggregation over {@link ReelOutcomeSnapshot}s — the same "mechanical
 * proxy, not true judgment" philosophy {@code QualityScorer} documents for review scoring. Plain
 * class, not a Spring bean, owned directly by {@code ReelAnalyticsServiceImpl} (mirrors {@code
 * QualityScorer}/{@code PublishingMetadataGenerator}).
 */
public class AnalyticsAggregator {

    private final AnalyticsConfig config;

    public AnalyticsAggregator(AnalyticsConfig config) {
        this.config = config;
    }

    /**
     * Rolls up every snapshot for one topic. {@code snapshots} need not be pre-sorted — this
     * method sorts a defensive copy by {@link ReelOutcomeSnapshot#jobCreatedAt()} before computing
     * the trend, so caller-supplied ordering never affects the result.
     */
    public TopicPerformanceAggregate aggregateTopic(String topicId, String topicTitle,
            List<ReelOutcomeSnapshot> snapshots) {
        List<ReelOutcomeSnapshot> chronological = snapshots.stream()
                .sorted(Comparator.comparing(ReelOutcomeSnapshot::jobCreatedAt))
                .toList();

        int sampleSize = chronological.size();
        double averageReviewScore = averageReviewScore(chronological);

        long reviewed = chronological.stream().filter(s -> s.reviewVerdict() != null).count();
        long approved = countByVerdict(chronological, ReviewResult.Verdict.APPROVED);
        long rejected = countByVerdict(chronological, ReviewResult.Verdict.REJECTED);
        long revision = countByVerdict(chronological, ReviewResult.Verdict.NEEDS_REVISION);

        double approvalRate = rate(approved, reviewed);
        double rejectionRate = rate(rejected, reviewed);
        double revisionRate = rate(revision, reviewed);
        double fallbackRate = rate(chronological.stream().filter(ReelOutcomeSnapshot::fallbackUsed).count(),
                sampleSize);

        TopicPerformanceAggregate.Trend trend = computeTrend(chronological);
        double revisionPriorityScore = round(Math.min(1.0, rejectionRate * 0.6 + revisionRate * 0.3
                + (trend == TopicPerformanceAggregate.Trend.DECLINING ? 0.2 : 0.0)));

        return new TopicPerformanceAggregate(topicId, topicTitle, sampleSize, round(averageReviewScore),
                round(approvalRate), round(rejectionRate), round(revisionRate), round(fallbackRate), trend,
                revisionPriorityScore, Instant.now());
    }

    public List<DimensionPerformanceAggregate> aggregateByHookType(List<ReelOutcomeSnapshot> snapshots) {
        Map<ContentStrategy.HookType, List<ReelOutcomeSnapshot>> grouped = snapshots.stream()
                .filter(s -> s.hookType() != null)
                .collect(Collectors.groupingBy(ReelOutcomeSnapshot::hookType, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> buildDimensionAggregate(DimensionPerformanceAggregate.Dimension.HOOK_TYPE, e.getKey().name(),
                        e.getValue()))
                .sorted(Comparator.comparing(DimensionPerformanceAggregate::value))
                .toList();
    }

    public List<DimensionPerformanceAggregate> aggregateByTeachingStyle(List<ReelOutcomeSnapshot> snapshots) {
        Map<ContentStrategy.TeachingStyle, List<ReelOutcomeSnapshot>> grouped = snapshots.stream()
                .filter(s -> s.teachingStyle() != null)
                .collect(Collectors.groupingBy(ReelOutcomeSnapshot::teachingStyle, LinkedHashMap::new,
                        Collectors.toList()));
        return grouped.entrySet().stream()
                .map(e -> buildDimensionAggregate(DimensionPerformanceAggregate.Dimension.TEACHING_STYLE,
                        e.getKey().name(), e.getValue()))
                .sorted(Comparator.comparing(DimensionPerformanceAggregate::value))
                .toList();
    }

    public List<DimensionPerformanceAggregate> aggregateByPlatform(List<ReelOutcomeSnapshot> snapshots) {
        Map<Script.Platform, List<ReelOutcomeSnapshot>> grouped = new LinkedHashMap<>();
        for (ReelOutcomeSnapshot snapshot : snapshots) {
            for (Script.Platform platform : snapshot.platformTargets()) {
                grouped.computeIfAbsent(platform, p -> new ArrayList<>()).add(snapshot);
            }
        }
        return grouped.entrySet().stream()
                .map(e -> buildDimensionAggregate(DimensionPerformanceAggregate.Dimension.PLATFORM, e.getKey().name(),
                        e.getValue()))
                .sorted(Comparator.comparing(DimensionPerformanceAggregate::value))
                .toList();
    }

    /**
     * Composes the full {@link AnalyticsReport} for a window's worth of snapshots. {@code
     * windowSnapshots} is expected to already be filtered to the window by the caller (see {@code
     * ReelAnalyticsServiceImpl#generateReport}).
     */
    public AnalyticsReport buildReport(List<ReelOutcomeSnapshot> windowSnapshots, Instant windowStart,
            Instant windowEnd) {
        Map<String, List<ReelOutcomeSnapshot>> byTopic = windowSnapshots.stream()
                .filter(s -> s.topicId() != null)
                .collect(Collectors.groupingBy(ReelOutcomeSnapshot::topicId, LinkedHashMap::new, Collectors.toList()));

        List<TopicPerformanceAggregate> topicAggregates = byTopic.entrySet().stream()
                .map(e -> aggregateTopic(e.getKey(), topicTitleOf(e.getValue()), e.getValue()))
                .toList();

        List<TopicPerformanceAggregate> topPerforming = topicAggregates.stream()
                .sorted(Comparator.comparingDouble(TopicPerformanceAggregate::averageReviewScore).reversed())
                .limit(5)
                .toList();
        List<TopicPerformanceAggregate> weak = topicAggregates.stream()
                .sorted(Comparator.comparingDouble(TopicPerformanceAggregate::averageReviewScore))
                .limit(5)
                .toList();
        List<TopicPerformanceAggregate> declining = topicAggregates.stream()
                .filter(a -> a.trendDirection() == TopicPerformanceAggregate.Trend.DECLINING)
                .toList();
        List<String> recommendedRevisit = topicAggregates.stream()
                .filter(a -> a.revisionPriorityScore() >= config.revisionPriorityHighThreshold())
                .sorted(Comparator.comparingDouble(TopicPerformanceAggregate::revisionPriorityScore).reversed())
                .map(TopicPerformanceAggregate::topicId)
                .limit(10)
                .toList();

        long reviewed = windowSnapshots.stream().filter(s -> s.reviewVerdict() != null).count();
        AnalyticsReport.ReviewTrendSummary reviewTrends = new AnalyticsReport.ReviewTrendSummary(
                round(averageReviewScore(windowSnapshots)),
                round(rate(countByVerdict(windowSnapshots, ReviewResult.Verdict.APPROVED), reviewed)),
                round(rate(countByVerdict(windowSnapshots, ReviewResult.Verdict.REJECTED), reviewed)),
                round(rate(countByVerdict(windowSnapshots, ReviewResult.Verdict.NEEDS_REVISION), reviewed)));

        AnalyticsReport.PublishReadinessTrendSummary publishTrends = new AnalyticsReport.PublishReadinessTrendSummary(
                countByOutcome(windowSnapshots, ReelOutcomeSnapshot.Outcome.PUBLISHED),
                countByOutcome(windowSnapshots, ReelOutcomeSnapshot.Outcome.PUBLISH_FAILED),
                countByOutcome(windowSnapshots, ReelOutcomeSnapshot.Outcome.NEEDS_REVISION),
                countByOutcome(windowSnapshots, ReelOutcomeSnapshot.Outcome.REJECTED),
                countByOutcome(windowSnapshots, ReelOutcomeSnapshot.Outcome.FAILED));

        return new AnalyticsReport(UUID.randomUUID().toString(), windowStart, windowEnd, windowSnapshots.size(),
                topPerforming, weak, declining, aggregateByHookType(windowSnapshots),
                aggregateByTeachingStyle(windowSnapshots), aggregateByPlatform(windowSnapshots), reviewTrends,
                publishTrends, recommendedRevisit, Instant.now());
    }

    private TopicPerformanceAggregate.Trend computeTrend(List<ReelOutcomeSnapshot> chronological) {
        List<Double> scores = chronological.stream()
                .map(ReelOutcomeSnapshot::reviewScore)
                .filter(Objects::nonNull)
                .toList();
        if (scores.size() < 2) {
            return TopicPerformanceAggregate.Trend.INSUFFICIENT_DATA;
        }
        int mid = scores.size() / 2;
        double firstHalfAverage = average(scores.subList(0, mid));
        double secondHalfAverage = average(scores.subList(mid, scores.size()));
        double delta = secondHalfAverage - firstHalfAverage;
        if (delta > config.trendSignificanceThreshold()) {
            return TopicPerformanceAggregate.Trend.IMPROVING;
        }
        if (delta < -config.trendSignificanceThreshold()) {
            return TopicPerformanceAggregate.Trend.DECLINING;
        }
        return TopicPerformanceAggregate.Trend.STABLE;
    }

    private DimensionPerformanceAggregate buildDimensionAggregate(DimensionPerformanceAggregate.Dimension dimension,
            String value, List<ReelOutcomeSnapshot> snapshots) {
        long reviewed = snapshots.stream().filter(s -> s.reviewVerdict() != null).count();
        long approved = countByVerdict(snapshots, ReviewResult.Verdict.APPROVED);
        List<String> contributingTopicIds = snapshots.stream()
                .map(ReelOutcomeSnapshot::topicId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String aggregateId = dimension.name().toLowerCase(java.util.Locale.ROOT) + ":" + value;
        return new DimensionPerformanceAggregate(aggregateId, dimension, value, snapshots.size(),
                round(averageReviewScore(snapshots)), round(rate(approved, reviewed)), contributingTopicIds,
                Instant.now());
    }

    private static String topicTitleOf(List<ReelOutcomeSnapshot> snapshots) {
        return snapshots.stream()
                .map(ReelOutcomeSnapshot::topicTitle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static double averageReviewScore(List<ReelOutcomeSnapshot> snapshots) {
        List<Double> scores = snapshots.stream().map(ReelOutcomeSnapshot::reviewScore).filter(Objects::nonNull).toList();
        return average(scores);
    }

    private static double average(List<Double> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static long countByVerdict(List<ReelOutcomeSnapshot> snapshots, ReviewResult.Verdict verdict) {
        return snapshots.stream().filter(s -> s.reviewVerdict() == verdict).count();
    }

    private static int countByOutcome(List<ReelOutcomeSnapshot> snapshots, ReelOutcomeSnapshot.Outcome outcome) {
        return (int) snapshots.stream().filter(s -> s.outcome() == outcome).count();
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
