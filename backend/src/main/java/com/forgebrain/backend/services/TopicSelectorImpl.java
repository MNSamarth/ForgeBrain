package com.forgebrain.backend.services;

import com.forgebrain.backend.curriculum.CurriculumLoader;
import com.forgebrain.backend.curriculum.RoadmapLevel;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.TopicSelectionDecision;
import com.forgebrain.backend.models.TopicSelectionDecision.Factors;
import com.forgebrain.backend.models.TopicSelectionDecision.Mode;
import com.forgebrain.backend.models.TopicSelectionDecision.RejectedTopic;
import com.forgebrain.backend.models.TopicSelectionDecision.ScoredCandidate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Real implementation of the gate-then-score algorithm in brain/topic-selector-spec.md,
 * weighted per brain/topic-ranking.json. Reconciles {@code curriculum/java-roadmap.json}
 * (via {@link CurriculumLoader}) with the given {@link MemoryState} — never invents a topic
 * outside the curriculum, and never returns a topic silently; every exclusion is recorded.
 */
@Component
public class TopicSelectorImpl implements TopicSelector {

    private static final double HIGH_PERFORMANCE_THRESHOLD = 0.8;
    private static final double SEQUENCE_FIT_DISTANCE_SCALE = 10.0;
    private static final double REPETITION_COOLDOWN_DAYS = 30.0;
    private static final double DEFAULT_PERFORMANCE_SCORE = 0.5;

    private final CurriculumLoader curriculumLoader;

    public TopicSelectorImpl(CurriculumLoader curriculumLoader) {
        this.curriculumLoader = curriculumLoader;
    }

    @Override
    public TopicSelectionDecision selectNextTopic(Mode mode, MemoryState memory, Instant selectionTimestamp) {
        List<PositionedTopic> flattened = flattenCurriculum();
        Map<String, PositionedTopic> byId = new LinkedHashMap<>();
        for (PositionedTopic pt : flattened) {
            byId.put(pt.topic().id(), pt);
        }

        int frontierPosition = frontierPosition(flattened, memory);
        Set<String> queuedTopicIds = memory.queue().stream().map(MemoryState.QueueEntry::topicId).collect(java.util.stream.Collectors.toSet());
        Map<String, Double> highPerformanceRelatedIds = highPerformanceRelatedTopicIds(flattened, memory);

        List<ScoredCandidate> candidates = new ArrayList<>();
        List<RejectedTopic> rejected = new ArrayList<>();
        List<String> blockedByPrerequisite = new ArrayList<>();
        List<String> blockedByRecentUse = new ArrayList<>();
        List<String> needsRevision = new ArrayList<>();

        for (PositionedTopic pt : flattened) {
            Topic topic = pt.topic();
            MemoryState.TopicRecord record = memory.topics().get(topic.id());
            Topic.Status effectiveStatus = record != null ? record.status() : Topic.Status.NOT_COVERED;

            if (effectiveStatus == Topic.Status.NEEDS_REVISIT) {
                needsRevision.add(topic.id());
            }

            if (isCurrentlyInProgress(topic.id(), memory, effectiveStatus)) {
                continue;
            }
            if (!inCandidatePool(mode, topic, effectiveStatus, queuedTopicIds, highPerformanceRelatedIds)) {
                continue;
            }

            boolean checkReadiness = mode != Mode.REVISION_TOPIC;
            if (checkReadiness && !prerequisitesPosted(topic, memory, byId)) {
                blockedByPrerequisite.add(topic.id());
                rejected.add(new RejectedTopic(topic.id(), topic.title(), firstUnmetPrerequisiteReason(topic, memory, byId)));
                continue;
            }
            if (onCooldown(record, selectionTimestamp)) {
                blockedByRecentUse.add(topic.id());
                rejected.add(new RejectedTopic(topic.id(), topic.title(), "On cooldown until " + record.avoidUntil() + "."));
                continue;
            }

            Factors factors = computeFactors(mode, pt, frontierPosition, record, effectiveStatus, highPerformanceRelatedIds, selectionTimestamp);
            double score = weightedScore(mode, factors);
            candidates.add(new ScoredCandidate(topic.id(), topic.title(), round(score), factors));
        }

        candidates.sort(candidateOrdering(byId, frontierPosition));

        if (candidates.isEmpty()) {
            return new TopicSelectionDecision(mode, null, null,
                    noCandidateReason(mode, blockedByPrerequisite, blockedByRecentUse, needsRevision),
                    null, candidates, rejected, blockedByPrerequisite, blockedByRecentUse, needsRevision, selectionTimestamp);
        }

        ScoredCandidate winner = candidates.get(0);
        String reason = winningReason(mode, winner, candidates.size());
        return new TopicSelectionDecision(mode, winner.topicId(), winner.title(), reason, winner.score(),
                candidates, rejected, blockedByPrerequisite, blockedByRecentUse, needsRevision, selectionTimestamp);
    }

    // ---------------------------------------------------------------- gates & candidate pool

