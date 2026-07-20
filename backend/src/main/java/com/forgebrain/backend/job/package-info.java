/**
 * The production job layer: turns the one-shot {@link com.forgebrain.backend.pipeline.ReelExportService}
 * export into a durable, trackable unit of work. {@link com.forgebrain.backend.job.ReelJob} is
 * the job record (lifecycle status, timestamps, output references, warnings, fallback usage);
 * {@link com.forgebrain.backend.job.ReelJobRepository} persists it; {@link
 * com.forgebrain.backend.job.ReelJobService} runs a job through the same pipeline/render stages
 * {@code ReelExportServiceImpl} uses, then hands the result to {@link
 * com.forgebrain.backend.job.OutputPackagingService}, which packages every output file through
 * the {@link com.forgebrain.backend.job.OutputStorage} seam — local today, Cloud Storage-ready
 * later without changing this package's shape.
 *
 * <p>Deliberately additive: nothing here modifies {@code com.forgebrain.backend.pipeline}'s
 * existing synchronous export path, which keeps working exactly as before (see {@code
 * ReelExportServiceImplTest}). This package composes the same lower-level stage services
 * ({@code VoiceService}, {@code SubtitleService}, {@code AssetService}, {@code RenderPlanBuilder},
 * {@code RenderValidator}, {@code RenderEngine}, {@code PipelineOrchestrator}) independently.
 */
package com.forgebrain.backend.job;
