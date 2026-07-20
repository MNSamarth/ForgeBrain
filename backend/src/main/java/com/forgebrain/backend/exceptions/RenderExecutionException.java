package com.forgebrain.backend.exceptions;

/**
 * Thrown when turning a {@link com.forgebrain.backend.rendering.RenderPlan} into a video fails:
 * the plan didn't pass {@link com.forgebrain.backend.rendering.RenderValidator}, the {@code
 * ffmpeg} executable couldn't be started, or the process exited with a non-zero status.
 */
public class RenderExecutionException extends PipelineStageException {

    public RenderExecutionException(String message) {
        super("RENDER", message);
    }

    public RenderExecutionException(String message, Throwable cause) {
        super("RENDER", message, cause);
    }
}