    private boolean isCurrentlyInProgress(String topicId, MemoryState memory, Topic.Status effectiveStatus) {
        return topicId.equals(memory.currentTopicId()) && effectiveStatus == Topic.Status.IN_PROGRESS;
    }

    private boolean inCandidatePool(Mode mode, Topic topic, Topic.Status status, Set<String> queuedTopicIds,
            Map<String, Double> highPerformanceRelatedIds) {
        return switch (mode) {
            case NEXT_TOPIC -> status == Topic.Status.NOT_COVERED || status == Topic.Status.QUEUED;
            case REVISION_TOPIC -> status == Topic.Status.NEEDS_REVISIT;
            case HIGH_PERFORMANCE_TOPIC -> (status == Topic.Status.NOT_COVERED || status == Topic.Status.QUEUED)
                    && highPerformanceRelatedIds.containsKey(topic.id());
            case GAP_FILLER_TOPIC -> status == Topic.Status.NOT_COVERED && !queuedTopicIds.contains(topic.id());
        };
    }

    private boolean prerequisitesPosted(Topic topic, MemoryState memory, Map<String, PositionedTopic> byId) {
        for (String prerequisiteId : topic.prerequisites()) {
            MemoryState.TopicRecord prerequisiteRecord = memory.topics().get(prerequisiteId);
            if (prerequisiteRecord == null || prerequisiteRecord.status() != Topic.Status.POSTED) {
                return false;
            }
        }
        return true;
    }

    private String firstUnmetPrerequisiteReason(Topic topic, MemoryState memory, Map<String, PositionedTopic> byId) {
        for (String prerequisiteId : topic.prerequisites()) {
            MemoryState.TopicRecord prerequisiteRecord = memory.topics().get(prerequisiteId);
            if (prerequisiteRecord == null || prerequisiteRecord.status() != Topic.Status.POSTED) {
                String title = byId.containsKey(prerequisiteId) ? byId.get(prerequisiteId).topic().title() : prerequisiteId;
                return "Prerequisite '" + title + "' (" + prerequisiteId + ") has not reached posted status.";
            }
        }
        return "Prerequisite not satisfied.";
    }

    private boolean onCooldown(MemoryState.TopicRecord record, Instant selectionTimestamp) {
        if (record == null || record.avoidUntil() == null) {
            return false;
        }
        return !record.avoidUntil().isBefore(selectionTimestamp.atZone(ZoneOffset.UTC).toLocalDate());
    }

    // ---------------------------------------------------------------------------- scoring

    private Factors computeFactors(Mode mode, PositionedTopic pt, int frontierPosition, MemoryState.TopicRecord record,
            Topic.Status effectiveStatus, Map<String, Double> highPerformanceRelatedIds, Instant now) {
        double sequenceFit = educationalSequenceFit(pt.position(), frontierPosition);
        double novelty = record == null ? 1.0 : 1.0 / (1.0 + record.timesUsed());
        double repetitionPenalty = repetitionPenalty(record, now);
        double performanceBoost = performanceBoost(pt.topic().id(), record, effectiveStatus, highPerformanceRelatedIds);
        double revisionPriority = revisionPriority(mode, record);
        double audienceDemand = 0.0; // not yet populated in Phase 1 — see brain/topic-ranking.json

        return new Factors(round(sequenceFit), round(novelty), round(repetitionPenalty), round(performanceBoost),
                round(revisionPriority), audienceDemand);
    }

    private double educationalSequenceFit(int position, int frontierPosition) {
        int distance = Math.max(0, position - frontierPosition);
        return 1.0 / (1.0 + distance / SEQUENCE_FIT_DISTANCE_SCALE);
    }

    private double repetitionPenalty(MemoryState.TopicRecord record, Instant now) {
        if (record == null || record.lastUsedAt() == null) {
            return 0.0;
        }
        long daysSince = Duration.between(record.lastUsedAt(), now).toDays();
        if (daysSince >= REPETITION_COOLDOWN_DAYS) {
            return 0.0;
        }
        return (REPETITION_COOLDOWN_DAYS - daysSince) / REPETITION_COOLDOWN_DAYS;
    }

    private double performanceBoost(String topicId, MemoryState.TopicRecord record, Topic.Status effectiveStatus,
            Map<String, Double> highPerformanceRelatedIds) {
        if (effectiveStatus == Topic.Status.POSTED) {
            return record.performanceScore() != null ? record.performanceScore() : DEFAULT_PERFORMANCE_SCORE;
        }
        Double inherited = highPerformanceRelatedIds.get(topicId);
        return inherited != null ? inherited * 0.5 : 0.0;
    }

    private double revisionPriority(Mode mode, MemoryState.TopicRecord record) {
        if (mode != Mode.REVISION_TOPIC || record == null) {
            return 0.0;
        }
        double performance = record.performanceScore() != null ? record.performanceScore() : DEFAULT_PERFORMANCE_SCORE;
        return (1.0 - performance) / (1.0 + record.revisionCount());
    }

