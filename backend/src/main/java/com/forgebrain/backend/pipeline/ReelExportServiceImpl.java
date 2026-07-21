package com.forgebrain.backend.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
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
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Real {@link ReelExportService} implementation: the full production path from topic selection
 * to a rendered MP4. Composes the existing AI pipeline ({@link PipelineOrchestrator}, unchanged)
 * with the new production stages (voice, subtitles, assets, render plan, render), following the
 * exact same try/catch/finally-report pattern {@link PipelineOrchestratorImpl#runFullPipeline()}
 * already established, so a failure at any stage still produces a written, inspectable {@link
 * ReelExportReport} rather than only a log line.
 */
@Component
public class ReelExportServiceImpl implements ReelExportService {

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
    private final ObjectMapper objectMapper;
    private final ReelExportReportWriter reportWriter;

    public ReelExportServiceImpl(PipelineOrchestrator pipelineOrchestrator, VoiceService voiceService,
            SubtitleService subtitleService, AssetService assetService, RenderPlanBuilder renderPlanBuilder,
            RenderValidator renderValidator, RenderEngine renderEngine, RenderingConfig renderingConfig,
            ObjectMapper objectMapper, ReelExportReportWriter reportWriter) {
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.voiceService = voiceService;
        this.subtitleService = subtitleService;
        this.assetService = assetService;
        this.renderPlanBuilder = renderPlanBuilder;
        this.renderValidator = renderValidator;
        this.renderEngine = renderEngine;
        this.renderingConfig = renderingConfig;
        this.objectMapper = objectMapper;
        this.reportWriter = reportWriter;
    }

    @Override
    public ReelExportResult exportReel() {
        String runId = UUID.randomUUID().toString();
        Instant executionStart = Instant.now();
        List<StageExecutionSummary> stageResults = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String finalStatus = "FAILED";
        String topicId = null;
        RenderValidationResult validation = null;
        VideoPackage videoPackage = null;
        Exception failureException = null;

        try {
            PipelineResult pipelineResult = runStage("AI_PIPELINE", stageResults,
                    () -> pipelineOrchestrator.runFullPipeline(),
                    result -> "topicId=" + result.topicId() + ", storyboard_scenes=" + result.storyboard().sceneCount(),
                    false, "n/a");
            topicId = pipelineResult.topicId();

            VoiceResult voiceResult = runStage("VOICE", stageResults,
                    () -> voiceService.generateVoice(pipelineResult.storyboard(), DEFAULT_VOICE_PROFILE),
                    result -> "voice_version=" + result.voiceVersion() + ", total_actual_duration="
                            + result.totalActualDurationSeconds() + "s",
                    result -> result.voiceVersion().contains("silent-fallback"),
                    result -> result.confidenceNotes().overallConfidence().name());
            if (voiceResult.voiceVersion().contains("silent-fallback")) {
                warnings.add("VOICE: silent placeholder audio track used — no real narration file found.");
            }

            SubtitleResult subtitleResult = runStage("SUBTITLES", stageResults,
                    () -> subtitleService.generateSubtitles(pipelineResult.storyboard(), voiceResult),
                    result -> "format=" + result.format() + ", scenes=" + result.scenes().size(),
                    result -> result.confidenceNotes().overallConfidence() != com.forgebrain.backend.shared.ConfidenceLevel.HIGH,
                    result -> result.confidenceNotes().overallConfidence().name());

            AssetManifest assetManifest = runStage("ASSETS", stageResults,
                    () -> assetService.resolveAssets(pipelineResult.storyboard()),
                    result -> "asset_manifest_version=" + result.assetManifestVersion(),
                    result -> result.assetManifestVersion().contains("placeholder"),
                    result -> result.confidenceNotes().overallConfidence().name());

            RenderPlan renderPlan = runStage("RENDER_PLAN", stageResults,
                    () -> renderPlanBuilder.build(pipelineResult.storyboard(), voiceResult, subtitleResult, assetManifest),
                    result -> "scenes=" + result.scenes().size() + ", total_duration=" + result.totalDurationSeconds() + "s",
                    false, "n/a");

            Instant validationStart = Instant.now();
            validation = renderValidator.validate(renderPlan);
            stageResults.add(new StageExecutionSummary("RENDER_VALIDATION",
                    Duration.between(validationStart, Instant.now()), validation.valid(),
                    "scenes=" + renderPlan.scenes().size(), describeValidation(validation), false,
                    "n/a", validation.valid() ? null : "validation failed"));
            if (!validation.valid()) {
                String message = "RenderPlan for topic '" + topicId + "' failed validation: " + describeValidation(validation);
                errors.add("RENDER_VALIDATION: " + message);
                throw new RenderExecutionException(message);
            }

            videoPackage = runStage("RENDER_EXECUTION", stageResults,
                    () -> renderEngine.render(renderPlan),
                    result -> "video=" + result.videoFileUri() + ", size=" + result.fileSizeBytes() + "b",
                    false, "n/a");

            finalStatus = "SUCCESS";
            return buildResult(runId, topicId, pipelineResult.topicTitle(), executionStart, stageResults,
                    validation, finalStatus, warnings, errors, videoPackage, subtitleResult);
        } catch (Exception e) {
            failureException = e;
            throw e;
        } finally {
            if (failureException != null) {
                writeFailureReport(runId, topicId, executionStart, stageResults, validation, warnings, errors,
                        failureException);
            }
        }
    }

    @FunctionalInterface
    private interface StageAction<T> {
        T run() throws Exception;
    }

    private <T> T runStage(String stageName, List<StageExecutionSummary> stageResults, StageAction<T> action,
            java.util.function.Function<T, String> outputSummary, boolean fallbackUsed, String confidence) {
        return runStage(stageName, stageResults, action, outputSummary, r -> fallbackUsed, r -> confidence);
    }

    private <T> T runStage(String stageName, List<StageExecutionSummary> stageResults, StageAction<T> action,
            java.util.function.Function<T, String> outputSummary,
            java.util.function.Function<T, Boolean> fallbackUsed,
            java.util.function.Function<T, String> confidence) {
        Instant stageStart = Instant.now();
        try {
            T result = action.run();
            stageResults.add(new StageExecutionSummary(stageName, Duration.between(stageStart, Instant.now()), true,
                    "n/a", outputSummary.apply(result), fallbackUsed.apply(result), confidence.apply(result), null));
            return result;
        } catch (Exception e) {
            stageResults.add(new StageExecutionSummary(stageName, Duration.between(stageStart, Instant.now()), false,
                    "n/a", "stage failed", false, "unknown", e.getClass().getSimpleName() + ": " + e.getMessage()));
            throw e instanceof RuntimeException re ? re : new RenderExecutionException(stageName + " failed.", e);
        }
    }

    private ReelExportResult buildResult(String runId, String topicId, String topicTitle, Instant executionStart,
            List<StageExecutionSummary> stageResults, RenderValidationResult validation, String finalStatus,
            List<String> warnings, List<String> errors, VideoPackage videoPackage, SubtitleResult subtitleResult) {
        Path renderDirectory = Path.of(videoPackage.videoFileUri()).getParent();
        String metadataPath = writeMetadata(renderDirectory, videoPackage);
        Path subtitleFile = renderDirectory.resolve("subtitles.ass");

        Map<String, String> outputPaths = new LinkedHashMap<>();
        outputPaths.put("video", videoPackage.videoFileUri());
        if (videoPackage.thumbnailFrameUri() != null) {
            outputPaths.put("thumbnail", videoPackage.thumbnailFrameUri());
        }
        outputPaths.put("subtitles", subtitleFile.toString());
        outputPaths.put("metadata", metadataPath);

        ReelExportReport report = new ReelExportReport(runId, executionStart, Instant.now(),
                Duration.between(executionStart, Instant.now()), topicId, List.copyOf(stageResults),
                validation != null ? describeValidation(validation) : null, finalStatus, List.copyOf(warnings),
                List.copyOf(errors), outputPaths);
        String reportPath = reportWriter.write(report, renderDirectory);
        outputPaths.put("report", reportPath);

        return new ReelExportResult(runId, topicId, topicTitle, renderDirectory.toString(),
                videoPackage.videoFileUri(), metadataPath, subtitleFile.toString(), reportPath, videoPackage, report);
    }

    private void writeFailureReport(String runId, String topicId, Instant executionStart,
            List<StageExecutionSummary> stageResults, RenderValidationResult validation, List<String> warnings,
            List<String> errors, Exception failureException) {
        errors.add(failureException.getClass().getSimpleName() + ": " + failureException.getMessage());
        Path fallbackDirectory = Path.of(renderingConfig.outputDirectory(),
                "failed-" + (topicId != null ? topicId : "unknown") + "-" + System.currentTimeMillis());
        ReelExportReport report = new ReelExportReport(runId, executionStart, Instant.now(),
                Duration.between(executionStart, Instant.now()), topicId, List.copyOf(stageResults),
                validation != null ? describeValidation(validation) : null, "FAILED", List.copyOf(warnings),
                List.copyOf(errors), Map.of());
        try {
            reportWriter.write(report, fallbackDirectory);
        } catch (RuntimeException reportWriteException) {
            failureException.addSuppressed(reportWriteException);
        }
    }

    private String writeMetadata(Path renderDirectory, VideoPackage videoPackage) {
        Path metadataFile = renderDirectory.resolve("metadata.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), videoPackage);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write metadata.json to " + metadataFile, e);
        }
        return metadataFile.toString();
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
