package com.forgebrain.backend.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted diagnostics for one end-to-end reel export run — the stages after {@link
 * PipelineExecutionReport} already covers (topic selection through storyboard): voice,
 * subtitles, assets, render plan construction, render validation, and render execution. Written
 * alongside the reel's own output files (see {@link ReelExportReportWriter}) so a failed or
 * degraded reel can be debugged from one file instead of scattered log lines.
 *
 * @param runId                     unique id for this export run, independent of the underlying
 *                                  AI pipeline's own {@code pipelineId}
 * @param topicId                   the topic this run exported, or {@code null} if the run
 *                                  failed before topic selection completed
 * @param stageResults              one entry per stage this report covers, in execution order;
 *                                  the AI-pipeline stages (topic selection through storyboard)
 *                                  appear as a single summarized entry — see their own {@link
 *                                  PipelineExecutionReport} for that per-stage breakdown
 * @param renderValidationSummary   human-readable summary of {@code RenderValidator}'s findings,
 *                                  or {@code null} if validation never ran
 * @param finalStatus               {@code "SUCCESS"} or {@code "FAILED"}
 * @param outputPaths               absolute paths written by this run, keyed by {@code "video"},
 *                                  {@code "thumbnail"}, {@code "subtitles"}, {@code "metadata"},
 *                                  {@code "report"} — a key is absent if that file wasn't written
 */
public record ReelExportReport(
        String runId,
        Instant executionStart,
        Instant executionEnd,
        Duration duration,
        String topicId,
        List<StageExecutionSummary> stageResults,
        String renderValidationSummary,
        String finalStatus,
        List<String> warnings,
        List<String> errors,
        Map<String, String> outputPaths
) {
}
