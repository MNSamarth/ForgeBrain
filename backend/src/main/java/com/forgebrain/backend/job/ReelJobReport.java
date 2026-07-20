package com.forgebrain.backend.job;

import com.forgebrain.backend.pipeline.StageExecutionSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The durable execution record for one {@link ReelJob} run — extends the report pattern already
 * established by {@code com.forgebrain.backend.pipeline.ReelExportReport} (reusing {@link
 * StageExecutionSummary} directly) with the job-specific fields this mission's Part 7 asks for:
 * a job id distinct from the pipeline run id, explicit fallback-stage tracking, and a packaging
 * summary. Written by {@link ReelJobReportWriter} as {@code report.json} alongside the reel it
 * describes — readable by a human opening the file and by {@link
 * com.forgebrain.backend.job.ReelJobServiceImpl} (or a test) via the shared {@code ObjectMapper}.
 */
public record ReelJobReport(
        String jobId,
        String pipelineRunId,
        String topicId,
        Instant executionStart,
        Instant executionEnd,
        Duration duration,
        List<StageExecutionSummary> stageResults,
        String status,
        Map<String, String> outputPaths,
        List<String> warnings,
        List<String> errors,
        List<String> fallbackStages,
        String renderValidationSummary,
        String packagingSummary
) {
}
