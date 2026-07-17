package com.forgebrain.backend.vertex;

/**
 * The raw response from a {@link VertexAiPromptRequest}, before it's parsed into a specific
 * pipeline model (e.g. {@link com.forgebrain.backend.models.ResearchResult}). Kept generic and
 * un-typed at this layer deliberately — parsing/validating into a structured model is each
 * calling service's own responsibility, not this client's.
 *
 * @param rawText     the model's raw text output, expected to be JSON per
 *                    docs/ARCHITECTURE.md's "structured outputs over freeform text" principle
 * @param modelId     which model actually produced this response
 * @param finishReason why generation stopped (e.g. "stop", "length", "safety")
 */
public record VertexAiPromptResponse(
        String rawText,
        String modelId,
        String finishReason
) {
}
