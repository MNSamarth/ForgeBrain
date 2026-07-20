package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for {@code com.forgebrain.backend.runtime.ForgeBrainRuntime}, the single coordinator
 * that drives {@code ReelJobService#submitJob()} across a whole batch. Bound from {@code
 * forgebrain.runtime.*}; {@code forgebrain.runtime.run-on-startup} is read directly by {@code
 * ForgeBrainRuntimeCommandLineRunner}'s {@code @ConditionalOnProperty}, not a field here — same
 * convention as {@code forgebrain.jobs.run-on-startup}/{@code forgebrain.pipeline.run-on-startup}.
 *
 * <p>Deliberately does <b>not</b> duplicate settings that already have a dedicated home: review
 * threshold lives in {@link ReviewerConfig#approvalThreshold()}, publish mode/dry-run in {@link
 * PlatformUploadConfig}, Vertex model selection in {@link VertexAiConfig}, and storage mode in
 * {@link CloudStorageConfig#enabled()}. The Runtime reads all four directly and echoes their
 * current values into every {@code RuntimeReport} for visibility — see {@code
 * RuntimeReport.RuntimeConfigSnapshot} — rather than owning a second, driftable copy.
 *
 * @param dailyReelCount     how many reels one {@code ForgeBrainRuntime#run()} call attempts
 * @param parallelism        how many reel jobs may run concurrently (1 = strictly sequential, the
 *                            committed default). Values above 1 are honored but experimental: the
 *                            file-based {@code MemoryService}/topic-selection path was not
 *                            designed for concurrent writers within one process, so two reels
 *                            could race to select the same topic — see backend/README.md's
 *                            "Runtime" section
 * @param maxRetriesPerReel  how many additional {@code submitJob()} attempts a single reel slot
 *                            gets after an initial failure before that slot is recorded as failed
 *                            and the runtime moves on — {@code 0} disables per-reel retry
 * @param retryBackoffMillis delay before retrying a failed reel slot
 * @param runtimeMode        a label describing how this run was triggered (e.g. {@code "manual"},
 *                            {@code "scheduled"}) — informational only, echoed into the report;
 *                            does not change execution behavior in this phase
 */
@ConfigurationProperties(prefix = "forgebrain.runtime")
public record RuntimeConfig(
        int dailyReelCount,
        int parallelism,
        int maxRetriesPerReel,
        long retryBackoffMillis,
        String runtimeMode
) {
}
