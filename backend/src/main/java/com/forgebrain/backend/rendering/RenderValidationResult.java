package com.forgebrain.backend.rendering;

import java.util.List;

/**
 * The outcome of {@link RenderValidator#validate(RenderPlan)}. {@code valid} is {@code true} iff
 * no {@link ValidationIssue.Severity#ERROR}-severity issue was found — {@link
 * ValidationIssue.Severity#WARNING} issues (e.g. an entirely empty subtitle timeline) don't
 * block a plan from being considered valid, mirroring the warnings/errors split already used by
 * {@link com.forgebrain.backend.pipeline.PipelineExecutionReport}.
 */
public record RenderValidationResult(boolean valid, List<ValidationIssue> issues) {

    /**
     * @param sceneId the scene this issue concerns, or {@code null} for a plan-wide issue
     */
    public record ValidationIssue(Severity severity, String code, String message, String sceneId) {
        public enum Severity {
            ERROR, WARNING
        }
    }
}
