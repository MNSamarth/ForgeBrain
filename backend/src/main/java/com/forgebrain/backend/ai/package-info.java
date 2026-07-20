/**
 * Centralized AI orchestration seam every generative pipeline stage (Research, Lesson, Content
 * Director, Script) calls through instead of {@link com.forgebrain.backend.vertex.VertexAiClient}
 * directly. {@link com.forgebrain.backend.ai.AiGateway} composes model routing (via {@link
 * com.forgebrain.backend.ai.PromptRegistry}), retry with exponential backoff (via the plain
 * {@code RetryExecutor}), a call timeout, response parsing and validation, a pluggable response
 * cache ({@link com.forgebrain.backend.ai.AiResponseCache}), and per-prompt metrics ({@link
 * com.forgebrain.backend.ai.PromptMetricsRecorder}) around the existing, unmodified {@code
 * VertexAiClient} — see backend/README.md's "AI Gateway" section.
 */
package com.forgebrain.backend.ai;
