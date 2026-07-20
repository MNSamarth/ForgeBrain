package com.forgebrain.backend.ai;

import com.forgebrain.backend.exceptions.AiGatewayException;
import java.util.List;

/**
 * The single orchestration layer every generative pipeline stage calls through instead of {@link
 * com.forgebrain.backend.vertex.VertexAiClient} directly — prompt execution, retry, timeout,
 * response parsing/validation, model selection, and metrics, all in one place. See
 * backend/README.md's "AI Gateway" section.
 */
public interface AiGateway {

    /**
     * Executes one prompt: resolves its {@link PromptDefinition} from {@link PromptRegistry},
     * serves a cached response if one exists and caching is enabled, otherwise calls {@code
     * VertexAiClient} with a timeout and retries (exponential backoff, non-retryable failures
     * fail immediately), parsing and validating each attempt's response.
     *
     * @throws AiGatewayException if the prompt is not configured/usable at all, or every retry
     *                            attempt failed — see {@link AiGatewayException.Reason}
     */
    <T> AiGatewayResult<T> execute(AiPromptExecution<T> execution);

    /**
     * Called by a stage's own catch block after it decides to fall back to a heuristic path
     * following an {@link AiGatewayException} — kept as an explicit, separate call (rather than
     * inferred from every failure) since not every future caller is guaranteed to fall back on
     * every failure.
     */
    void recordFallbackUsed(String promptName);

    /** Current {@link PromptMetrics} for every prompt name that has been executed at least once. */
    List<PromptMetrics> metricsSnapshot();
}
