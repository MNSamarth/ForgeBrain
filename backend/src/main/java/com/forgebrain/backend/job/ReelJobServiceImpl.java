package com.forgebrain.backend.job;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.pipeline.PipelineOrchestrator;
import com.forgebrain.backend.pipeline.PipelineResult;
import com.forgebrain.backend.pipeline.StageExecutionSummary;
import com.forgebrain.backend.rendering.RenderEngine;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.RenderPlanBuilder;
import com.forgebrain.backend.rendering.RenderValidationResult;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue.Severity;
import com.forgebrain.backend.rendering.RenderValidator;
import com.forgebrain.backend.services.AssetService;
import com.forgebrain.backend.services.SubtitleService;
import com.forgebrain.backend.services.VoiceService;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link ReelJobService} implementation. Composes the same stage services {@code
 * ReelExportServiceImpl} uses (unmodified) — {@link PipelineOrchestrator}, {@link VoiceService},
 * {@link SubtitleService}, {@link AssetService}, {@link RenderPlanBuilder}, {@link
 * RenderValidator}, {@link RenderEngine} — around explicit {@link ReelJob} lifecycle transitions,
 * persisting the job after every meaningful milestone so a crash mid-run still leaves the latest
 * real progress durably recorded, not just a log line.
 */
@Component
public class ReelJobServiceImpl implements ReelJobService {

    private static final Logger log = LoggerFactory.getLogger(ReelJobServiceImpl.class);
    private static final VoiceResult.VoiceProfile DEFAULT_VOICE_PROFILE =
            new VoiceResult.VoiceProfile("en-US-Neural2-C", "en-US", 1.0, 0.0);

    private final PipelineOrchestrator pipelineOrchestrator;
    private final VoiceService voiceService;
    private final SubtitleService subtitleService;
    private final AssetService assetService;
    private final RenderPlanBuilder renderPlanBuilder;
    private final RenderValidator renderValidator;
    private final RenderEngine renderEngine;
    private final RenderingConfig renderingConfig;
    private final OutputPackagingService outputPackagingService;
    private final ReelJobReportWriter reportWriter;
    private final ReelJobRepository jobRepository;

    public ReelJobServiceImpl(PipelineOrchestrator pipelineOrchestrator, VoiceService voiceService,
            SubtitleService subtitleService, AssetService assetService, RenderPlanBuilder renderPlanBuilder,
            RenderValidator renderValidator, RenderEngine renderEngine, RenderingConfig renderingConfig,
            OutputPackagingService outputPackagingService, ReelJobReportWriter reportWriter,
            ReelJobRepository jobRepository) {
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.voiceService = voiceService;
        this.subtitleService = subtitleService;
        this.assetService = assetService;
        this.renderPlanBuilder = renderPlanBuilder;
        this.renderValidator = renderValidator;
        this.renderEngine = renderEngine;
        this.renderingConfig = renderingConfig;
        this.outputPackagingService = outputPackagingService;
        this.reportWriter = reportWriter;
        this.jobRepository = jobRepository;
    }

