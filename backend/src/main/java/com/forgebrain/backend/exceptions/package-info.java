/**
 * Custom exception types for pipeline failures, invalid domain state, and configuration
 * errors. All extend {@link com.forgebrain.backend.exceptions.PipelineStageException} or
 * {@link java.lang.RuntimeException} directly, matching where in the pipeline they can
 * actually occur — see docs/PIPELINE.md for the stage list these map to.
 */
package com.forgebrain.backend.exceptions;
