package com.forgebrain.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.AiGatewayConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RetryExecutor} — per this mission's Part 7 ("retry"). A no-op sleeper is
 * injected so these run instantly while still recording the exact backoff delays that would have
 * been used.
 */
class RetryExecutorTest {

    private final List<Long> sleeps = new ArrayList<>();

    private RetryExecutor executor(int maxRetries, long initialBackoffMillis, double backoffMultiplier) {
        AiGatewayConfig config = new AiGatewayConfig(maxRetries, initialBackoffMillis, backoffMultiplier, 30000,
                true);
        return new RetryExecutor(config, sleeps::add);
    }

    @Test
    void succeedsOnTheFirstAttemptWithoutRetryingOrSleeping() {
        RetryExecutor.Outcome<String> outcome = executor(3, 100, 2.0).execute(() -> "ok", e -> true);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.result()).isEqualTo("ok");
        assertThat(outcome.retries()).isZero();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesARetryableFailureUntilItSucceedsAndUsesExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor.Outcome<String> outcome = executor(3, 100, 2.0).execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw new RuntimeException("transient failure " + calls.get());
            }
            return "ok";
        }, e -> true);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.result()).isEqualTo("ok");
        assertThat(outcome.retries()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(3);
        // Exponential backoff: 100ms before the 2nd attempt, 200ms before the 3rd.
        assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test
    void stopsImmediatelyOnANonRetryableFailureWithoutConsumingAnyRetries() {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor.Outcome<String> outcome = executor(3, 100, 2.0).execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("not retryable");
        }, e -> false);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.failure()).isInstanceOf(IllegalStateException.class);
        assertThat(outcome.retries()).isZero();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void exhaustsConfiguredRetriesThenReportsFailure() {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor.Outcome<String> outcome = executor(2, 50, 2.0).execute(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("always fails");
        }, e -> true);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retries()).isEqualTo(2);
        // maxRetries=2 means 3 total attempts (1 initial + 2 retries).
        assertThat(calls.get()).isEqualTo(3);
        assertThat(sleeps).containsExactly(50L, 100L);
    }

    @Test
    void zeroMaxRetriesMeansExactlyOneAttempt() {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor.Outcome<String> outcome = executor(0, 100, 2.0).execute(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("fails");
        }, e -> true);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retries()).isZero();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }
}
