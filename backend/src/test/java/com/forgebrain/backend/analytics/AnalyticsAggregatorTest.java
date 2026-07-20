package com.forgebrain.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalyticsAggregator} — per this mission's Part 7 ("aggregation by topic
 * and style"). Pure computation, no Spring context, no file I/O.
 */
class AnalyticsAggregatorTest {

    private final AnalyticsConfig config = new AnalyticsConfig("unused", "unused", "1.0.0", 0.5, 0.05, 0.6, 0.8, 14);
    private final AnalyticsAggregator aggregator = new AnalyticsAggregator(config);

    @Test
    void aggregatesASingleApprovedSnapshotAsInsufficientDataForTrend() {
        List<ReelOutcomeSnapshot> snapshots = List.of(
                snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.8, ReelOutcomeSnapshot.Outcome.PUBLISHED,
                        Instant.now()));

        TopicPerformanceAggregate aggregate = aggregator.aggregateTopic("topic-1", "The Topic", snapshots);

        assertThat(aggregate.sampleSize()).isEqualTo(1);
        assertThat(aggregate.averageReviewScore()).isEqualTo(0.8);
        assertThat(aggregate.approvalRate()).isEqualTo(1.0);
        assertThat(aggregate.trendDirection()).isEqualTo(TopicPerformanceAggregate.Trend.INSUFFICIENT_DATA);
    }

    @Test
    void detectsADecliningTrendFromChronologicallyWorseningScores() {
        Instant t0 = Instant.now().minus(4, ChronoUnit.DAYS);
        List<ReelOutcomeSnapshot> snapshots = List.of(
                snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.9, ReelOutcomeSnapshot.Outcome.PUBLISHED, t0),
                snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.85, ReelOutcomeSnapshot.Outcome.PUBLISHED,
                        t0.plus(1, ChronoUnit.DAYS)),
                snapshot("topic-1", ReviewResult.Verdict.NEEDS_REVISION, 0.5, ReelOutcomeSnapshot.Outcome.NEEDS_REVISION,
                        t0.plus(2, ChronoUnit.DAYS)),
                snapshot("topic-1", ReviewResult.Verdict.NEEDS_REVISION, 0.45, ReelOutcomeSnapshot.Outcome.NEEDS_REVISION,
                        t0.plus(3, ChronoUnit.DAYS)));

        TopicPerformanceAggregate aggregate = aggregator.aggregateTopic("topic-1", "The Topic", snapshots);

        assertThat(aggregate.trendDirection()).isEqualTo(TopicPerformanceAggregate.Trend.DECLINING);
        assertThat(aggregate.revisionRate()).isEqualTo(0.5);
        assertThat(aggregate.revisionPriorityScore()).isGreaterThan(0.0);
    }

    @Test
    void detectsAnImprovingTrendRegardlessOfInputOrder() {
        Instant t0 = Instant.now().minus(2, ChronoUnit.DAYS);
        // Deliberately supplied out of chronological order — aggregateTopic must sort internally.
        List<ReelOutcomeSnapshot> snapshots = List.of(
                snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.9, ReelOutcomeSnapshot.Outcome.PUBLISHED,
                        t0.plus(1, ChronoUnit.DAYS)),
                snapshot("topic-1", ReviewResult.Verdict.NEEDS_REVISION, 0.5, ReelOutcomeSnapshot.Outcome.NEEDS_REVISION,
                        t0));

        TopicPerformanceAggregate aggregate = aggregator.aggregateTopic("topic-1", "The Topic", snapshots);

        assertThat(aggregate.trendDirection()).isEqualTo(TopicPerformanceAggregate.Trend.IMPROVING);
    }

    @Test
    void rejectionsDominateTheRevisionPriorityScore() {
        Instant t0 = Instant.now();
        List<ReelOutcomeSnapshot> snapshots = List.of(
                snapshot("topic-1", ReviewResult.Verdict.REJECTED, 0.2, ReelOutcomeSnapshot.Outcome.REJECTED, t0),
                snapshot("topic-1", ReviewResult.Verdict.REJECTED, 0.2, ReelOutcomeSnapshot.Outcome.REJECTED,
                        t0.plus(1, ChronoUnit.DAYS)));

        TopicPerformanceAggregate aggregate = aggregator.aggregateTopic("topic-1", "The Topic", snapshots);

        assertThat(aggregate.rejectionRate()).isEqualTo(1.0);
        assertThat(aggregate.revisionPriorityScore()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void aggregatesByHookTypeAcrossTopics() {
        List<ReelOutcomeSnapshot> snapshots = List.of(
                snapshotWithHook("topic-1", ContentStrategy.HookType.MYTH, 0.9),
                snapshotWithHook("topic-2", ContentStrategy.HookType.MYTH, 0.7),
                snapshotWithHook("topic-3", ContentStrategy.HookType.QUESTION, 0.6));

        List<DimensionPerformanceAggregate> byHook = aggregator.aggregateByHookType(snapshots);

        assertThat(byHook).extracting(DimensionPerformanceAggregate::value)
                .containsExactlyInAnyOrder("MYTH", "QUESTION");
        DimensionPerformanceAggregate myth = byHook.stream().filter(a -> a.value().equals("MYTH")).findFirst()
                .orElseThrow();
        assertThat(myth.sampleSize()).isEqualTo(2);
        assertThat(myth.averageReviewScore()).isEqualTo(0.8);
        assertThat(myth.contributingTopicIds()).containsExactlyInAnyOrder("topic-1", "topic-2");
    }

    @Test
    void aggregatesByPlatformCountingASnapshotOnceForEachTargetedPlatform() {
        ReelOutcomeSnapshot multiPlatform = new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-1", "topic-1",
                "The Topic", ContentStrategy.HookType.MYTH, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS, Script.Platform.INSTAGRAM_REELS), 40.0,
                ReviewResult.Verdict.APPROVED, 0.9, "READY", Map.of(), ReelOutcomeSnapshot.Outcome.PUBLISHED, 0, false,
                List.of(), 0, null, Instant.now(), Instant.now(), "1.0.0");

        List<DimensionPerformanceAggregate> byPlatform = aggregator.aggregateByPlatform(List.of(multiPlatform));

        assertThat(byPlatform).extracting(DimensionPerformanceAggregate::value)
                .containsExactlyInAnyOrder("YOUTUBE_SHORTS", "INSTAGRAM_REELS");
        assertThat(byPlatform).allMatch(a -> a.sampleSize() == 1);
    }

    @Test
    void buildReportRanksTopAndWeakTopicsAndFlagsHighRevisionPriorityAsRecommendedRevisit() {
        Instant now = Instant.now();
        ReelOutcomeSnapshot strong = snapshot("topic-strong", ReviewResult.Verdict.APPROVED, 0.95,
                ReelOutcomeSnapshot.Outcome.PUBLISHED, now);
        ReelOutcomeSnapshot weakA = snapshot("topic-weak", ReviewResult.Verdict.REJECTED, 0.2,
                ReelOutcomeSnapshot.Outcome.REJECTED, now);
        ReelOutcomeSnapshot weakB = snapshot("topic-weak", ReviewResult.Verdict.REJECTED, 0.2,
                ReelOutcomeSnapshot.Outcome.REJECTED, now.plus(1, ChronoUnit.DAYS));

        AnalyticsReport report = aggregator.buildReport(List.of(strong, weakA, weakB), now.minus(7, ChronoUnit.DAYS),
                now.plus(7, ChronoUnit.DAYS));

        assertThat(report.totalReelsAnalyzed()).isEqualTo(3);
        assertThat(report.topPerformingTopics().get(0).topicId()).isEqualTo("topic-strong");
        assertThat(report.weakTopics().get(0).topicId()).isEqualTo("topic-weak");
        assertThat(report.recommendedRevisitTopics()).containsExactly("topic-weak");
        assertThat(report.publishReadinessTrends().publishedCount()).isEqualTo(1);
        assertThat(report.publishReadinessTrends().rejectedCount()).isEqualTo(2);
    }

    private static ReelOutcomeSnapshot snapshot(String topicId, ReviewResult.Verdict verdict, double score,
            ReelOutcomeSnapshot.Outcome outcome, Instant jobCreatedAt) {
        return new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-" + UUID.randomUUID(), topicId,
                "Title for " + topicId, ContentStrategy.HookType.MYTH, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS), 40.0, verdict, score,
                outcome == ReelOutcomeSnapshot.Outcome.PUBLISHED ? "READY" : null, Map.of(), outcome, 0, false,
                List.of(), 0, null, jobCreatedAt, Instant.now(), "1.0.0");
    }

    private static ReelOutcomeSnapshot snapshotWithHook(String topicId, ContentStrategy.HookType hookType,
            double score) {
        return new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-" + UUID.randomUUID(), topicId,
                "Title for " + topicId, hookType, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS), 40.0, ReviewResult.Verdict.APPROVED, score, "READY", Map.of(),
                ReelOutcomeSnapshot.Outcome.PUBLISHED, 0, false, List.of(), 0, null, Instant.now(), Instant.now(),
                "1.0.0");
    }
}
