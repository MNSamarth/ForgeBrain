/**
 * Service contracts for every stage in docs/PIPELINE.md, plus {@link
 * com.forgebrain.backend.services.MemoryService} and {@link
 * com.forgebrain.backend.services.AnalyticsService}, which support the pipeline without being
 * linear stages in it themselves.
 *
 * <p>Method parameters are explicit and typed against {@link com.forgebrain.backend.models},
 * deliberately not a single shared "context" blob, so each contract is self-documenting:
 * reading a method signature should tell you exactly what that stage needs, matching the
 * Inputs table in its corresponding {@code *-spec.md}.
 *
 * <p><b>Implementation status</b> (see {@code NEXT_EXECUTION.md} for detail): {@link
 * MemoryService}, {@link TopicSelector}, {@link ResearchService}, {@link LessonService},
 * {@link ContentDirectorService}, {@link ScriptService}, {@link StoryboardService}, {@link
 * VoiceService}, {@link SubtitleService}, {@link AssetService}, and {@link ReviewerService}
 * have real implementations, covering topic selection through the post-render quality gate.
 * {@link RendererService} (superseded by {@code com.forgebrain.backend.rendering}'s real FFmpeg
 * path — see backend/README.md's "Storyboard to MP4" section), {@link PublishingService}, and
 * {@link AnalyticsService} remain contracts only — publishing was explicitly out of scope for
 * this pass (see TODO.md).
 */
package com.forgebrain.backend.services;
