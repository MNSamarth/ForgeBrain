package com.forgebrain.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.exceptions.InvalidTopicException;
import com.forgebrain.backend.models.VideoPackage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The full-stack proof for this mission: a real Spring context wiring every new production
 * stage (voice, subtitles, assets, render plan, render validation, render execution) around the
 * existing AI pipeline, run against the real curriculum with the real {@code ffmpeg} binary —
 * not mocks. Mirrors {@link PipelineOrchestratorImplTest}'s pattern exactly, extended with the
 * rendering-specific storage overrides this stage needs.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReelExportServiceImplTest {

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
    }

    @Autowired
    private ReelExportService reelExportService;

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
    void exportsARealReelEndToEndThenFailsClearlyOnASecondRunAndStillWritesADiagnosticReport() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        ReelExportResult result = reelExportService.exportReel();

        // On a fresh memory state, java-what-is-java is the only topic with no prerequisites —
        // same starting point PipelineOrchestratorImplTest documents.
        assertThat(result.topicId()).isEqualTo("java-what-is-java");
        assertThat(result.runId()).isNotBlank();

        Path videoFile = Path.of(result.videoFilePath());
        assertThat(Files.isRegularFile(videoFile)).isTrue();
        assertThat(Files.size(videoFile)).isGreaterThan(0);

        Path metadataFile = Path.of(result.metadataFilePath());
        assertThat(Files.isRegularFile(metadataFile)).isTrue();
        VideoPackage metadata = objectMapper.readValue(metadataFile.toFile(), VideoPackage.class);
        assertThat(metadata.topicId()).isEqualTo(result.topicId());
        assertThat(metadata.videoFileUri()).isEqualTo(result.videoPackage().videoFileUri());

        Path subtitleFile = Path.of(result.subtitleFilePath());
        assertThat(Files.isRegularFile(subtitleFile)).isTrue();
        // Subtitles burn in from .ass now (word-highlight emphasis needs its inline color
        // override tags), not .srt — see RenderCommandBuilder / AssSubtitleWriter.
        assertThat(Files.readString(subtitleFile)).contains("Dialogue:");

        Path reportFile = Path.of(result.reportFilePath());
        assertThat(Files.isRegularFile(reportFile)).isTrue();
        ReelExportReport report = objectMapper.readValue(reportFile.toFile(), ReelExportReport.class);
        assertThat(report.finalStatus()).isEqualTo("SUCCESS");
        assertThat(report.errors()).isEmpty();
        assertThat(report.stageResults()).extracting(StageExecutionSummary::stageName)
                .containsExactly("AI_PIPELINE", "VOICE", "SUBTITLES", "ASSETS", "RENDER_PLAN",
                        "RENDER_VALIDATION", "RENDER_EXECUTION");
        assertThat(report.stageResults()).allMatch(StageExecutionSummary::success);
        // No real narration file exists in this test environment, so the documented silent
        // fallback is expected to have been used and flagged as a warning, not silently hidden.
        assertThat(report.warnings()).anyMatch(w -> w.contains("VOICE"));

        // A second run against the same (now in-progress) memory state fails clearly rather than
        // silently reselecting or fabricating progress — same real, systemic failure
        // PipelineOrchestratorImplTest documents, now proven to also produce a diagnosable
        // ReelExportReport rather than only a log line.
        assertThrows(InvalidTopicException.class, reelExportService::exportReel);

        File rendersDir = new File(tempDir.toFile(), "renders");
        File failedRunDirectory = Arrays.stream(rendersDir.listFiles())
                .filter(f -> f.isDirectory() && f.getName().startsWith("failed-"))
                .max(Comparator.comparing(File::getName))
                .orElseThrow(() -> new AssertionError("Expected a failed-run report directory under " + rendersDir));
        File failedReportFile = new File(failedRunDirectory, "report.json");
        assertThat(failedReportFile).exists();
        ReelExportReport failedReport = objectMapper.readValue(failedReportFile, ReelExportReport.class);
        assertThat(failedReport.finalStatus()).isEqualTo("FAILED");
        assertThat(failedReport.errors()).isNotEmpty();
        assertThat(failedReport.stageResults()).anyMatch(
                s -> s.stageName().equals("AI_PIPELINE") && !s.success());
    }
}
