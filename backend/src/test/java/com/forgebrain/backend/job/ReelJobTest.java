package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ReelJob}'s lifecycle transitions — each one is a small, isolated,
 * directly testable pure function, per this mission's Part 1 ("make job state transitions
 * explicit and testable").
 */
class ReelJobTest {

    @Test
    void queuedStartsWithNoTopicNoTimestampsAndEmptyCollections() {
        ReelJob job = ReelJob.queued("job-1", "run-1");

        assertThat(job.jobId()).isEqualTo("job-1");
        assertThat(job.pipelineRunId()).isEqualTo("run-1");
        assertThat(job.status()).isEqualTo(ReelJob.Status.QUEUED);
        assertThat(job.createdAt()).isNotNull();
        assertThat(job.startedAt()).isNull();
        assertThat(job.completedAt()).isNull();
        assertThat(job.topicId()).isNull();
        assertThat(job.outputFiles()).isEmpty();
        assertThat(job.warnings()).isEmpty();
        assertThat(job.fallbackStages()).isEmpty();
    }

    @Test
    void runningSetsStartedAtAndStatusWithoutTouchingOtherFields() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running();

        assertThat(job.status()).isEqualTo(ReelJob.Status.RUNNING);
        assertThat(job.startedAt()).isNotNull();
        assertThat(job.jobId()).isEqualTo("job-1");
    }

    @Test
    void validatingRenderingAndPackagingOnlyChangeStatus() {
        ReelJob base = ReelJob.queued("job-1", "run-1").running().withTopic("t1", "Topic One");

        assertThat(base.validating().status()).isEqualTo(ReelJob.Status.VALIDATING);
        assertThat(base.rendering().status()).isEqualTo(ReelJob.Status.RENDERING);
        assertThat(base.packaging().status()).isEqualTo(ReelJob.Status.PACKAGING);
        assertThat(base.validating().topicId()).isEqualTo("t1");
    }

    @Test
    void withTopicSetsTopicIdAndTitle() {
        ReelJob job = ReelJob.queued("job-1", "run-1").withTopic("java-for-loop", "The For Loop");

        assertThat(job.topicId()).isEqualTo("java-for-loop");
        assertThat(job.topicTitle()).isEqualTo("The For Loop");
    }

    @Test
    void withOutputFilesMergesRatherThanReplaces() {
        ReelJob job = ReelJob.queued("job-1", "run-1")
                .withOutputFiles(Map.of("video", "/path/reel.mp4"))
                .withOutputFiles(Map.of("thumbnail", "/path/thumbnail.jpg"));

        assertThat(job.outputFiles()).containsEntry("video", "/path/reel.mp4");
        assertThat(job.outputFiles()).containsEntry("thumbnail", "/path/thumbnail.jpg");
    }

    @Test
    void withWarningAndWithFallbackStageAppendRatherThanReplace() {
        ReelJob job = ReelJob.queued("job-1", "run-1")
                .withWarning("first warning")
                .withWarning("second warning")
                .withFallbackStage("VOICE")
                .withFallbackStage("ASSETS");

        assertThat(job.warnings()).containsExactly("first warning", "second warning");
        assertThat(job.fallbackStages()).containsExactly("VOICE", "ASSETS");
    }

    @Test
    void withRenderChecksumSetsTheChecksum() {
        ReelJob job = ReelJob.queued("job-1", "run-1").withRenderChecksum("abc123");

        assertThat(job.renderChecksum()).isEqualTo("abc123");
    }

    @Test
    void completedSetsTerminalTimestampDurationAndStatus() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running();

        ReelJob completed = job.completed();

        assertThat(completed.status()).isEqualTo(ReelJob.Status.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.duration()).isNotNull();
        assertThat(completed.duration().isNegative()).isFalse();
    }

    @Test
    void failedSetsFailureReasonTerminalTimestampAndStatus() {
        ReelJob job = ReelJob.queued("job-1", "run-1").running();

        ReelJob failed = job.failed("RenderExecutionException: something broke");

        assertThat(failed.status()).isEqualTo(ReelJob.Status.FAILED);
        assertThat(failed.failureReason()).isEqualTo("RenderExecutionException: something broke");
        assertThat(failed.completedAt()).isNotNull();
        assertThat(failed.duration()).isNotNull();
    }

    @Test
    void transitionsPreserveFieldsSetEarlierInTheLifecycle() {
        ReelJob job = ReelJob.queued("job-1", "run-1")
                .running()
                .withTopic("java-for-loop", "The For Loop")
                .withWarning("a warning")
                .withFallbackStage("VOICE")
                .validating()
                .rendering()
                .withRenderChecksum("deadbeef")
                .packaging()
                .withOutputDirectory("/out/dir")
                .withOutputFiles(Map.of("video", "/out/dir/reel.mp4"))
                .completed();

        assertThat(job.topicId()).isEqualTo("java-for-loop");
        assertThat(job.warnings()).containsExactly("a warning");
        assertThat(job.fallbackStages()).containsExactly("VOICE");
        assertThat(job.renderChecksum()).isEqualTo("deadbeef");
        assertThat(job.outputDirectory()).isEqualTo("/out/dir");
        assertThat(job.outputFiles()).containsEntry("video", "/out/dir/reel.mp4");
        assertThat(job.status()).isEqualTo(ReelJob.Status.COMPLETED);
    }
}
