package com.forgebrain.backend.models;

import java.time.Instant;
import java.util.List;

/**
 * The topic selector's decision output. Mirrors {@code brain/topic-selector-schema.json}.
 * {@code selectedTopicId} is {@code null} exactly when no eligible candidate existed — a valid,
 * complete answer, not an error (see brain/topic-selector-spec.md Section 8).
 *
 * @see <a href="../../../../../../../../brain/topic-selector-schema.json">brain/topic-selector-schema.json</a>
 */
public record TopicSelectionDecision(
        Mode mode,
        String selectedTopicId,
        String selectedTopicTitle,
        String reason,
        Double score,
        List<ScoredCandidate> candidateTopics,
        List<RejectedTopic> rejectedTopics,
        List<String> blockedByPrerequisite,
        List<String> blockedByRecentUse,
        List<String> needsRevision,
        Instant selectionTimestamp
) {

    public enum Mode {
        NEXT_TOPIC, REVISION_TOPIC, HIGH_PERFORMANCE_TOPIC, GAP_FILLER_TOPIC
    }

    public record ScoredCandidate(String topicId, String title, double score, Factors factors) {
    }

    public record Factors(
            double educationalSequenceFit,
            double noveltyScore,
            double repetitionPenalty,
            double performanceBoost,
            double revisionPriority,
            double audienceDemandSignal
    ) {
    }

    public record RejectedTopic(String topicId, String title, String reason) {
    }
}
