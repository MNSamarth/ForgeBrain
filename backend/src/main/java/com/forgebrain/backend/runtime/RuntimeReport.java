package com.forgebrain.backend.runtime;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.job.ReelJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The durable record of one {@code ForgeBrainRuntime#run()} call — written by {@code
 * RuntimeReportWriter} as {@code <runtimeId>.json}, readable by a human or by code via the shared
 * {@code ObjectMapper}, mirroring the report pattern already established by {@code
 * ReelJobReport}/{@code ReelExportReport}. {@code analyticsSummary} is not recomputed here — it is
 * {@code ReelAnalyticsService#generateReport} called once for the exact time window this runtime
 * ran in, reusing the analytics stage's own aggregation rather than duplicating it.
 *
 * @param reelsRequested    {@code RuntimeConfig.dailyReelCount()} at the time this run started
 * @param reelsCompleted    how many reel slots reached {@link ReelJob.Status#COMPLETED}
 * @param reelsFailed       how many reel slots exhausted their retries without completing
 * @param reelExecutions    one entry per reel slot attempted, in dispatch order
 * @param publishStatusSummary a human-readable tally of {@link ReelJob#publishingStatus()} values
 *                              across every completed reel, e.g. {@code "2 READY, 1
 *                              SKIPPED_NOT_APPROVED"}
 * @param analyticsSummary  the {@code AnalyticsReport} covering exactly this run's reels, or
 *                          {@code null} if it could not be built (recorded as a warning instead)
 * @param memoryUpdates     the resulting {@code MemoryState.TopicRecord} for every distinct topic
 *                          touched by this run, read after every reel finished
 * @param configSnapshot    the live values of every config domain {@code RuntimeConfig}
 *                          deliberately does not duplicate — see {@code RuntimeConfig}'s javadoc
 */
public record RuntimeReport(
        String runtimeId,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        int reelsRequested,
        int reelsCompleted,
        int reelsFailed,
        List<ReelExecutionSummary> reelExecutions,
        String publishStatusSummary,
        AnalyticsReport analyticsSummary,
        List<MemoryUpdateSummary> memoryUpdates,
        List<String> warnings,
        List<String> errors,
        RuntimeConfigSnapshot configSnapshot
) {

    /**
     * @param attempts how many {@code submitJob()} calls this reel slot took (1 = succeeded or
     *                 failed on the first try; more only when {@code RuntimeConfig.maxRetriesPerReel()}
     *                 &gt; 0 and earlier attempts failed)
     */
    public record ReelExecutionSummary(
            String jobId,
            String topicId,
            String topicTitle,
            ReelJob.Status status,
            String reviewVerdict,
            String publishingStatus,
            int attempts,
            String failureReason
    ) {
    }

    public record MemoryUpdateSummary(
            String topicId,
            String status,
            Double performanceScore,
            String priority
    ) {
    }

    public record RuntimeConfigSnapshot(
            int dailyReelCount,
            int parallelism,
            int maxRetriesPerReel,
            String runtimeMode,
            double reviewApprovalThreshold,
            boolean publishDryRunOnly,
            boolean youtubeUploadEnabled,
            boolean instagramUploadEnabled,
            boolean cloudStorageEnabled,
            String researchModel,
            String lessonModel,
            String contentDirectorModel,
            String scriptModel
    ) {
    }
}
