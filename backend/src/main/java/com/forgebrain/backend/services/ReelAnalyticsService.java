package com.forgebrain.backend.services;

import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import java.time.Instant;
import java.util.List;

/**
 * Contract for the analytics/feedback-loop layer: capturing a durable outcome snapshot for every
 * completed {@link com.forgebrain.backend.job.ReelJob} (successful, rejected, or failed), feeding
 * strong signals back into {@link MemoryService}, and reporting on a batch or time window.
 * Distinct from {@link AnalyticsService}, which is reserved for real audience/platform metrics
 * that don't exist yet — this service works entirely from signals the pipeline already produces
 * today.
 */
public interface ReelAnalyticsService {

    /**
     * Records one job's outcome as a durable {@link ReelOutcomeSnapshot} and, when the job
     * reached topic selection, feeds the resulting per-topic aggregate back into {@link
     * MemoryService#updateTopicRecord}. Safe to call for a {@link ReelJob.Status#FAILED} job —
     * {@code contentStrategy}, {@code script}, and {@code videoPackage} may all be {@code null}
     * when the job failed before producing them.
     */
    ReelOutcomeSnapshot recordOutcome(ReelJob job, ReelJobReport report, ContentStrategy contentStrategy,
            Script script, VideoPackage videoPackage);

    /**
     * Every snapshot captured so far, in no particular order.
     */
    List<ReelOutcomeSnapshot> findAll();

    /**
     * Aggregates every snapshot whose {@code jobCreatedAt} falls within {@code [windowStart,
     * windowEnd]} into a readable {@link AnalyticsReport}, and writes it to disk as both JSON and
     * markdown.
     */
    AnalyticsReport generateReport(Instant windowStart, Instant windowEnd);
}
