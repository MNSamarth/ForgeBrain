/**
 * The Vertex AI integration seam. Every generative pipeline stage (Research, Lesson, Content
 * Director, Script) is expected to call {@link com.forgebrain.backend.vertex.VertexAiClient}
 * internally rather than depend on a Google Cloud type directly — this keeps the "replaceable
 * AI providers" principle (docs/ARCHITECTURE.md Section 3) real rather than aspirational.
 *
 * No client implementation or Google Cloud SDK dependency is included in this phase — see
 * docs/CONFIGURATION.md Section 2 and TODO.md.
 */
package com.forgebrain.backend.vertex;
