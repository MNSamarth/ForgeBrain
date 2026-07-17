package com.forgebrain.backend.pipeline;

/**
 * Contract for persisting a completed {@link PipelineResult}. Kept as its own small interface
 * (rather than folding this into {@link PipelineOrchestrator}) so the orchestrator's job stays
 * "sequence the stages" and doesn't also own storage mechanics — see {@link
 * PipelineResultStoreImpl} for this phase's local-file implementation.
 */
public interface PipelineResultStore {

    void save(PipelineResult result);
}
