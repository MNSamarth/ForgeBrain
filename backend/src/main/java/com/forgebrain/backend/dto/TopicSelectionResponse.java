package com.forgebrain.backend.dto;

import com.forgebrain.backend.models.TopicSelectionDecision;

/**
 * Response DTO wrapping a {@link TopicSelectionDecision} for a future API endpoint. Currently
 * a thin pass-through — kept as its own type rather than returning the model directly so the
 * API's response shape isn't hard-wired to the pipeline's internal model (see dto/package-info.java).
 */
public record TopicSelectionResponse(
        TopicSelectionDecision decision
) {
}
