package com.forgebrain.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.ai.AiGateway;
import com.forgebrain.backend.ai.PromptMetrics;
import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.job.ReelJobService;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.runtime.ForgeBrainRuntime;
import com.forgebrain.backend.runtime.RuntimeReport;
import com.forgebrain.backend.services.MemoryService;
import com.forgebrain.backend.services.ReelAnalyticsService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The Production Validation Suite's end-to-end proof — per this mission's Part 1. Runs the real
 * {@link ForgeBrainRuntime} (same real Spring context, real curriculum, real {@code ffmpeg} as
 * {@code ForgeBrainRuntimeIntegrationTest}), then feeds every resulting artifact through {@link
 * PipelineInvariants} and {@link ArtifactValidator} and assembles one {@link
 * ProductionReadinessReport}. Deliberately does not re-assert what {@code
 * ForgeBrainRuntimeIntegrationTest}/{@code ReelJobServiceImplTest} already cover field-by-field —
 * this test's job is to prove the *validation infrastructure* holds against a real run, and that
 * every subsystem in the checklist below was genuinely reached, not stubbed.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductionValidationSuiteTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStoragePaths(DynamicPropertyRegistry registry) {
        registry.add("forgebrain.local-storage.memory-state-path",
                () -> new File(tempDir.toFile(), "memory-state.json").getAbsolutePath());
        registry.add("forgebrain.local-storage.pipeline-output-directory",
                () -> new File(tempDir.toFile(), "output").getAbsolutePath());
        registry.add("forgebrain.local-storage.execution-report-directory",
                () -> new File(tempDir.toFile(), "reports").getAbsolutePath());
        registry.add("forgebrain.rendering.output-directory",
                () -> new File(tempDir.toFile(), "renders").getAbsolutePath());
        registry.add("forgebrain.rendering.voiceover-assets-directory",
                () -> new File(tempDir.toFile(), "voiceover").getAbsolutePath());
        registry.add("forgebrain.rendering.assets-directory",
                () -> new File(tempDir.toFile(), "empty-assets").getAbsolutePath());
        registry.add("forgebrain.jobs.jobs-directory", () -> new File(tempDir.toFile(), "jobs").getAbsolutePath());
        registry.add("forgebrain.jobs.output-storage-root",
                () -> new File(tempDir.toFile(), "job-output").getAbsolutePath());
        registry.add("forgebrain.analytics.snapshots-directory",
                () -> new File(tempDir.toFile(), "analytics/snapshots").getAbsolutePath());
        registry.add("forgebrain.analytics.reports-directory",
                () -> new File(tempDir.toFile(), "analytics/reports").getAbsolutePath());
        registry.add("forgebrain.runtime.daily-reel-count", () -> "1");
        registry.add("forgebrain.runtime.max-retries-per-reel", () -> "0");
        registry.add("forgebrain.runtime.parallelism", () -> "1");
        registry.add("forgebrain.runtime.retry-backoff-millis", () -> "0");
    }

    @Autowired
    private ForgeBrainRuntime runtime;
    @Autowired
    private ReelJobService reelJobService;
    @Autowired
    private ReelAnalyticsService reelAnalyticsService;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private AiGateway aiGateway;
    @Autowired
    private ProductionReadinessReportWriter validationReportWriter;
    @Autowired
    private ObjectMapper objectMapper;

    private static boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    void theCompletePipelineExecutesCorrectlyAndEveryInvariantHolds() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        // --- Part 1: run the Runtime through its normal orchestration -----------------------
        RuntimeReport runtimeReport = runtime.run();
        assertThat(runtimeReport).isNotNull();
        assertThat(runtimeReport.reelExecutions()).hasSize(1);

        RuntimeReport.ReelExecutionSummary reel = runtimeReport.reelExecutions().get(0);
        ReelJob job = reelJobService.getJob(reel.jobId()).orElseThrow();
        ReelJobReport jobReport = objectMapper.readValue(Path.of(job.outputFiles().get("report")).toFile(),
                ReelJobReport.class);
        ReelOutcomeSnapshot snapshot = reelAnalyticsService.findAll().stream()
                .filter(s -> s.jobId().equals(job.jobId()))
                .findFirst()
                .orElse(null);
        MemoryState.TopicRecord topicRecordAfter = job.topicId() == null ? null
                : memoryService.getTopicRecord(job.topicId());

        // Topic selection occurred.
        assertThat(job.topicId()).isNotBlank();
        // AI Gateway was genuinely invoked (not bypassed) for every generative stage.
        List<String> promptsInvoked = aiGateway.metricsSnapshot().stream()
                .filter(m -> m.invocations() > 0)
                .map(PromptMetrics::promptName)
                .toList();
        assertThat(promptsInvoked).containsExactlyInAnyOrder("research", "lesson", "content-director", "script");
        // Rendering completed.
        assertThat(jobReport.stageResults()).anyMatch(s -> s.stageName().equals("RENDER_EXECUTION") && s.success());
        // Review executed.
        assertThat(job.reviewVerdict()).isIn("APPROVED", "NEEDS_REVISION", "REJECTED");
        // Publishing was reached, and stayed in dry-run (the committed default).
        assertThat(job.publishingStatus()).isNotBlank();
        if (jobReport.publishingResult() != null) {
            assertThat(jobReport.publishingResult().platformOutcomes()).allMatch(PlatformPublishOutcome::dryRun);
        }
        // Analytics executed.
        assertThat(snapshot).isNotNull();
        // Runtime report was produced.
        assertThat(runtimeReport.runtimeId()).isNotBlank();

        // --- Part 2: pipeline invariants ------------------------------------------------------
        Map<String, List<String>> violationsByCheck = new LinkedHashMap<>();
        violationsByCheck.put("stageRunsAtMostOnce", PipelineInvariants.stageRunsAtMostOnce(jobReport));
        violationsByCheck.put("stageOrderingIsCanonical", PipelineInvariants.stageOrderingIsCanonical(jobReport));
        violationsByCheck.put("requiredArtifactsPresent", PipelineInvariants.requiredArtifactsPresent(job));
        violationsByCheck.put("publishingOnlyAfterApproval", PipelineInvariants.publishingOnlyAfterApproval(jobReport));
        violationsByCheck.put("analyticsCapturedAfterPublishingDecision",
                PipelineInvariants.analyticsCapturedAfterPublishingDecision(job, snapshot));
        if (snapshot != null) {
            violationsByCheck.put("memoryReflectsAnalyticsOutcome",
                    PipelineInvariants.memoryReflectsAnalyticsOutcome(snapshot, topicRecordAfter));
        }

        // --- Part 4: artifact validation -------------------------------------------------------
        violationsByCheck.put("validateRuntimeReport", ArtifactValidator.validateRuntimeReport(runtimeReport));
        violationsByCheck.put("validateRenderReport", ArtifactValidator.validateRenderReport(jobReport));
        if (runtimeReport.analyticsSummary() != null) {
            violationsByCheck.put("validateAnalyticsReport",
                    ArtifactValidator.validateAnalyticsReport(runtimeReport.analyticsSummary()));
        }
        if (jobReport.publishingResult() != null
                && jobReport.publishingResult().status() != PublishingResult.Status.FAILED) {
            violationsByCheck.put("validatePublishingPackage",
                    ArtifactValidator.validatePublishingPackage(jobReport.publishingResult().publishingPackage()));
        }

        violationsByCheck.forEach((check, violations) -> assertThat(violations)
                .describedAs("check '%s'", check)
                .isEmpty());

        ProductionReadinessReport readinessReport = ProductionReadinessReport.of(violationsByCheck);
        String reportPath = validationReportWriter.write(readinessReport);

        assertThat(readinessReport.passed()).isTrue();
        assertThat(Files.isRegularFile(Path.of(reportPath))).isTrue();
    }
}
