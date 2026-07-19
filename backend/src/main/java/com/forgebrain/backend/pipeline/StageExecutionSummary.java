package com.forgebrain.backend.pipeline;

import java.time.Duration;

/**
 * Execution summary for one pipeline stage in one run.
 */
public record StageExecutionSummary(
        String stageName,
        Duration duration,
        boolean success,
        String inputSummary,
        String outputSummary,
        boolean fallbackUsed,
        String confidence,
        String exception
) {
}
