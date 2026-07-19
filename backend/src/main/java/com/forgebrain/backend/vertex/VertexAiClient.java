package com.forgebrain.backend.vertex;

/**
 * The single seam every generative pipeline stage calls through. {@link VertexAiClientImpl}
 * calls Google Vertex AI using Application Default Credentials — see backend/README.md for
 * local setup. Callers must treat any failure (including {@link
 * com.forgebrain.backend.exceptions.ConfigurationException} for missing project/location
 * config) as a signal to fall back to a deterministic path, not as a fatal pipeline error.
 */
public interface VertexAiClient {

    VertexAiPromptResponse generate(VertexAiPromptRequest request);
}
