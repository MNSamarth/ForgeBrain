package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The full-stack proof for the job layer: a real Spring context, the real curriculum, and a real
 * {@code ffmpeg} binary — submits one job through to {@link ReelJob.Status#COMPLETED}, then
 * submits a second against the same (now in-progress) memory state to prove failure is captured
 * as a {@link ReelJob.Status#FAILED} record (never a thrown exception, never silently dropped),
 * with its own diagnosable {@link ReelJobReport}. Mirrors {@code ReelExportServiceImplTest}'s
 * pattern, extended with this layer's own storage overrides.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReelJobServiceImplTest {

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
        registry.add("forgebrain.jobs.jobs-directory",
                () -> new File(tempDir.toFile(), "jobs").getAbsolutePath());
        registry.add("forgebrain.jobs.output-storage-root",
                () -> new File(tempDir.toFile(), "job-output").getAbsolutePath());
    }

    @Autowired
    private ReelJobService reelJobService;

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
    void submitsARealJobToCompletionThenFailsClearlyAndDiagnosablyOnRerun() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        ReelJob job = reelJobService.submitJob();

        assertThat(job.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(job.jobId()).isNotBlank();
        assertThat(job.pipelineRunId()).isNotBlank();
        assertThat(job.pipelineRunId()).isNotEqualTo(job.jobId());
        // On a fresh memory state, java-what-is-java is the only topic with no prerequisites.
        assertThat(job.topicId()).isEqualTo("java-what-is-java");
        assertThat(job.topicTitle()).isNotBlank();
        assertThat(job.createdAt()).isNotNull();
        assertThat(job.startedAt()).isNotNull();
        assertThat(job.completedAt()).isNotNull();
        assertThat(job.duration()).isNotNull();
        assertThat(job.failureReason()).isNull();
        assertThat(job.renderChecksum()).isNotBlank();

        // No real narration file exists in this test environment, so the documented voice
        // fallback is expected to have been used and flagged, not silently hidden.
        assertThat(job.fallbackStages()).contains("VOICE");
        assertThat(job.warnings()).anyMatch(w -> w.contains("VOICE"));

        assertThat(job.outputDirectory()).isNotBlank();
        assertThat(job.outputFiles()).containsKeys("video", "thumbnail", "subtitles", "metadata", "report");
        job.outputFiles().values().forEach(ref -> assertThat(Files.isRegularFile(Path.of(ref))).isTrue());
        // Every output file is stored under the job-scoped storage root, not the raw render dir.
        job.outputFiles().values().forEach(ref -> assertThat(ref).contains(job.jobId()));

        ReelJobReport report = objectMapper.readValue(Path.of(job.outputFiles().get("report")).toFile(),
                ReelJobReport.class);
        assertThat(report.jobId()).isEqualTo(job.jobId());
        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.errors()).isEmpty();
        assertThat(report.stageResults()).extracting(s -> s.stageName())
                .containsExactly("AI_PIPELINE", "VOICE", "SUBTITLES", "ASSETS", "RENDER_PLAN",
                        "RENDER_VALIDATION", "RENDER_EXECUTION");
        assertThat(report.stageResults()).allMatch(s -> s.success());
        assertThat(report.fallbackStages()).contains("VOICE");
        assertThat(report.packagingSummary()).isNotBlank();

        // The job repository holds the same completed job, retrievable by id and via listJobs().
        ReelJob reloaded = reelJobService.getJob(job.jobId()).orElseThrow();
        assertThat(reloaded.jobId()).isEqualTo(job.jobId());
        assertThat(reloaded.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(reloaded.topicId()).isEqualTo(job.topicId());
        assertThat(reloaded.outputFiles()).isEqualTo(job.outputFiles());
        assertThat(reelJobService.listJobs()).extracting(ReelJob::jobId).contains(job.jobId());

        // A second submission against the same (now in-progress) memory state fails clearly —
        // as a returned FAILED job record, never a thrown exception — and still preserves a
        // readable diagnostic report, matching this mission's "never silently swallow a
        // failure" requirement.
        ReelJob secondJob = reelJobService.submitJob();

        assertThat(secondJob.status()).isEqualTo(ReelJob.Status.FAILED);
        assertThat(secondJob.jobId()).isNotEqualTo(job.jobId());
        assertThat(secondJob.failureReason()).contains("InvalidTopicException");
        assertThat(secondJob.completedAt()).isNotNull();

        assertThat(secondJob.outputFiles()).containsKey("report");
        ReelJobReport failedReport = objectMapper.readValue(Path.of(secondJob.outputFiles().get("report")).toFile(),
                ReelJobReport.class);
        assertThat(failedReport.status()).isEqualTo("FAILED");
        assertThat(failedReport.errors()).isNotEmpty();
        assertThat(failedReport.stageResults()).anyMatch(s -> s.stageName().equals("AI_PIPELINE") && !s.success());

        List<ReelJob> allJobs = reelJobService.listJobs();
        assertThat(allJobs).extracting(ReelJob::jobId).contains(job.jobId(), secondJob.jobId());
    }
}
