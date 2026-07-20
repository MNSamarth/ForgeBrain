package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for {@link com.forgebrain.backend.ai.AiGateway}. Bound from {@code
 * forgebrain.ai-gateway.*}. Per-prompt model selection stays in {@link VertexAiConfig} (unchanged)
 * — this record only controls how the gateway calls whichever model a {@code PromptDefinition}
 * names.
 *
 * @param maxRetries           how many times a failed prompt is retried after the first attempt
 *                             (0 disables retries) — see {@code RetryExecutor}. Never applies to a
 *                             missing/blank project id or model id, which fails immediately.
 * @param initialBackoffMillis delay before the first retry
 * @param backoffMultiplier    multiplier applied to the delay after each subsequent retry
 *                             (exponential backoff)
 * @param timeoutMillis        maximum time to wait for one Vertex AI call before treating it as a
 *                             (retryable) failure
 * @param cacheEnabled         whether an exact-duplicate request reuses a previously cached raw
 *                             response instead of calling Vertex AI again — see {@code
 *                             AiResponseCache}
 */
@ConfigurationProperties(prefix = "forgebrain.ai-gateway")
public record AiGatewayConfig(
        int maxRetries,
        long initialBackoffMillis,
        double backoffMultiplier,
        long timeoutMillis,
        boolean cacheEnabled
) {
}
