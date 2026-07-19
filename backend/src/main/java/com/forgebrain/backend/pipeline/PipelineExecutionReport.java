package com.forgebrain.backend.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Persisted execution report for one pipeline run.
 */
public record PipelineExecutionReport(
        String pipelineId,
        Instant executionStart,
        Instant executionEnd,
        Duration duration,
        String selectedTopic,
        List<StageExecutionSummary> stageResults,
        String finalStatus,
        List<String> warnings,
        List<String> errors
) {
}
