package com.forgebrain.backend.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mutable, in-progress accumulator one {@code ForgeBrainRuntime#run()} call updates as it
 * dispatches reels — the runtime-batch analogue of {@code
 * com.forgebrain.backend.pipeline.PipelineContext} (mutable, in-progress) vs. {@code
 * RuntimeReport} (immutable, finished snapshot), the same split that codebase already establishes
 * for one pipeline run. Every mutator is safe to call from multiple threads at once (backed by
 * atomics/synchronized lists), since {@code forgebrain.runtime.parallelism} may run several reel
 * slots concurrently — {@link #snapshot()} is the point-in-time, immutable view a report or a
 * future status endpoint would read.
 */
public class RuntimeState {

    /** Coarse "runtime health" signal — see {@link #snapshot()}. */
    public enum Status {
        STARTING, RUNNING, COMPLETED, COMPLETED_WITH_FAILURES, FAILED
    }

    private final String runtimeId;
    private final Instant startedAt;
    private final int reelsRequested;
    private volatile Status status = Status.STARTING;
    private volatile String currentStage = "STARTING";
    private final AtomicInteger completedCount = new AtomicInteger();
    private final AtomicInteger failedCount = new AtomicInteger();
    private final AtomicInteger queuedCount;
    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    public RuntimeState(String runtimeId, int reelsRequested) {
        this.runtimeId = runtimeId;
        this.startedAt = Instant.now();
        this.reelsRequested = reelsRequested;
        this.queuedCount = new AtomicInteger(reelsRequested);
    }

    public void markRunning(String stage) {
        this.status = Status.RUNNING;
        this.currentStage = stage;
    }

    public void recordCompleted() {
        completedCount.incrementAndGet();
        queuedCount.decrementAndGet();
    }

    public void recordFailed(String error) {
        failedCount.incrementAndGet();
        queuedCount.decrementAndGet();
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void markFinished() {
        this.currentStage = "FINISHED";
        this.status = failedCount.get() == 0
                ? Status.COMPLETED
                : (completedCount.get() > 0 ? Status.COMPLETED_WITH_FAILURES : Status.FAILED);
    }

    public Snapshot snapshot() {
        return new Snapshot(runtimeId, startedAt, status, currentStage, reelsRequested, completedCount.get(),
                failedCount.get(), Math.max(0, queuedCount.get()), Duration.between(startedAt, Instant.now()),
                List.copyOf(warnings), List.copyOf(errors));
    }

    /** Immutable, point-in-time view of {@link RuntimeState} — see {@link #snapshot()}. */
    public record Snapshot(
            String runtimeId,
            Instant startedAt,
            Status status,
            String currentStage,
            int reelsRequested,
            int reelsCompleted,
            int reelsFailed,
            int reelsQueued,
            Duration elapsed,
            List<String> warnings,
            List<String> errors
    ) {
    }
}
