package com.forgebrain.backend.vertex;

/**
 * The single seam every generative pipeline stage calls through. No implementation exists in
 * this phase — see docs/CONFIGURATION.md Section 2 for the intended provider (Vertex AI) and
 * TODO.md for what remains before this is functional.
 */
public interface VertexAiClient {

    VertexAiPromptResponse generate(VertexAiPromptRequest request);
}
