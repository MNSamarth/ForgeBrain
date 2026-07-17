package com.forgebrain.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for starting or advancing a pipeline run via a future API endpoint.
 */
public record PipelineRunRequest(
        @NotBlank String topicId
) {
}
