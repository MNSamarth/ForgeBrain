package com.forgebrain.backend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Persistent memory state: what's been posted, queued, or flagged for revision, and how each
 * topic has performed. Mirrors {@code memory/memory-schema.json}.
 *
 * @see <a href="../../../../../../../../memory/memory-schema.json">memory/memory-schema.json</a>
 */
public record MemoryState(
        String memoryVersion,
        String language,
        Instant lastUpdated,
        String currentTopicId,
        List<QueueEntry> queue,
        List<UsedContentFragment> recentlyUsedHooks,
        List<UsedContentFragment> recentlyUsedExamples,
        Map<String, TopicRecord> topics,
        GlobalStats globalStats
) {

    public record QueueEntry(String topicId, Priority priority, Instant queuedAt, String reason) {
    }

    /**
     * A previously used hook line or code example, tracked independently of topic so a
     * revisited topic doesn't repeat the exact same opening or example. Mirrors
     * memory-schema.json's {@code usedContentFragment} definition.
     */
    public record UsedContentFragment(String topicId, String content, Instant usedAt) {
    }

    public record TopicRecord(
            String topicId,
            String title,
            Topic.Status status,
            Topic.Difficulty difficulty,
            int timesUsed,
            Instant lastUsedAt,
            Instant lastPostedAt,
            int revisionCount,
            Double performanceScore,
            Double retentionScore,
            AudienceResponse audienceResponse,
            Priority priority,
            LocalDate avoidUntil,
            List<String> relatedTopics,
            String notes
    ) {
    }

    public record AudienceResponse(
            Integer viewCount,
            Double watchTimeSeconds,
            Double averageRetentionPercent,
            Integer likes,
            Integer comments,
            Integer shares,
            Integer saves,
            Integer followerConversion
    ) {
    }

    public record GlobalStats(int totalReelsPosted, String lastPostedTopicId, Instant lastPostedAt) {
    }

    public enum Priority {
        LOW, NORMAL, HIGH
    }
}
