package com.forgebrain.backend.exceptions;

/**
 * Base exception for a failure attributable to a specific pipeline stage (see
 * docs/PIPELINE.md for the fourteen-stage list). Carries the stage name so a failure is
 * traceable to exactly one stage, mirroring how {@code ReviewResult.suggested_stage_to_revisit}
 * in reviewer-schema.json names a stage rather than describing a vague failure.
 */
public class PipelineStageException extends RuntimeException {

    private final String stageName;

    public PipelineStageException(String stageName, String message) {
        super(message);
        this.stageName = stageName;
    }

    public PipelineStageException(String stageName, String message, Throwable cause) {
        super(message, cause);
        this.stageName = stageName;
    }

    public String stageName() {
        return stageName;
    }
}
