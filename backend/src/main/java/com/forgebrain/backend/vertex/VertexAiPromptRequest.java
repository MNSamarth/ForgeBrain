package com.forgebrain.backend.vertex;

import java.util.Map;

/**
 * A single, narrowly-scoped prompt request to Vertex AI. Mirrors the "one narrow prompt per
 * pipeline stage" principle in docs/ARCHITECTURE.md Section 8 — {@code promptText} is the
 * fully-built text for one of the stage-specific prompts (planner, research, teaching, review,
 * storyboard, and so on), assembled by the calling service, rather than one general-purpose
 * prompt trying to do everything.
 *
 * @param modelId          which Vertex AI model to call, from {@code VertexAiConfig}
 * @param promptText       the complete prompt text to send, already built by the calling stage
 * @param variables        stage-specific values echoed back with the request for
 *                         logging/debugging; not interpolated by this record itself
 * @param temperature      sampling temperature, or {@code null} to use the SDK default
 * @param maxOutputTokens  output token cap, or {@code null} to use the SDK default
 * @param responseMimeType response MIME type to request, e.g. {@code "application/json"}
 */
public record VertexAiPromptRequest(
        String modelId,
        String promptText,
        Map<String, Object> variables,
        Double temperature,
        Integer maxOutputTokens,
        String responseMimeType
) {

    /**
     * Convenience constructor for the common case: no generation-parameter overrides, JSON
     * output. Used by every caller that doesn't need per-request temperature/token tuning.
     */
    public VertexAiPromptRequest(String modelId, String promptText, Map<String, Object> variables) {
        this(modelId, promptText, variables, null, null, "application/json");
    }
}
