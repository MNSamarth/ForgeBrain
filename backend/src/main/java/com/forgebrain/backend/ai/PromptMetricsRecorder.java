package com.forgebrain.backend.ai;

import java.time.Duration;
import java.util.List;

/**
 * Tracks {@link PromptMetrics} per prompt name. {@link AiGatewayImpl} calls {@link
 * #recordSuccess}/{@link #recordFailure} once per {@link AiGateway#execute} call; a caller that
 * falls back after a failure calls {@link AiGateway#recordFallbackUsed}, which delegates to
 * {@link #recordFallback} here.
 */
public interface PromptMetricsRecorder {

    void recordSuccess(String promptName, Duration duration, int retries, long estimatedTokens, boolean cacheHit);

    void recordFailure(String promptName, Duration duration, int retries);

    void recordFallback(String promptName);

    PromptMetrics snapshot(String promptName);

    List<PromptMetrics> snapshotAll();
}
