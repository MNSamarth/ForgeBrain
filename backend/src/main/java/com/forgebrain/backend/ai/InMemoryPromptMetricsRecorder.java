package com.forgebrain.backend.ai;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * The default {@link PromptMetricsRecorder}: in-process counters, one bucket per prompt name,
 * reset on restart. {@link LongAdder} throughout since this is written from every {@link
 * AiGateway#execute} call and read only occasionally (a future metrics/observability endpoint).
 */
@Component
public class InMemoryPromptMetricsRecorder implements PromptMetricsRecorder {

    private final Map<String, Counters> countersByPrompt = new ConcurrentHashMap<>();

    @Override
    public void recordSuccess(String promptName, Duration duration, int retries, long estimatedTokens,
            boolean cacheHit) {
        Counters counters = countersFor(promptName);
        counters.invocations.increment();
        counters.totalRetries.add(retries);
        counters.totalDurationMillis.add(duration.toMillis());
        counters.estimatedTotalTokens.add(estimatedTokens);
        if (cacheHit) {
            counters.cacheHits.increment();
        }
    }

    @Override
    public void recordFailure(String promptName, Duration duration, int retries) {
        Counters counters = countersFor(promptName);
        counters.invocations.increment();
        counters.failures.increment();
        counters.totalRetries.add(retries);
        counters.totalDurationMillis.add(duration.toMillis());
    }

    @Override
    public void recordFallback(String promptName) {
        countersFor(promptName).fallbacks.increment();
    }

    @Override
    public PromptMetrics snapshot(String promptName) {
        return countersFor(promptName).toSnapshot(promptName);
    }

    @Override
    public List<PromptMetrics> snapshotAll() {
        return countersByPrompt.entrySet().stream()
                .map(entry -> entry.getValue().toSnapshot(entry.getKey()))
                .sorted(Comparator.comparing(PromptMetrics::promptName))
                .toList();
    }

    private Counters countersFor(String promptName) {
        return countersByPrompt.computeIfAbsent(promptName, name -> new Counters());
    }

    private static final class Counters {
        private final LongAdder invocations = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder fallbacks = new LongAdder();
        private final LongAdder cacheHits = new LongAdder();
        private final LongAdder totalRetries = new LongAdder();
        private final LongAdder totalDurationMillis = new LongAdder();
        private final LongAdder estimatedTotalTokens = new LongAdder();

        private PromptMetrics toSnapshot(String promptName) {
            long invocationCount = invocations.sum();
            double averageDurationMillis = invocationCount == 0 ? 0.0
                    : (double) totalDurationMillis.sum() / invocationCount;
            return new PromptMetrics(promptName, invocationCount, failures.sum(), fallbacks.sum(), cacheHits.sum(),
                    totalRetries.sum(), averageDurationMillis, estimatedTotalTokens.sum());
        }
    }
}
