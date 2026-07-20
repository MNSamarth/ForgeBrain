package com.forgebrain.backend.runtime;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.config.ReviewerConfig;
import com.forgebrain.backend.config.RuntimeConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobService;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.services.MemoryService;
import com.forgebrain.backend.services.ReelAnalyticsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link ForgeBrainRuntime}. Dispatches {@link RuntimeConfig#dailyReelCount()} reel slots to
 * the existing, unmodified {@link ReelJobService#submitJob()} — every stage from topic selection
 * through publishing, analytics capture, and the memory feedback loop already runs inside that one
 * call (see {@code ReelJobServiceImpl}) — retrying a failing slot per {@link
 * RuntimeConfig#maxRetriesPerReel()} and always continuing to the remaining slots rather than
 * stopping the batch. After every slot finishes, builds one {@link RuntimeReport} that reuses
 * {@link ReelAnalyticsService#generateReport} for the analytics summary and {@link
 * MemoryService#getTopicRecord} for the resulting memory state, rather than recomputing either.
 */
@Component
public class ForgeBrainRuntimeImpl implements ForgeBrainRuntime {

    private static final Logger log = LoggerFactory.getLogger(ForgeBrainRuntimeImpl.class);

    private final RuntimeConfig config;
    private final ReelJobService reelJobService;
    private final ReelAnalyticsService reelAnalyticsService;
    private final MemoryService memoryService;
    private final ReviewerConfig reviewerConfig;
    private final PlatformUploadConfig platformUploadConfig;
    private final VertexAiConfig vertexAiConfig;
    private final CloudStorageConfig cloudStorageConfig;
    private final RuntimeReportWriter reportWriter;

    public ForgeBrainRuntimeImpl(RuntimeConfig config, ReelJobService reelJobService,
            ReelAnalyticsService reelAnalyticsService, MemoryService memoryService, ReviewerConfig reviewerConfig,
            PlatformUploadConfig platformUploadConfig, VertexAiConfig vertexAiConfig,
            CloudStorageConfig cloudStorageConfig, RuntimeReportWriter reportWriter) {
        this.config = config;
        this.reelJobService = reelJobService;
        this.reelAnalyticsService = reelAnalyticsService;
        this.memoryService = memoryService;
        this.reviewerConfig = reviewerConfig;
        this.platformUploadConfig = platformUploadConfig;
        this.vertexAiConfig = vertexAiConfig;
        this.cloudStorageConfig = cloudStorageConfig;
        this.reportWriter = reportWriter;
    }

    @Override
    public RuntimeReport run() {
        String runtimeId = UUID.randomUUID().toString();
        int reelCount = Math.max(0, config.dailyReelCount());
        log.info("ForgeBrain Runtime '{}' starting: {} reel(s) requested, parallelism={}, "
                + "max-retries-per-reel={}.", runtimeId, reelCount, config.parallelism(), config.maxRetriesPerReel());

        RuntimeState state = new RuntimeState(runtimeId, reelCount);
        state.markRunning("DISPATCHING");
        Instant windowStart = Instant.now();

        List<ReelExecutionOutcome> executions = executeReels(state, reelCount);

        state.markFinished();
        RuntimeReport report = buildReport(runtimeId, windowStart, state, executions);
        String reportPath = reportWriter.write(report);
        log.info("ForgeBrain Runtime '{}' finished in {}: {}/{} completed, {} failed. Report: {}", runtimeId,
                report.duration(), report.reelsCompleted(), report.reelsRequested(), report.reelsFailed(),
                reportPath);
        return report;
    }

    private List<ReelExecutionOutcome> executeReels(RuntimeState state, int reelCount) {
        if (reelCount == 0) {
            return List.of();
        }
        int parallelism = Math.max(1, config.parallelism());
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, reelCount));
        try {
            List<Future<ReelExecutionOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < reelCount; i++) {
                futures.add(executor.submit(() -> executeOneReelWithRetry(state)));
            }
            List<ReelExecutionOutcome> results = new ArrayList<>();
            for (Future<ReelExecutionOutcome> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    log.error("Unexpected failure dispatching a reel slot for runtime '{}'.", state.snapshot()
                            .runtimeId(), e);
                    state.recordFailed("Reel slot dispatch failed unexpectedly: " + e.getMessage());
                    results.add(new ReelExecutionOutcome(null, 0));
                }
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Submits one reel slot, retrying up to {@link RuntimeConfig#maxRetriesPerReel()} additional
     * times while {@link ReelJobService#submitJob()} keeps returning a {@link
     * ReelJob.Status#FAILED} job — {@code submitJob()} never throws, so every attempt here is
     * inspected, never caught.
     */
    private ReelExecutionOutcome executeOneReelWithRetry(RuntimeState state) {
        int maxAttempts = 1 + Math.max(0, config.maxRetriesPerReel());
        ReelJob lastJob = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            state.markRunning("REEL_EXECUTION");
            ReelJob job = reelJobService.submitJob();
            lastJob = job;
            if (job.status() == ReelJob.Status.COMPLETED) {
                state.recordCompleted();
                return new ReelExecutionOutcome(job, attempt);
            }
            String warning = "Reel job '" + job.jobId() + "' ended with status " + job.status() + " on attempt "
                    + attempt + "/" + maxAttempts + ": " + job.failureReason();
            state.addWarning(warning);
            log.warn(warning);
            if (attempt < maxAttempts) {
                sleep(config.retryBackoffMillis());
            }
        }
        state.recordFailed("Reel slot exhausted " + maxAttempts + " attempt(s); last job '"
                + (lastJob != null ? lastJob.jobId() : "n/a") + "' failure: "
                + (lastJob != null ? lastJob.failureReason() : "unknown"));
        return new ReelExecutionOutcome(lastJob, maxAttempts);
    }

    private RuntimeReport buildReport(String runtimeId, Instant windowStart, RuntimeState state,
            List<ReelExecutionOutcome> executions) {
        Instant completedAt = Instant.now();

        List<RuntimeReport.ReelExecutionSummary> summaries = executions.stream()
                .map(ForgeBrainRuntimeImpl::toSummary)
                .toList();

        AnalyticsReport analyticsSummary = null;
        try {
            analyticsSummary = reelAnalyticsService.generateReport(windowStart, completedAt);
        } catch (RuntimeException e) {
            state.addWarning("Failed to build analytics summary for runtime '" + runtimeId + "': " + e.getMessage());
            log.warn("Failed to build analytics summary for runtime '{}'.", runtimeId, e);
        }

        List<RuntimeReport.MemoryUpdateSummary> memoryUpdates = buildMemoryUpdateSummaries(executions);
        String publishStatusSummary = summarizePublishStatuses(executions);
        RuntimeReport.RuntimeConfigSnapshot configSnapshot = buildConfigSnapshot();

        // Snapshot last, so warnings/errors recorded above (e.g. an analytics failure) make it
        // into the report — not just whatever RuntimeState held before this method ran.
        RuntimeState.Snapshot snapshot = state.snapshot();

        return new RuntimeReport(runtimeId, snapshot.startedAt(), completedAt,
                Duration.between(snapshot.startedAt(), completedAt), snapshot.reelsRequested(),
                snapshot.reelsCompleted(), snapshot.reelsFailed(), summaries, publishStatusSummary, analyticsSummary,
                memoryUpdates, snapshot.warnings(), snapshot.errors(), configSnapshot);
    }

    private List<RuntimeReport.MemoryUpdateSummary> buildMemoryUpdateSummaries(List<ReelExecutionOutcome> executions) {
        List<RuntimeReport.MemoryUpdateSummary> updates = new ArrayList<>();
        List<String> seenTopicIds = new ArrayList<>();
        for (ReelExecutionOutcome outcome : executions) {
            if (outcome.job() == null || outcome.job().topicId() == null
                    || seenTopicIds.contains(outcome.job().topicId())) {
                continue;
            }
            String topicId = outcome.job().topicId();
            seenTopicIds.add(topicId);
            MemoryState.TopicRecord record = memoryService.getTopicRecord(topicId);
            if (record != null) {
                updates.add(new RuntimeReport.MemoryUpdateSummary(topicId, record.status().name(),
                        record.performanceScore(), record.priority() != null ? record.priority().name() : null));
            }
        }
        return List.copyOf(updates);
    }

    private RuntimeReport.RuntimeConfigSnapshot buildConfigSnapshot() {
        return new RuntimeReport.RuntimeConfigSnapshot(config.dailyReelCount(), config.parallelism(),
                config.maxRetriesPerReel(), config.runtimeMode(), reviewerConfig.approvalThreshold(),
                platformUploadConfig.dryRunOnly(), platformUploadConfig.youtube().enabled(),
                platformUploadConfig.instagram().enabled(), cloudStorageConfig.enabled(),
                vertexAiConfig.researchModel(), vertexAiConfig.lessonModel(), vertexAiConfig.contentDirectorModel(),
                vertexAiConfig.scriptModel());
    }

    private static String summarizePublishStatuses(List<ReelExecutionOutcome> executions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ReelExecutionOutcome outcome : executions) {
            if (outcome.job() == null) {
                continue;
            }
            String status = outcome.job().publishingStatus() == null ? "NONE" : outcome.job().publishingStatus();
            counts.merge(status, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private static RuntimeReport.ReelExecutionSummary toSummary(ReelExecutionOutcome outcome) {
        ReelJob job = outcome.job();
        if (job == null) {
            return new RuntimeReport.ReelExecutionSummary(null, null, null, ReelJob.Status.FAILED, null, null,
                    outcome.attempts(), "No job record was produced for this reel slot.");
        }
        return new RuntimeReport.ReelExecutionSummary(job.jobId(), job.topicId(), job.topicTitle(), job.status(),
                job.reviewVerdict(), job.publishingStatus(), outcome.attempts(), job.failureReason());
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ReelExecutionOutcome(ReelJob job, int attempts) {
    }
}
