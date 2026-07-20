package com.forgebrain.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryPromptMetricsRecorder} — per this mission's Part 7 ("metrics").
 */
class InMemoryPromptMetricsRecorderTest {

    @Test
    void aPromptNeverExecutedHasZeroedMetrics() {
        PromptMetrics metrics = new InMemoryPromptMetricsRecorder().snapshot("never-called");

        assertThat(metrics.invocations()).isZero();
        assertThat(metrics.failures()).isZero();
        assertThat(metrics.fallbacks()).isZero();
        assertThat(metrics.cacheHits()).isZero();
        assertThat(metrics.averageDurationMillis()).isZero();
    }

    @Test
    void tracksSuccessesRetriesDurationAndTokensSeparatelyPerPrompt() {
        InMemoryPromptMetricsRecorder recorder = new InMemoryPromptMetricsRecorder();

        recorder.recordSuccess("research", Duration.ofMillis(100), 1, 50, false);
        recorder.recordSuccess("research", Duration.ofMillis(300), 0, 70, false);
        recorder.recordSuccess("lesson", Duration.ofMillis(200), 0, 90, false);

        PromptMetrics research = recorder.snapshot("research");
        assertThat(research.invocations()).isEqualTo(2);
        assertThat(research.totalRetries()).isEqualTo(1);
        assertThat(research.averageDurationMillis()).isEqualTo(200.0);
        assertThat(research.estimatedTotalTokens()).isEqualTo(120);
        assertThat(research.failures()).isZero();

        PromptMetrics lesson = recorder.snapshot("lesson");
        assertThat(lesson.invocations()).isEqualTo(1);
    }

    @Test
    void tracksCacheHitsSeparatelyFromRealCalls() {
        InMemoryPromptMetricsRecorder recorder = new InMemoryPromptMetricsRecorder();

        recorder.recordSuccess("research", Duration.ofMillis(5), 0, 10, true);
        recorder.recordSuccess("research", Duration.ofMillis(300), 0, 60, false);

        PromptMetrics metrics = recorder.snapshot("research");
        assertThat(metrics.invocations()).isEqualTo(2);
        assertThat(metrics.cacheHits()).isEqualTo(1);
    }

    @Test
    void tracksFailuresAndFallbacksSeparately() {
        InMemoryPromptMetricsRecorder recorder = new InMemoryPromptMetricsRecorder();

        recorder.recordFailure("script", Duration.ofMillis(500), 2);
        recorder.recordFallback("script");

        PromptMetrics metrics = recorder.snapshot("script");
        assertThat(metrics.invocations()).isEqualTo(1);
        assertThat(metrics.failures()).isEqualTo(1);
        assertThat(metrics.fallbacks()).isEqualTo(1);
        assertThat(metrics.totalRetries()).isEqualTo(2);
    }

    @Test
    void snapshotAllReturnsEveryPromptEverRecordedSortedByName() {
        InMemoryPromptMetricsRecorder recorder = new InMemoryPromptMetricsRecorder();

        recorder.recordSuccess("script", Duration.ofMillis(1), 0, 1, false);
        recorder.recordSuccess("content-director", Duration.ofMillis(1), 0, 1, false);
        recorder.recordSuccess("lesson", Duration.ofMillis(1), 0, 1, false);

        assertThat(recorder.snapshotAll()).extracting(PromptMetrics::promptName)
                .containsExactly("content-director", "lesson", "script");
    }
}
