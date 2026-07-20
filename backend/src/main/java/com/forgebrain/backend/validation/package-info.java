/**
 * The Production Validation Suite: reusable, pure checks over the pipeline's existing artifact
 * shapes ({@link com.forgebrain.backend.validation.PipelineInvariants}, {@link
 * com.forgebrain.backend.validation.ArtifactValidator}) and the aggregated {@link
 * com.forgebrain.backend.validation.ProductionReadinessReport} they feed. Nothing here calls a
 * service, re-runs a stage, or changes production behavior — it only inspects records the
 * pipeline already produces. See backend/README.md's "Production Validation" section.
 */
package com.forgebrain.backend.validation;
