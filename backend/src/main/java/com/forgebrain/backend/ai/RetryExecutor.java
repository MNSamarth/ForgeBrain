package com.forgebrain.backend.ai;

import com.forgebrain.backend.config.AiGatewayConfig;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Runs an action with exponential-backoff retry, per {@link AiGatewayConfig}. Plain class, not a
 * Spring bean — owned directly by {@link AiGatewayImpl}, the same "pure logic, directly
 * instantiated by its orchestrator" convention already established by {@code QualityScorer} and
 * {@code PublishingMetadataGenerator}. Package-private: this is an implementation detail of
 * {@link AiGatewayImpl}, not part of the gateway's public seam.
 */
class RetryExecutor {

    private final AiGatewayConfig config;
    private final LongConsumer sleeper;

    RetryExecutor(AiGatewayConfig config) {
        this(config, RetryExecutor::sleep);
    }

    /** Package-private constructor so tests can inject a no-op sleeper and run instantly. */
    RetryExecutor(AiGatewayConfig config, LongConsumer sleeper) {
        this.config = config;
        this.sleeper = sleeper;
    }

    /**
     * @param action    the operation to attempt, up to {@code config.maxRetries() + 1} times total
     * @param retryable decides whether a given failure is worth retrying at all — a non-retryable
     *                  failure stops immediately, consuming no further attempts
     */
    <T> Outcome<T> execute(Callable<T> action, Predicate<Exception> retryable) {
        int maxAttempts = config.maxRetries() + 1;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return Outcome.success(action.call(), attempt - 1);
            } catch (Exception e) {
                lastFailure = e;
                if (!retryable.test(e) || attempt >= maxAttempts) {
                    return Outcome.failure(e, attempt - 1);
                }
                sleeper.accept(backoffMillis(attempt));
            }
        }
        // Unreachable: the loop above always returns, but the compiler can't see that.
        return Outcome.failure(lastFailure, maxAttempts - 1);
    }

    private long backoffMillis(int attempt) {
        return Math.round(config.initialBackoffMillis() * Math.pow(config.backoffMultiplier(), attempt - 1));
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param success {@code true} if {@code action} eventually returned a value
     * @param result  the returned value, or {@code null} on failure
     * @param failure the last exception seen, or {@code null} on success
     * @param retries how many retries were consumed (0 = succeeded/failed on the first attempt)
     */
    record Outcome<T>(boolean success, T result, Exception failure, int retries) {
        static <T> Outcome<T> success(T result, int retries) {
            return new Outcome<>(true, result, null, retries);
        }

        static <T> Outcome<T> failure(Exception failure, int retries) {
            return new Outcome<>(false, null, failure, retries);
        }
    }
}
