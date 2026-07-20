package com.forgebrain.backend.analytics;

import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Topic;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Turns one {@link ReelOutcomeSnapshot} plus its topic's rolled-up {@link
 * TopicPerformanceAggregate} into an updated {@code MemoryState.TopicRecord} — the write side of
 * the feedback loop {@code ReelAnalyticsServiceImpl} closes via {@code
 * MemoryService#updateTopicRecord}. A pure function so the decision logic is testable without any
 * file I/O.
 *
 * <p>Only fields this component owns are touched: {@code performanceScore}, {@code
 * revisionCount}, {@code priority}, {@code avoidUntil}, {@code status}, and {@code notes}. {@code
 * title}, {@code difficulty}, {@code timesUsed}, {@code lastUsedAt}, and {@code relatedTopics}
 * are always carried forward from the existing record untouched — those mirror curriculum
 * structure and topic-selection bookkeeping this component has no business overwriting. {@code
 * retentionScore} and {@code audienceResponse} are reserved for real audience data (see {@code
 * PerformanceSnapshot}) and are likewise left untouched.
 */
public class AnalyticsMemoryFeedback {

    private final AnalyticsConfig config;

    public AnalyticsMemoryFeedback(AnalyticsConfig config) {
        this.config = config;
    }

    /**
     * @param existing  the topic's current record, or {@code null} if it somehow has none yet
     *                  (defensive — in practice {@code markTopicInProgress} always runs first)
     * @param snapshot  the just-captured snapshot for this topic
     * @param aggregate this topic's rolled-up performance, including the new snapshot
     */
    public MemoryState.TopicRecord apply(MemoryState.TopicRecord existing, ReelOutcomeSnapshot snapshot,
            TopicPerformanceAggregate aggregate) {
        Double newPerformanceScore = updatedPerformanceScore(existing, snapshot);
        int newRevisionCount = (existing == null ? 0 : existing.revisionCount())
                + (snapshot.reviewVerdict() == ReviewResult.Verdict.NEEDS_REVISION ? 1 : 0);

        Topic.Status newStatus = existing == null ? Topic.Status.NOT_COVERED : existing.status();
        MemoryState.Priority newPriority = existing == null ? MemoryState.Priority.NORMAL : existing.priority();
        LocalDate newAvoidUntil = existing == null ? null : existing.avoidUntil();
        Instant newLastPostedAt = existing == null ? null : existing.lastPostedAt();

        if (snapshot.outcome() == ReelOutcomeSnapshot.Outcome.REJECTED) {
            newStatus = Topic.Status.NEEDS_REVISIT;
            newPriority = MemoryState.Priority.HIGH;
            newAvoidUntil = LocalDate.now(ZoneOffset.UTC).plusDays(config.cooldownDaysOnRejection());
        } else if (aggregate.revisionPriorityScore() >= config.revisionPriorityHighThreshold()) {
            newStatus = Topic.Status.NEEDS_REVISIT;
            newPriority = MemoryState.Priority.HIGH;
        } else if (snapshot.outcome() == ReelOutcomeSnapshot.Outcome.PUBLISHED) {
            newStatus = Topic.Status.POSTED;
            newLastPostedAt = snapshot.capturedAt();
            boolean strongPerformer = aggregate.approvalRate() >= config.strongPerformerApprovalRateThreshold()
                    && aggregate.averageReviewScore() >= 0.8;
            newPriority = strongPerformer ? MemoryState.Priority.LOW : MemoryState.Priority.NORMAL;
        }

        String notes = "Analytics: " + aggregate.sampleSize() + " reel(s) analyzed, avg review score "
                + aggregate.averageReviewScore() + ", approval rate " + Math.round(aggregate.approvalRate() * 100)
                + "%, trend " + aggregate.trendDirection() + ". Updated " + snapshot.capturedAt() + ".";

        return new MemoryState.TopicRecord(
                snapshot.topicId(),
                existing == null ? snapshot.topicTitle() : existing.title(),
                newStatus,
                existing == null ? null : existing.difficulty(),
                existing == null ? 0 : existing.timesUsed(),
                existing == null ? null : existing.lastUsedAt(),
                newLastPostedAt,
                newRevisionCount,
                newPerformanceScore,
                existing == null ? null : existing.retentionScore(),
                existing == null ? null : existing.audienceResponse(),
                newPriority,
                newAvoidUntil,
                existing == null ? List.of() : existing.relatedTopics(),
                notes
        );
    }

    private Double updatedPerformanceScore(MemoryState.TopicRecord existing, ReelOutcomeSnapshot snapshot) {
        if (snapshot.reviewScore() == null) {
            return existing == null ? null : existing.performanceScore();
        }
        if (existing == null || existing.performanceScore() == null) {
            return snapshot.reviewScore();
        }
        double smoothing = config.performanceScoreSmoothing();
        double blended = existing.performanceScore() * (1 - smoothing) + snapshot.reviewScore() * smoothing;
        return Math.round(blended * 1000.0) / 1000.0;
    }
}