    private double weightedScore(Mode mode, Factors f) {
        ModeWeights w = ModeWeights.forMode(mode);
        return f.educationalSequenceFit() * w.sequenceFit()
                + f.noveltyScore() * w.novelty()
                + f.performanceBoost() * w.performanceBoost()
                + f.audienceDemandSignal() * w.audienceDemand()
                + f.repetitionPenalty() * w.repetitionPenalty()
                + f.revisionPriority() * w.revisionPriority();
    }

    private Comparator<ScoredCandidate> candidateOrdering(Map<String, PositionedTopic> byId, int frontierPosition) {
        return Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparingInt((ScoredCandidate c) -> Math.abs(byId.get(c.topicId()).position() - frontierPosition))
                .thenComparing(ScoredCandidate::topicId);
    }

    // ------------------------------------------------------------------------- explanations

    private String winningReason(Mode mode, ScoredCandidate winner, int candidateCount) {
        return "Mode " + mode + " selected '" + winner.title() + "' (" + winner.topicId() + ") with score "
                + winner.score() + " out of " + candidateCount + " eligible candidate(s) — highest weighted score "
                + "combining curriculum proximity, novelty, and momentum for this mode.";
    }

    private String noCandidateReason(Mode mode, List<String> blockedByPrerequisite, List<String> blockedByRecentUse, List<String> needsRevision) {
        if (mode == Mode.REVISION_TOPIC && needsRevision.isEmpty()) {
            return "No topic is currently flagged needs_revisit; there is nothing for revision_topic mode to select.";
        }
        if (!blockedByRecentUse.isEmpty() && blockedByPrerequisite.isEmpty()) {
            return "Every otherwise-eligible topic is currently on cooldown (" + blockedByRecentUse.size() + " blocked). No selection until a cooldown expires.";
        }
        if (!blockedByPrerequisite.isEmpty()) {
            return blockedByPrerequisite.size() + " candidate(s) exist but are blocked by unmet prerequisites, and no other topic is eligible for mode " + mode + ".";
        }
        return "No eligible candidate exists for mode " + mode + " given the current curriculum and memory state.";
    }

    // ------------------------------------------------------------------------------- helpers

    private List<PositionedTopic> flattenCurriculum() {
        List<PositionedTopic> flattened = new ArrayList<>();
        int position = 0;
        for (RoadmapLevel level : curriculumLoader.loadFullRoadmap()) {
            for (Topic topic : level.topics()) {
                flattened.add(new PositionedTopic(topic, position++));
            }
        }
        return flattened;
    }

    private int frontierPosition(List<PositionedTopic> flattened, MemoryState memory) {
        for (PositionedTopic pt : flattened) {
            MemoryState.TopicRecord record = memory.topics().get(pt.topic().id());
            Topic.Status status = record != null ? record.status() : Topic.Status.NOT_COVERED;
            if (status != Topic.Status.POSTED) {
                return pt.position();
            }
        }
        return flattened.isEmpty() ? 0 : flattened.get(flattened.size() - 1).position();
    }

    /**
     * topic_id -> highest performance_score among POSTED topics that list it in their
     * curriculum next_topics or memory related_topics. Used both to build the
     * high_performance_topic candidate pool and to compute inherited performance_boost.
     */
    private Map<String, Double> highPerformanceRelatedTopicIds(List<PositionedTopic> flattened, MemoryState memory) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (PositionedTopic pt : flattened) {
            MemoryState.TopicRecord record = memory.topics().get(pt.topic().id());
            if (record == null || record.status() != Topic.Status.POSTED || record.performanceScore() == null) {
                continue;
            }
            if (record.performanceScore() < HIGH_PERFORMANCE_THRESHOLD) {
                continue;
            }
            List<String> related = new ArrayList<>(pt.topic().nextTopics());
            if (record.relatedTopics() != null) {
                related.addAll(record.relatedTopics());
            }
            for (String relatedId : related) {
                result.merge(relatedId, record.performanceScore(), Math::max);
            }
        }
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record PositionedTopic(Topic topic, int position) {
    }

    /**
     * Per-mode weight profiles, mirroring brain/topic-ranking.json exactly. Kept as code
     * constants rather than parsed from that file for this phase — see NEXT_EXECUTION.md.
     */
    private record ModeWeights(double sequenceFit, double novelty, double performanceBoost,
            double audienceDemand, double repetitionPenalty, double revisionPriority) {

        static ModeWeights forMode(Mode mode) {
            return switch (mode) {
                case NEXT_TOPIC -> new ModeWeights(0.45, 0.25, 0.15, 0.15, -0.15, 0.0);
                case REVISION_TOPIC -> new ModeWeights(0.0, 0.0, -0.2, 0.2, 0.0, 0.6);
                case HIGH_PERFORMANCE_TOPIC -> new ModeWeights(0.2, 0.15, 0.5, 0.15, -0.1, 0.0);
                case GAP_FILLER_TOPIC -> new ModeWeights(0.2, 0.2, 0.1, 0.5, -0.1, 0.0);
            };
        }
    }
}
