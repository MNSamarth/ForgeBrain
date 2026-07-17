package com.forgebrain.backend.vertex;

import java.util.Map;

/**
 * A single, narrowly-scoped prompt request to Vertex AI. Mirrors the "one narrow prompt per
 * pipeline stage" principle in docs/ARCHITECTURE.md Section 8 — {@code promptTemplate} names
 * which of the stage-specific prompts (planner, research, teaching, review, storyboard, and
 * so on) this request is for, rather than one general-purpose prompt trying to do everything.
 *
 * @param modelId        which Vertex AI model to call, from {@code VertexAiConfig}
 * @param promptTemplate identifies the narrow, stage-specific prompt being issued
 * @param variables      the values that fill in promptTemplate's placeholders
 */
public record VertexAiPromptRequest(
        String modelId,
        String promptTemplate,
        Map<String, Object> variables
) {
}
