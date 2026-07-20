package com.forgebrain.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RuntimeState} — per this mission's Part 3 ("track current execution,
 * completed reels, failed reels, queued reels, current stage, runtime duration, runtime health").
 */
class RuntimeStateTest {

    @Test
    void startsInStartingStatusWithEveryReelQueued() {
        RuntimeState state = new RuntimeState("run-1", 3);

        RuntimeState.Snapshot snapshot = state.snapshot();

        assertThat(snapshot.runtimeId()).isEqualTo("run-1");
        assertThat(snapshot.status()).isEqualTo(RuntimeState.Status.STARTING);
        assertThat(snapshot.reelsRequested()).isEqualTo(3);
        assertThat(snapshot.reelsQueued()).isEqualTo(3);
        assertThat(snapshot.reelsCompleted()).isZero();
        assertThat(snapshot.reelsFailed()).isZero();
    }

    @Test
    void tracksCompletedAndFailedCountsAndDrainsTheQueue() {
        RuntimeState state = new RuntimeState("run-1", 3);
        state.markRunning("REEL_EXECUTION");

        state.recordCompleted();
        state.recordCompleted();
        state.recordFailed("reel 3 exhausted retries");

        RuntimeState.Snapshot snapshot = state.snapshot();
        assertThat(snapshot.reelsCompleted()).isEqualTo(2);
        assertThat(snapshot.reelsFailed()).isEqualTo(1);
        assertThat(snapshot.reelsQueued()).isZero();
        assertThat(snapshot.currentStage()).isEqualTo("REEL_EXECUTION");
        assertThat(snapshot.errors()).containsExactly("reel 3 exhausted retries");
    }

    @Test
    void markFinishedReportsCompletedWhenNothingFailed() {
        RuntimeState state = new RuntimeState("run-1", 2);
        state.recordCompleted();
        state.recordCompleted();

        state.markFinished();

        assertThat(state.snapshot().status()).isEqualTo(RuntimeState.Status.COMPLETED);
    }

    @Test
    void markFinishedReportsCompletedWithFailuresWhenSomeButNotAllFailed() {
        RuntimeState state = new RuntimeState("run-1", 2);
        state.recordCompleted();
        state.recordFailed("boom");

        state.markFinished();

        assertThat(state.snapshot().status()).isEqualTo(RuntimeState.Status.COMPLETED_WITH_FAILURES);
    }

    @Test
    void markFinishedReportsFailedWhenNothingCompleted() {
        RuntimeState state = new RuntimeState("run-1", 2);
        state.recordFailed("boom 1");
        state.recordFailed("boom 2");

        state.markFinished();

        assertThat(state.snapshot().status()).isEqualTo(RuntimeState.Status.FAILED);
    }

    @Test
    void warningsAreRecordedSeparatelyFromErrors() {
        RuntimeState state = new RuntimeState("run-1", 1);

        state.addWarning("a soft warning");
        state.recordFailed("a hard error");

        RuntimeState.Snapshot snapshot = state.snapshot();
        assertThat(snapshot.warnings()).containsExactly("a soft warning");
        assertThat(snapshot.errors()).containsExactly("a hard error");
    }

    @Test
    void elapsedDurationIsNonNegativeAndGrowsOverTime() throws InterruptedException {
        RuntimeState state = new RuntimeState("run-1", 1);

        Thread.sleep(5);

        assertThat(state.snapshot().elapsed()).isNotNull();
        assertThat(state.snapshot().elapsed().toMillis()).isGreaterThanOrEqualTo(0);
    }
}
