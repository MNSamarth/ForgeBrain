package com.forgebrain.backend.entities;

import java.time.Instant;

/**
 * Persisted per-topic tracking record — the entity-layer counterpart of {@code
 * MemoryState.TopicRecord} (see {@link com.forgebrain.backend.models.MemoryState}), broken
 * out as its own document so a single topic's record can be read or updated without loading
 * the entire memory state blob.
 *
 * <p>Whether topic tracking is ultimately stored this way (one document per topic) or folded
 * entirely into {@link MemoryStateEntity}'s single document is an open implementation
 * decision — see TODO.md. Both entities are provided now so either direction is representable.
 */
public class TopicEntity {

    private String topicId;
    private String status;
    private int timesUsed;
    private Instant lastUsedAt;
    private Instant lastPostedAt;
    private Double performanceScore;
    private Double retentionScore;

    public TopicEntity() {
    }

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(int timesUsed) {
        this.timesUsed = timesUsed;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getLastPostedAt() {
        return lastPostedAt;
    }

    public void setLastPostedAt(Instant lastPostedAt) {
        this.lastPostedAt = lastPostedAt;
    }

    public Double getPerformanceScore() {
        return performanceScore;
    }

    public void setPerformanceScore(Double performanceScore) {
        this.performanceScore = performanceScore;
    }

    public Double getRetentionScore() {
        return retentionScore;
    }

    public void setRetentionScore(Double retentionScore) {
        this.retentionScore = retentionScore;
    }
}
