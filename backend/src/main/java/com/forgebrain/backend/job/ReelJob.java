package com.forgebrain.backend.job;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A durable, trackable unit of reel-generation work. Distinct from {@link
 * com.forgebrain.backend.models.RenderJob}: that record mirrors {@code
 * renderer/render-schema.json}'s narrower {@code renderJob} contract for the still-unimplemented
 * {@code RendererService} job-lifecycle interface; this one is the actual job record the new job
 * layer (this package) creates, persists, and transitions through {@link ReelJobService} —
 * richer (topic title, output files, warnings, fallback usage, a render checksum) because it's
 * the real thing being built, not a schema placeholder. Immutable, like every other pipeline
 * artifact in this codebase — each lifecycle transition below returns a new snapshot rather than
 * mutating in place, so {@link ReelJobRepository} always stores (and can be tested against) one
 * unambiguous, complete state per update.
 *
 * @param jobId           stable identifier for this job record
 * @param pipelineRunId   correlation id for this specific execution attempt — distinct from
 *                        {@code jobId} so a future retry-with-history feature could give one
 *                        logical job several attempts without inventing a new id scheme; today
 *                        every {@link ReelJobService#submitJob()} call mints both together (see
 *                        that class's javadoc for why "rerun" means "submit a new job" for now)
 * @param outputFiles     category (e.g. {@code "video"}, {@code "thumbnail"}, {@code "report"})
 *                        to stored file reference — a local path today, ready to become a
 *                        {@code gs://} URI once {@link OutputStorage} has a Cloud Storage-backed
 *                        implementation
 * @param fallbackStages  names of stages that used a documented fallback (silent audio, a
 *                        placeholder asset, ...) rather than a real signal, for at-a-glance
 *                        quality triage
 * @param renderChecksum  the rendered video's checksum from {@link
 *                        com.forgebrain.backend.models.VideoPackage#checksum()}, once rendering
 *                        completes; {@code null} before then
 */
public record ReelJob(
        String jobId,
        String pipelineRunId,
        String topicId,
        String topicTitle,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        Status status,
        String outputDirectory,
        Map<String, String> outputFiles,
        String failureReason,
        List<String> warnings,
        List<String> fallbackStages,
        String renderChecksum
) {

    public enum Status {
        QUEUED, RUNNING, VALIDATING, RENDERING, PACKAGING, COMPLETED, FAILED
    }

    public static ReelJob queued(String jobId, String pipelineRunId) {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, null, null, now, null, null, null, Status.QUEUED,
                null, Map.of(), null, List.of(), List.of(), null);
    }

    public ReelJob running() {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, Instant.now(), completedAt,
                duration, Status.RUNNING, outputDirectory, outputFiles, failureReason, warnings, fallbackStages,
                renderChecksum);
    }

    public ReelJob validating() {
        return withStatus(Status.VALIDATING);
    }

    public ReelJob rendering() {
        return withStatus(Status.RENDERING);
    }

    public ReelJob packaging() {
        return withStatus(Status.PACKAGING);
    }

    public ReelJob withTopic(String newTopicId, String newTopicTitle) {
        return new ReelJob(jobId, pipelineRunId, newTopicId, newTopicTitle, createdAt, startedAt, completedAt,
                duration, status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages,
                renderChecksum);
    }

    public ReelJob withOutputDirectory(String newOutputDirectory) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, newOutputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum);
    }

    public ReelJob withOutputFiles(Map<String, String> additionalFiles) {
        Map<String, String> merged = new LinkedHashMap<>(outputFiles);
        merged.putAll(additionalFiles);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, Map.copyOf(merged), failureReason, warnings, fallbackStages, renderChecksum);
    }

    public ReelJob withWarning(String warning) {
        List<String> updated = new ArrayList<>(warnings);
        updated.add(warning);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, List.copyOf(updated), fallbackStages,
                renderChecksum);
    }

    public ReelJob withFallbackStage(String stageName) {
        List<String> updated = new ArrayList<>(fallbackStages);
        updated.add(stageName);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, List.copyOf(updated), renderChecksum);
    }

    public ReelJob withRenderChecksum(String checksum) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, checksum);
    }

    public ReelJob completed() {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, now,
                Duration.between(createdAt, now), Status.COMPLETED, outputDirectory, outputFiles, failureReason,
                warnings, fallbackStages, renderChecksum);
    }

    public ReelJob failed(String reason) {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, now,
                Duration.between(createdAt, now), Status.FAILED, outputDirectory, outputFiles, reason, warnings,
                fallbackStages, renderChecksum);
    }

    private ReelJob withStatus(Status newStatus) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                newStatus, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum);
    }
}
