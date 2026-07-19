package com.forgebrain.backend.vertex;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the fail-fast config checks in {@link VertexAiClientImpl} — the only paths
 * exercisable without live GCP credentials. A real, network-backed call is out of scope for a
 * unit test; {@link com.forgebrain.backend.services.VertexAiResearchServiceImplTest} covers
 * caller behavior against a mocked {@link VertexAiClient} instead.
 */
class VertexAiClientImplTest {

    @Test
    void throwsConfigurationExceptionWhenProjectIdIsBlank() {
        VertexAiConfig config = new VertexAiConfig("", "us-central1", "gemini-2.0-flash-001", "", "",
                0.4, 2048, "application/json");
        VertexAiClientImpl client = new VertexAiClientImpl(config);

        assertThatThrownBy(() -> client.generate(
                new VertexAiPromptRequest("gemini-2.0-flash-001", "prompt text", Map.of())))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("project-id");
    }

    @Test
    void throwsConfigurationExceptionWhenModelIdIsBlank() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "gemini-2.0-flash-001", "", "",
                0.4, 2048, "application/json");
        VertexAiClientImpl client = new VertexAiClientImpl(config);

        assertThatThrownBy(() -> client.generate(new VertexAiPromptRequest("", "prompt text", Map.of())))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("modelId");
    }
}
