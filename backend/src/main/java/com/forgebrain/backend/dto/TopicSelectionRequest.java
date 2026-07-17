package com.forgebrain.backend.dto;

import com.forgebrain.backend.models.TopicSelectionDecision;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for triggering a topic selection run via a future API endpoint.
 */
public record TopicSelectionRequest(
        @NotNull TopicSelectionDecision.Mode mode
) {
}
