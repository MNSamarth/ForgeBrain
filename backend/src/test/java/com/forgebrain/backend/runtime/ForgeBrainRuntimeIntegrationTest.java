package com.forgebrain.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The full-stack proof for the Runtime — per this mission's Part 8 ("runtime integration tests",
 * "pipeline orchestration tests"): a real Spring context, the real curriculum, and a real {@code
 * ffmpeg} binary, running {@link ForgeBrainRuntimeImpl} exactly as {@code
 * ForgeBrainRuntimeCommandLineRunner} would. Mirrors {@code ReelJobServiceImplTest}'s pattern and
 * storage overrides — one reel (fast, deterministic) is enough to prove every stage in the
 * Runtime's own stated chain (topic selection through publishing, analytics, and memory feedback)
 * really executes end to end through this new coordinator. See {@link ForgeBrainRuntimeImplTest}
 * for the fast, mocked multi-reel/retry/failure-recovery/configuration coverage.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ForgeBrainRuntimeIntegrationTest {

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
    void runsOneReelEndToEndThroughEveryStageAndProducesAReport() {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        RuntimeReport report = runtime.run();

        assertThat(report.runtimeId()).isNotBlank();
        assertThat(report.reelsRequested()).isEqualTo(1);
        assertThat(report.reelsCompleted() + report.reelsFailed()).isEqualTo(1);
        assertThat(report.reelExecutions()).hasSize(1);

        RuntimeReport.ReelExecutionSummary reel = report.reelExecutions().get(0);
        assertThat(reel.jobId()).isNotBlank();
        assertThat(reel.topicId()).isEqualTo("java-what-is-java");
        assertThat(reel.status().name()).isEqualTo("COMPLETED");
        assertThat(reel.reviewVerdict()).isIn("APPROVED", "NEEDS_REVISION", "REJECTED");

        // The full stage chain ran: review verdict is set, and publishing was either attempted
        // (APPROVED) or explicitly skipped (anything else) — never left null.
        assertThat(reel.publishingStatus()).isNotNull();

        // Analytics ran and fed memory back for this topic — the loop the Runtime's own javadoc
        // claims closes automatically.
        assertThat(report.analyticsSummary()).isNotNull();
        assertThat(report.analyticsSummary().totalReelsAnalyzed()).isEqualTo(1);
        assertThat(report.memoryUpdates()).hasSize(1);
        assertThat(report.memoryUpdates().get(0).topicId()).isEqualTo("java-what-is-java");

        assertThat(report.configSnapshot()).isNotNull();
        assertThat(report.configSnapshot().dailyReelCount()).isEqualTo(1);
    }
}
