package com.forgebrain.backend.job;

import com.forgebrain.backend.models.ReviewResult;
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
 * @param reviewVerdict     {@link ReviewResult.Verdict#name()} from the {@link ReviewResult}
 *                          this job produced (see {@code ReviewerService}), once the {@code
 *                          REVIEWING} stage runs; {@code null} before then. Surfaced as a plain
 *                          {@code String} (rather than the model type) so a job snapshot stays
 *                          self-contained and trivially JSON-serializable at a glance, matching
 *                          {@code failureReason}'s convention.
 * @param recommendedAction {@link ReviewResult.RecommendedAction#name()} from the same review —
 *                          what should happen next (approve, reject, regenerate a section, or
 *                          regenerate fully); {@code null} before the {@code REVIEWING} stage runs
 * @param publishingStatus  {@link com.forgebrain.backend.models.PublishingResult.Status#name()}
 *                          once the {@code PUBLISHING} stage runs (only reached when {@code
 *                          reviewVerdict} is {@code "APPROVED"}); {@code "SKIPPED_NOT_APPROVED"}
 *                          when the review verdict was not {@code APPROVED} and publishing was
 *                          therefore never attempted; {@code null} before the job reaches either
 *                          outcome
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
        String renderChecksum,
        String reviewVerdict,
        String recommendedAction,
        String publishingStatus
) {

    public enum Status {
        QUEUED, RUNNING, VALIDATING, RENDERING, REVIEWING, PACKAGING, PUBLISHING, COMPLETED, FAILED
    }

    public static ReelJob queued(String jobId, String pipelineRunId) {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, null, null, now, null, null, null, Status.QUEUED,
                null, Map.of(), null, List.of(), List.of(), null, null, null, null);
    }

    public ReelJob running() {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, Instant.now(), completedAt,
                duration, Status.RUNNING, outputDirectory, outputFiles, failureReason, warnings, fallbackStages,
                renderChecksum, reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob validating() {
        return withStatus(Status.VALIDATING);
    }

    public ReelJob rendering() {
        return withStatus(Status.RENDERING);
    }

    public ReelJob reviewing() {
        return withStatus(Status.REVIEWING);
    }

    public ReelJob packaging() {
        return withStatus(Status.PACKAGING);
    }

    public ReelJob publishing() {
        return withStatus(Status.PUBLISHING);
    }

    public ReelJob withTopic(String newTopicId, String newTopicTitle) {
        return new ReelJob(jobId, pipelineRunId, newTopicId, newTopicTitle, createdAt, startedAt, completedAt,
                duration, status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages,
                renderChecksum, reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob withOutputDirectory(String newOutputDirectory) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, newOutputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum,
                reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob withOutputFiles(Map<String, String> additionalFiles) {
        Map<String, String> merged = new LinkedHashMap<>(outputFiles);
        merged.putAll(additionalFiles);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, Map.copyOf(merged), failureReason, warnings, fallbackStages, renderChecksum,
                reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob withWarning(String warning) {
        List<String> updated = new ArrayList<>(warnings);
        updated.add(warning);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, List.copyOf(updated), fallbackStages,
                renderChecksum, reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob withFallbackStage(String stageName) {
        List<String> updated = new ArrayList<>(fallbackStages);
        updated.add(stageName);
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, List.copyOf(updated), renderChecksum,
                reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob withRenderChecksum(String checksum) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, checksum,
                reviewVerdict, recommendedAction, publishingStatus);
    }

    /**
     * Records a completed review's verdict and recommended action on the job snapshot — see
     * {@code ReelJobReport.reviewResult()} for the full {@link ReviewResult} this summarizes.
     */
    public ReelJob withReviewResult(ReviewResult reviewResult) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum,
                reviewResult.verdict().name(), reviewResult.recommendedAction().name(), publishingStatus);
    }

    /**
     * Records the {@code PUBLISHING} stage's outcome — either a real {@link
     * com.forgebrain.backend.models.PublishingResult.Status#name()} once {@link
     * com.forgebrain.backend.services.PublishingService} ran, or {@code "SKIPPED_NOT_APPROVED"}
     * when it was never called because {@link #reviewVerdict()} was not {@code "APPROVED"}.
     */
    public ReelJob withPublishingStatus(String newPublishingStatus) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                status, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum,
                reviewVerdict, recommendedAction, newPublishingStatus);
    }

    public ReelJob completed() {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, now,
                Duration.between(createdAt, now), Status.COMPLETED, outputDirectory, outputFiles, failureReason,
                warnings, fallbackStages, renderChecksum, reviewVerdict, recommendedAction, publishingStatus);
    }

    public ReelJob failed(String reason) {
        Instant now = Instant.now();
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, now,
                Duration.between(createdAt, now), Status.FAILED, outputDirectory, outputFiles, reason, warnings,
                fallbackStages, renderChecksum, reviewVerdict, recommendedAction, publishingStatus);
    }

    private ReelJob withStatus(Status newStatus) {
        return new ReelJob(jobId, pipelineRunId, topicId, topicTitle, createdAt, startedAt, completedAt, duration,
                newStatus, outputDirectory, outputFiles, failureReason, warnings, fallbackStages, renderChecksum,
                reviewVerdict, recommendedAction, publishingStatus);
    }
}
