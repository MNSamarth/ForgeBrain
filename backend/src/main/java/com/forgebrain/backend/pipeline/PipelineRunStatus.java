package com.forgebrain.backend.pipeline;

/**
 * The status of one topic's full run through the pipeline, as distinct from an individual
 * {@link com.forgebrain.backend.models.RenderJob}'s status — a run spans all fourteen stages
 * in docs/PIPELINE.md, of which rendering is only one.
 */
public enum PipelineRunStatus {
    NOT_STARTED,
    IN_PROGRESS,
    AWAITING_REVIEW,
    NEEDS_REVISION,
    COMPLETED,
    FAILED
}
