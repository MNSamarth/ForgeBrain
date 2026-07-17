package com.forgebrain.backend.entities;

import java.time.Instant;

/**
 * Persisted record of one topic's run through the pipeline — the storable counterpart of
 * {@link com.forgebrain.backend.pipeline.PipelineContext}, which is in-memory only. Storing
 * runs allows a run to be resumed or audited after an application restart, which an
 * in-memory-only context cannot survive.
 */
public class PipelineRunEntity {

    private String runId;
    private String topicId;
    private String status;
    private String currentStage;
    private Instant startedAt;
    private Instant updatedAt;

    public PipelineRunEntity() {
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
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

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
