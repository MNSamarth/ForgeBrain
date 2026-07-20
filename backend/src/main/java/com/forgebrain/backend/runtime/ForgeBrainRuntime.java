package com.forgebrain.backend.runtime;

/**
 * The single coordinator for autonomous ForgeBrain production — the "operating system" every
 * other entry point (a CLI runner, a future scheduled trigger) drives instead of calling {@code
 * ReelJobService} directly. One {@link #run()} call executes {@code
 * RuntimeConfig#dailyReelCount()} reels end to end (topic selection through publishing and the
 * analytics/memory feedback loop — see backend/README.md's "Runtime" section for the exact
 * stage list, every one of them an existing, unmodified service), tolerating individual reel
 * failures without stopping the batch, and returns one {@link RuntimeReport} covering the whole
 * run.
 */
public interface ForgeBrainRuntime {

    RuntimeReport run();
}
