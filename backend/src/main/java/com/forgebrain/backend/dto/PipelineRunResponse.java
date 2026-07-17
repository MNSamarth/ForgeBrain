package com.forgebrain.backend.dto;

import com.forgebrain.backend.pipeline.PipelineRunStatus;
import com.forgebrain.backend.shared.PipelineStage;

/**
 * Response DTO summarizing a pipeline run's current state for a future API endpoint, without
 * exposing the full, potentially large {@link com.forgebrain.backend.pipeline.PipelineContext}
 * (which carries every intermediate artifact) over the wire.
 */
public record PipelineRunResponse(
        String topicId,
        PipelineRunStatus status,
        PipelineStage currentStage
) {
}
