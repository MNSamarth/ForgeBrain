package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.services.MemoryService;
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
 * submits a second job and checks its outcome against whatever the first job's analytics
 * feedback just wrote into memory (see {@code ReelAnalyticsServiceImpl}): a genuinely advanced
 * curriculum (first topic published) legitimately lets the second job pick the next topic,
 * while anything else still blocks the same topic and fails cleanly as a {@link
 * ReelJob.Status#FAILED} record (never a thrown exception, never silently dropped), with its own
 * diagnosable {@link ReelJobReport}. Mirrors {@code ReelExportServiceImplTest}'s pattern,
 * extended with this layer's own storage overrides.
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
        registry.add("forgebrain.analytics.snapshots-directory",
                () -> new File(tempDir.toFile(), "analytics/snapshots").getAbsolutePath());
        registry.add("forgebrain.analytics.reports-directory",
                () -> new File(tempDir.toFile(), "analytics/reports").getAbsolutePath());
    }

    @Autowired
    private ReelJobService reelJobService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemoryService memoryService;

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

        // The Reviewer stage always runs after render, regardless of what it decides — the job
        // still reaches COMPLETED either way (see ReelJobServiceImpl: a review verdict is a
        // content decision, not a job-execution failure).
        assertThat(job.reviewVerdict()).isIn("APPROVED", "NEEDS_REVISION", "REJECTED");
        assertThat(job.recommendedAction()).isIn("APPROVE", "REJECT", "REGENERATE_SECTION", "REGENERATE_FULL");

        // Publishing only runs for an APPROVED review — everything else is recorded as skipped,
        // never a job failure (see ReelJobServiceImpl and this mission's Part 5).
        boolean approved = job.reviewVerdict().equals("APPROVED");
        if (approved) {
            assertThat(job.publishingStatus()).isIn("READY", "PARTIAL_FAILURE", "FAILED");
        } else {
            assertThat(job.publishingStatus()).isEqualTo("SKIPPED_NOT_APPROVED");
            assertThat(job.warnings()).anyMatch(w -> w.contains("PUBLISHING") && w.contains("skipped"));
        }

        ReelJobReport report = objectMapper.readValue(Path.of(job.outputFiles().get("report")).toFile(),
                ReelJobReport.class);
        assertThat(report.jobId()).isEqualTo(job.jobId());
        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.errors()).isEmpty();
        List<String> expectedStages = new java.util.ArrayList<>(List.of("AI_PIPELINE", "VOICE", "SUBTITLES",
                "ASSETS", "RENDER_PLAN", "RENDER_VALIDATION", "RENDER_EXECUTION", "REVIEWING"));
        if (approved) {
            expectedStages.add("PUBLISHING");
        }
        assertThat(report.stageResults()).extracting(s -> s.stageName())
                .containsExactlyElementsOf(expectedStages);
        assertThat(report.stageResults()).allMatch(s -> s.success());
        assertThat(report.fallbackStages()).contains("VOICE");
        assertThat(report.packagingSummary()).isNotBlank();

        assertThat(report.reviewResult()).isNotNull();
        assertThat(report.reviewResult().jobId()).isEqualTo(job.jobId());
        assertThat(report.reviewResult().verdict().name()).isEqualTo(job.reviewVerdict());
        assertThat(report.reviewResult().recommendedAction().name()).isEqualTo(job.recommendedAction());
        assertThat(report.reviewResult().score()).isBetween(0.0, 1.0);
        assertThat(report.reviewResult().categoryScores()).hasSize(9);

        if (approved) {
            assertThat(report.publishingResult()).isNotNull();
            assertThat(report.publishingResult().jobId()).isEqualTo(job.jobId());
            assertThat(report.publishingResult().status().name()).isEqualTo(job.publishingStatus());
            assertThat(report.publishingResult().publishingPackage().reviewVerdict()).isEqualTo("APPROVED");
            assertThat(report.publishingResult().platformOutcomes()).hasSize(2);
            assertThat(job.outputFiles()).containsKey("publishing_package");
            job.outputFiles().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("publishing_"))
                    .forEach(entry -> assertThat(Files.isRegularFile(Path.of(entry.getValue()))).isTrue());
        } else {
            assertThat(report.publishingResult()).isNull();
        }

        // Analytics captured a durable snapshot for this job and fed its outcome back into
        // memory — the same topic record TopicSelectorImpl reads on the next run (see this
        // mission's Part 4/6).
        File analyticsSnapshot = new File(new File(tempDir.toFile(), "analytics/snapshots"), job.jobId() + ".json");
        assertThat(analyticsSnapshot).isFile();
        MemoryState.TopicRecord topicRecord = memoryService.getTopicRecord(job.topicId());
        assertThat(topicRecord).isNotNull();
        assertThat(topicRecord.performanceScore()).isNotNull();
        assertThat(topicRecord.notes()).contains("Analytics:");
        if ("READY".equals(job.publishingStatus())) {
            assertThat(topicRecord.status()).isEqualTo(com.forgebrain.backend.models.Topic.Status.POSTED);
        }

        // The job repository holds the same completed job, retrievable by id and via listJobs().
        ReelJob reloaded = reelJobService.getJob(job.jobId()).orElseThrow();
        assertThat(reloaded.jobId()).isEqualTo(job.jobId());
        assertThat(reloaded.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(reloaded.topicId()).isEqualTo(job.topicId());
        assertThat(reloaded.outputFiles()).isEqualTo(job.outputFiles());
        assertThat(reelJobService.listJobs()).extracting(ReelJob::jobId).contains(job.jobId());

        // A second submission's outcome now depends on what analytics just fed back into
        // memory (this mission's Part 4/6): if the first topic was actually published,
        // analytics marked it POSTED for the first time ever in this pipeline — so the
        // curriculum has genuinely advanced, and topic selection legitimately picks the next
        // topic instead of being blocked. Otherwise the first topic is still ineligible for
        // NEXT_TOPIC selection (still IN_PROGRESS, or now NEEDS_REVISIT) and, with no other
        // topic yet eligible, the submission still fails clearly — as a returned FAILED job
        // record, never a thrown exception — with its own diagnosable report.
        ReelJob secondJob = reelJobService.submitJob();

        if ("READY".equals(job.publishingStatus())) {
            assertThat(secondJob.jobId()).isNotEqualTo(job.jobId());
            if (secondJob.topicId() != null) {
                assertThat(secondJob.topicId()).isNotEqualTo(job.topicId());
            }
        } else {
            assertThat(secondJob.status()).isEqualTo(ReelJob.Status.FAILED);
            assertThat(secondJob.jobId()).isNotEqualTo(job.jobId());
            assertThat(secondJob.failureReason()).contains("InvalidTopicException");
            assertThat(secondJob.completedAt()).isNotNull();

            assertThat(secondJob.outputFiles()).containsKey("report");
            ReelJobReport failedReport = objectMapper.readValue(
                    Path.of(secondJob.outputFiles().get("report")).toFile(), ReelJobReport.class);
            assertThat(failedReport.status()).isEqualTo("FAILED");
            assertThat(failedReport.errors()).isNotEmpty();
            assertThat(failedReport.stageResults()).anyMatch(s -> s.stageName().equals("AI_PIPELINE") && !s.success());
        }

        List<ReelJob> allJobs = reelJobService.listJobs();
        assertThat(allJobs).extracting(ReelJob::jobId).contains(job.jobId(), secondJob.jobId());
    }
}
