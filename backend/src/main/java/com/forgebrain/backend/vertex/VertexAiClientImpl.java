package com.forgebrain.backend.vertex;

import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Calls Google Vertex AI using the official Java client library, authenticating via
 * Application Default Credentials (no embedded API keys — see backend/README.md). A new {@link
 * VertexAI} client is opened per call and closed immediately after, since this stage runs at
 * most a few times per pipeline run and the connection setup cost is not worth holding a
 * long-lived client across requests.
 *
 * <p>Fails fast with {@link ConfigurationException} when {@code forgebrain.vertex-ai.project-id}
 * is blank, and wraps any I/O failure from the SDK the same way, so every caller can treat "no
 * usable Vertex AI client" as one exception type and fall back to a deterministic path.
 */
@Component
public class VertexAiClientImpl implements VertexAiClient {

    private static final String DEFAULT_LOCATION = "us-central1";
    private static final String JSON_MIME_TYPE = "application/json";

    private final VertexAiConfig config;

    public VertexAiClientImpl(VertexAiConfig config) {
        this.config = config;
    }

    @Override
    public VertexAiPromptResponse generate(VertexAiPromptRequest request) {
        if (config.projectId() == null || config.projectId().isBlank()) {
            throw new ConfigurationException(
                    "forgebrain.vertex-ai.project-id is not configured; run "
                            + "'gcloud auth application-default login' and set the project id locally "
                            + "(see backend/README.md) before calling Vertex AI");
        }
        if (request.modelId() == null || request.modelId().isBlank()) {
            throw new ConfigurationException("VertexAiPromptRequest.modelId must not be blank");
        }

        String location = config.location() == null || config.location().isBlank()
                ? DEFAULT_LOCATION
                : config.location();

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder()
                .setResponseMimeType(request.responseMimeType() == null || request.responseMimeType().isBlank()
                        ? JSON_MIME_TYPE
                        : request.responseMimeType());
        if (request.temperature() != null) {
            generationConfigBuilder.setTemperature(request.temperature().floatValue());
        }
        if (request.maxOutputTokens() != null) {
            generationConfigBuilder.setMaxOutputTokens(request.maxOutputTokens());
        }
        GenerationConfig generationConfig = generationConfigBuilder.build();

        try (VertexAI vertexAI = new VertexAI(config.projectId(), location)) {
            GenerativeModel model = new GenerativeModel(request.modelId(), vertexAI)
                    .withGenerationConfig(generationConfig);

            GenerateContentResponse response = model.generateContent(request.promptText());
            String text = ResponseHandler.getText(response);
            Candidate.FinishReason finishReason = ResponseHandler.getFinishReason(response);

            return new VertexAiPromptResponse(text, request.modelId(), finishReason.name());
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Vertex AI call failed for model '" + request.modelId() + "': " + e.getMessage(), e);
        }
    }
}
