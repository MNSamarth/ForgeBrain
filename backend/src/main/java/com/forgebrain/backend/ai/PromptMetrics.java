package com.forgebrain.backend.ai;

/**
 * Point-in-time counters for one prompt name, as tracked by {@link PromptMetricsRecorder}.
 *
 * @param invocations           total {@link AiGateway#execute} calls for this prompt, successful
 *                               or not
 * @param failures               calls that exhausted every retry (or failed non-retryably) and
 *                               threw {@code AiGatewayException}
 * @param fallbacks              how many times a caller reported falling back to its heuristic
 *                               path after a failure — see {@link AiGateway#recordFallbackUsed}
 * @param cacheHits              calls served from {@link AiResponseCache} without a real Vertex
 *                               AI call
 * @param totalRetries           sum of retry attempts consumed across every call (successful or
 *                               not)
 * @param averageDurationMillis  mean wall-clock duration per call, in milliseconds
 * @param estimatedTotalTokens   sum of a rough {@code (prompt + response) / 4} character-based
 *                               token estimate — a documented proxy, not a real tokenizer count
 */
public record PromptMetrics(
        String promptName,
        long invocations,
        long failures,
        long fallbacks,
        long cacheHits,
        long totalRetries,
        double averageDurationMillis,
        long estimatedTotalTokens
) {
}
