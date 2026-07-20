package com.forgebrain.backend.ai;

/**
 * Static metadata for one reusable prompt type, registered once in {@link PromptRegistry}.
 * Distinct from the actual prompt <em>text</em> — which is still built per call by each stage's
 * own {@code *PromptBuilder} from run-specific data (a lesson, a research brief, ...) — this is
 * only the part that stays fixed across every call to the same prompt: which model handles it,
 * what generation parameters to use, and why it exists.
 *
 * @param name             stable identifier a caller passes to {@link
 *                         com.forgebrain.backend.ai.AiPromptExecution#promptName()}, e.g. {@code
 *                         "research"}
 * @param version          this prompt definition's own version, echoed into {@link
 *                         AiGatewayResult#promptVersion()} for provenance
 * @param purpose          one-line human-readable description of what this prompt asks the model
 *                         to produce
 * @param modelId          which Vertex AI model handles this prompt — the single place "model
 *                         routing" lives; changing which tier/model a stage uses is an {@code
 *                         application.yml} edit, never a service code change
 * @param temperature      sampling temperature, or {@code null} to use the SDK default
 * @param maxOutputTokens  output token cap, or {@code null} to use the SDK default
 * @param responseMimeType response MIME type requested, e.g. {@code "application/json"}
 */
public record PromptDefinition(
        String name,
        String version,
        String purpose,
        String modelId,
        Double temperature,
        Integer maxOutputTokens,
        String responseMimeType
) {
}
