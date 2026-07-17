package com.forgebrain.backend.exceptions;

/**
 * Thrown when a content-generating stage (Research, Lesson, Content Director, Script,
 * Storyboard — see docs/PIPELINE.md) fails to produce a valid output, including when an
 * upstream AI provider call fails or returns output that fails schema validation.
 */
public class ContentGenerationException extends PipelineStageException {

    public ContentGenerationException(String stageName, String message) {
        super(stageName, message);
    }

    public ContentGenerationException(String stageName, String message, Throwable cause) {
        super(stageName, message, cause);
    }
}