    @Override
    public ReelJob submitJob() {
        String jobId = UUID.randomUUID().toString();
        String pipelineRunId = UUID.randomUUID().toString();
        Instant executionStart = Instant.now();
        List<StageExecutionSummary> stageResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ReelJob job = ReelJob.queued(jobId, pipelineRunId);
        job = jobRepository.create(job);
        job = jobRepository.update(job.running());

        RenderValidationResult validation = null;
        try {
            PipelineResult pipelineResult = runStage("AI_PIPELINE", stageResults,
                    () -> pipelineOrchestrator.runFullPipeline(),
                    r -> "topicId=" + r.topicId() + ", storyboard_scenes=" + r.storyboard().sceneCount());
            job = jobRepository.update(job.withTopic(pipelineResult.topicId(), pipelineResult.topicTitle()));

            VoiceResult voiceResult = runStage("VOICE", stageResults,
                    () -> voiceService.generateVoice(pipelineResult.storyboard(), DEFAULT_VOICE_PROFILE),
                    r -> "voice_version=" + r.voiceVersion());
            if (voiceResult.voiceVersion().contains("silent-fallback")) {
                job = jobRepository.update(job
                        .withWarning("VOICE: silent placeholder audio track used — no real narration file found.")
                        .withFallbackStage("VOICE"));
            }

            SubtitleResult subtitleResult = runStage("SUBTITLES", stageResults,
                    () -> subtitleService.generateSubtitles(pipelineResult.storyboard(), voiceResult),
                    r -> "format=" + r.format() + ", scenes=" + r.scenes().size());

            AssetManifest assetManifest = runStage("ASSETS", stageResults,
                    () -> assetService.resolveAssets(pipelineResult.storyboard()),
                    r -> "asset_manifest_version=" + r.assetManifestVersion());
            if (assetManifest.assetManifestVersion().contains("placeholder")) {
                job = jobRepository.update(job
                        .withWarning("ASSETS: no real asset catalog found; deterministic placeholder references used.")
                        .withFallbackStage("ASSETS"));
            }

            RenderPlan renderPlan = runStage("RENDER_PLAN", stageResults,
                    () -> renderPlanBuilder.build(pipelineResult.storyboard(), voiceResult, subtitleResult, assetManifest),
                    r -> "scenes=" + r.scenes().size() + ", total_duration=" + r.totalDurationSeconds() + "s");

            job = jobRepository.update(job.validating());
            Instant validationStart = Instant.now();
            validation = renderValidator.validate(renderPlan);
            stageResults.add(new StageExecutionSummary("RENDER_VALIDATION",
                    Duration.between(validationStart, Instant.now()), validation.valid(),
                    "scenes=" + renderPlan.scenes().size(), describeValidation(validation), false, "n/a",
                    validation.valid() ? null : "validation failed"));
            if (!validation.valid()) {
                String message = "RenderPlan failed validation: " + describeValidation(validation);
                errors.add("RENDER_VALIDATION: " + message);
                throw new RenderExecutionException(message);
            }

            job = jobRepository.update(job.rendering());
            VideoPackage videoPackage = runStage("RENDER_EXECUTION", stageResults,
                    () -> renderEngine.render(renderPlan),
                    r -> "video=" + r.videoFileUri() + ", size=" + r.fileSizeBytes() + "b");
            job = jobRepository.update(job.withRenderChecksum(videoPackage.checksum()));

            job = jobRepository.update(job.packaging());
            Path renderDirectory = Path.of(videoPackage.videoFileUri()).getParent();
            job = jobRepository.update(job.withOutputDirectory(renderDirectory.toString()));
            Path subtitleFile = renderDirectory.resolve("subtitles.srt");
            ReelOutputPackage outputPackage;
            try {
                outputPackage = outputPackagingService.packageOutputs(jobId, renderDirectory, videoPackage, subtitleFile);
                job = jobRepository.update(job.withOutputFiles(outputPackage.files()));
            } catch (RuntimeException packagingException) {
                // Partial output status is preserved even if packaging itself fails partway —
                // whatever job.withOutputFiles(...) already recorded above stays on the job.
                // The outer catch below records this failure in `errors`; not duplicated here.
                throw packagingException;
            }

            job = jobRepository.update(job.completed());

            ReelJobReport report = buildReport(jobId, pipelineRunId, pipelineResult.topicId(), executionStart,
                    stageResults, validation, "COMPLETED", job.warnings(), errors, job.fallbackStages(),
                    job.outputFiles(), "packaged and stored " + outputPackage.files().size() + " file(s): "
                            + String.join(", ", outputPackage.files().keySet()));
            String reportPath = reportWriter.write(report, renderDirectory);
            String reportRef = outputPackagingService.storeReport(jobId, Path.of(reportPath));
            job = jobRepository.update(job.withOutputFiles(Map.of("report", reportRef)));

            return job;
        } catch (Exception e) {
            errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            ReelJob failedJob = job.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            job = jobRepository.update(failedJob);

            Path reportDirectory = job.outputDirectory() != null
                    ? Path.of(job.outputDirectory())
                    : Path.of(renderingConfig.outputDirectory(), "failed-job-" + jobId);
            ReelJobReport report = buildReport(jobId, pipelineRunId, job.topicId(), executionStart, stageResults,
                    validation, "FAILED", job.warnings(), errors, job.fallbackStages(), job.outputFiles(),
                    "packaging did not complete");
            try {
                String reportPath = reportWriter.write(report, reportDirectory);
                job = jobRepository.update(job.withOutputFiles(Map.of("report", reportPath)));
            } catch (RuntimeException reportWriteException) {
                log.error("Failed to write failure report for job '{}'; the job's FAILED status was still"
                        + " persisted.", jobId, reportWriteException);
            }

            log.error("Reel job '{}' failed: {}", jobId, e.getMessage(), e);
            return job;
        }
    }

    @Override
    public Optional<ReelJob> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    public List<ReelJob> listJobs() {
        return jobRepository.findAll();
    }

    @FunctionalInterface
    private interface StageAction<T> {
        T run() throws Exception;
    }

    private <T> T runStage(String stageName, List<StageExecutionSummary> stageResults, StageAction<T> action,
            Function<T, String> outputSummary) {
        Instant stageStart = Instant.now();
        try {
            T result = action.run();
            stageResults.add(new StageExecutionSummary(stageName, Duration.between(stageStart, Instant.now()), true,
                    "n/a", outputSummary.apply(result), false, "n/a", null));
            return result;
        } catch (Exception e) {
            stageResults.add(new StageExecutionSummary(stageName, Duration.between(stageStart, Instant.now()), false,
                    "n/a", "stage failed", false, "unknown", e.getClass().getSimpleName() + ": " + e.getMessage()));
            throw e instanceof RuntimeException re ? re : new RenderExecutionException(stageName + " failed.", e);
        }
    }

    private ReelJobReport buildReport(String jobId, String pipelineRunId, String topicId, Instant executionStart,
            List<StageExecutionSummary> stageResults, RenderValidationResult validation, String status,
            List<String> warnings, List<String> errors, List<String> fallbackStages,
            Map<String, String> outputPaths, String packagingSummary) {
        Instant executionEnd = Instant.now();
        return new ReelJobReport(jobId, pipelineRunId, topicId, executionStart, executionEnd,
                Duration.between(executionStart, executionEnd), List.copyOf(stageResults), status,
                Map.copyOf(outputPaths), List.copyOf(warnings), List.copyOf(errors),
                List.copyOf(fallbackStages), validation != null ? describeValidation(validation) : null,
                packagingSummary);
    }

    private static String describeValidation(RenderValidationResult validation) {
        if (validation.issues().isEmpty()) {
            return "valid, no issues";
        }
        long errorCount = validation.issues().stream().filter(i -> i.severity() == Severity.ERROR).count();
        long warningCount = validation.issues().size() - errorCount;
        String detail = validation.issues().stream()
                .map(ValidationIssue::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
        return (validation.valid() ? "valid" : "invalid") + " — " + errorCount + " error(s), " + warningCount
                + " warning(s): " + detail;
    }
}
