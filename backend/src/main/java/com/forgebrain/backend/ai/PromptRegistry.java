package com.forgebrain.backend.ai;

import java.util.List;

/**
 * Lookup for every registered {@link PromptDefinition} — the model-routing table {@link
 * AiGateway} consults before every call. Read-only and built once at startup from {@code
 * VertexAiConfig}; adding a new generative stage means adding one entry here, not writing a new
 * "check config, build request, call the client" block in a new service.
 */
public interface PromptRegistry {

    /**
     * @throws com.forgebrain.backend.exceptions.ConfigurationException if no prompt is registered
     *                                                                  under this name
     */
    PromptDefinition get(String promptName);

    List<PromptDefinition> findAll();
}
