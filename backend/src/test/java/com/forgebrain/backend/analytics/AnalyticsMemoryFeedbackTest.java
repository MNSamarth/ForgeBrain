package com.forgebrain.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalyticsMemoryFeedback} — per this mission's Part 7 ("memory feedback
 * updates"). Pure computation, no Spring context, no file I/O.
 */
class AnalyticsMemoryFeedbackTest {

    private final AnalyticsConfig config = new AnalyticsConfig("unused", "unused", "1.0.0", 0.5, 0.05, 0.6, 0.8, 14);
    private final AnalyticsMemoryFeedback feedback = new AnalyticsMemoryFeedback(config);

    @Test
    void createsAFreshRecordWhenNoneExistsYet() {
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.8,
                ReelOutcomeSnapshot.Outcome.PUBLISHED);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 1, 0.8, 1.0, 0.0, 0.0,
                TopicPerformanceAggregate.Trend.INSUFFICIENT_DATA, 0.0);

        MemoryState.TopicRecord updated = feedback.apply(null, snapshot, aggregate);

        assertThat(updated.topicId()).isEqualTo("topic-1");
        assertThat(updated.title()).isEqualTo("Title for topic-1");
        assertThat(updated.performanceScore()).isEqualTo(0.8);
        assertThat(updated.status()).isEqualTo(Topic.Status.POSTED);
        assertThat(updated.priority()).isIn(MemoryState.Priority.NORMAL, MemoryState.Priority.LOW);
    }

    @Test
    void blendsPerformanceScoreWithTheExistingScoreUsingTheConfiguredSmoothing() {
        MemoryState.TopicRecord existing = existingRecord(0.6, MemoryState.Priority.NORMAL, Topic.Status.POSTED, 0);
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.APPROVED, 1.0,
                ReelOutcomeSnapshot.Outcome.PUBLISHED);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 2, 0.8, 1.0, 0.0, 0.0,
                TopicPerformanceAggregate.Trend.STABLE, 0.0);

        MemoryState.TopicRecord updated = feedback.apply(existing, snapshot, aggregate);

        // smoothing = 0.5: 0.6 * 0.5 + 1.0 * 0.5 = 0.8
        assertThat(updated.performanceScore()).isEqualTo(0.8);
    }

    @Test
    void aRejectedReelForcesNeedsRevisitHighPriorityAndACooldown() {
        MemoryState.TopicRecord existing = existingRecord(0.7, MemoryState.Priority.NORMAL, Topic.Status.POSTED, 0);
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.REJECTED, 0.2,
                ReelOutcomeSnapshot.Outcome.REJECTED);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 1, 0.2, 0.0, 1.0, 0.0,
                TopicPerformanceAggregate.Trend.INSUFFICIENT_DATA, 0.6);

        MemoryState.TopicRecord updated = feedback.apply(existing, snapshot, aggregate);

        assertThat(updated.status()).isEqualTo(Topic.Status.NEEDS_REVISIT);
        assertThat(updated.priority()).isEqualTo(MemoryState.Priority.HIGH);
        assertThat(updated.avoidUntil()).isEqualTo(java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(14));
        assertThat(updated.revisionCount()).isEqualTo(existing.revisionCount());
    }

    @Test
    void aHighRevisionPriorityAggregateFlagsNeedsRevisitEvenWithoutAFreshRejection() {
        MemoryState.TopicRecord existing = existingRecord(0.5, MemoryState.Priority.NORMAL, Topic.Status.POSTED, 2);
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.NEEDS_REVISION, 0.5,
                ReelOutcomeSnapshot.Outcome.NEEDS_REVISION);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 3, 0.5, 0.33, 0.0, 0.67,
                TopicPerformanceAggregate.Trend.DECLINING, 0.7);

        MemoryState.TopicRecord updated = feedback.apply(existing, snapshot, aggregate);

        assertThat(updated.status()).isEqualTo(Topic.Status.NEEDS_REVISIT);
        assertThat(updated.priority()).isEqualTo(MemoryState.Priority.HIGH);
        assertThat(updated.revisionCount()).isEqualTo(existing.revisionCount() + 1);
    }

    @Test
    void aStrongPerformerIsMarkedPostedWithLowPriority() {
        MemoryState.TopicRecord existing = existingRecord(0.85, MemoryState.Priority.NORMAL, Topic.Status.NOT_COVERED,
                0);
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.9,
                ReelOutcomeSnapshot.Outcome.PUBLISHED);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 3, 0.88, 1.0, 0.0, 0.0,
                TopicPerformanceAggregate.Trend.IMPROVING, 0.0);

        MemoryState.TopicRecord updated = feedback.apply(existing, snapshot, aggregate);

        assertThat(updated.status()).isEqualTo(Topic.Status.POSTED);
        assertThat(updated.priority()).isEqualTo(MemoryState.Priority.LOW);
        assertThat(updated.lastPostedAt()).isNotNull();
    }

    @Test
    void neverOverwritesTitleDifficultyOrRelatedTopicsFromAnExistingRecord() {
        MemoryState.TopicRecord existing = new MemoryState.TopicRecord("topic-1", "Curriculum Title",
                Topic.Status.POSTED, Topic.Difficulty.INTERMEDIATE, 3, Instant.now(), Instant.now(), 0, 0.7, null,
                null, MemoryState.Priority.NORMAL, null, List.of("topic-2"), "old notes");
        ReelOutcomeSnapshot snapshot = snapshot("topic-1", ReviewResult.Verdict.APPROVED, 0.8,
                ReelOutcomeSnapshot.Outcome.PUBLISHED);
        TopicPerformanceAggregate aggregate = aggregate("topic-1", 4, 0.75, 0.9, 0.0, 0.0,
                TopicPerformanceAggregate.Trend.STABLE, 0.0);

        MemoryState.TopicRecord updated = feedback.apply(existing, snapshot, aggregate);

        assertThat(updated.title()).isEqualTo("Curriculum Title");
        assertThat(updated.difficulty()).isEqualTo(Topic.Difficulty.INTERMEDIATE);
        assertThat(updated.relatedTopics()).containsExactly("topic-2");
        assertThat(updated.timesUsed()).isEqualTo(3);
    }

    private static MemoryState.TopicRecord existingRecord(Double performanceScore, MemoryState.Priority priority,
            Topic.Status status, int revisionCount) {
        return new MemoryState.TopicRecord("topic-1", "Title for topic-1", status, Topic.Difficulty.BEGINNER, 1,
                Instant.now(), null, revisionCount, performanceScore, null, null, priority, null, List.of(), null);
    }

    private static ReelOutcomeSnapshot snapshot(String topicId, ReviewResult.Verdict verdict, double score,
            ReelOutcomeSnapshot.Outcome outcome) {
        return new ReelOutcomeSnapshot(UUID.randomUUID().toString(), "job-1", topicId, "Title for " + topicId,
                ContentStrategy.HookType.MYTH, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                List.of(Script.Platform.YOUTUBE_SHORTS), 40.0, verdict, score,
                outcome == ReelOutcomeSnapshot.Outcome.PUBLISHED ? "READY" : null, Map.of(), outcome, 0, false,
                List.of(), 0, null, Instant.now(), Instant.now(), "1.0.0");
    }

    private static TopicPerformanceAggregate aggregate(String topicId, int sampleSize, double averageReviewScore,
            double approvalRate, double rejectionRate, double revisionRate, TopicPerformanceAggregate.Trend trend,
            double revisionPriorityScore) {
        return new TopicPerformanceAggregate(topicId, "Title for " + topicId, sampleSize, averageReviewScore,
                approvalRate, rejectionRate, revisionRate, 0.0, trend, revisionPriorityScore, Instant.now());
    }
}
