package com.forgebrain.backend.ai;

import java.time.Duration;

/**
 * The outcome of one successful {@link AiGateway#execute} call: the parsed, validated content
 * plus provenance a caller previously had to track itself (which model actually produced this —
 * every {@code Vertex*ServiceImpl}'s {@code confidenceNotes} cites this).
 *
 * @param content       the parsed, validated response body
 * @param modelId       which Vertex AI model produced it — from the resolved {@link
 *                      PromptDefinition}, not guessed
 * @param promptName    the prompt name this was executed under
 * @param promptVersion {@link PromptDefinition#version()} at call time
 * @param cacheHit      {@code true} if this came from {@link AiResponseCache} rather than a real
 *                      Vertex AI call
 * @param retries       how many retry attempts were needed before this succeeded (0 = succeeded
 *                      on the first attempt, or came from the cache)
 * @param duration      total wall-clock time this call took, including any retries
 */
public record AiGatewayResult<T>(
        T content,
        String modelId,
        String promptName,
        String promptVersion,
        boolean cacheHit,
        int retries,
        Duration duration
) {
}
