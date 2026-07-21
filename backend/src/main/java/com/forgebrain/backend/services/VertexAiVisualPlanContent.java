package com.forgebrain.backend.services;

import java.util.List;

/**
 * The full JSON shape Vertex AI is asked to return for the Visual Director stage — see {@link
 * VisualDirectorPromptBuilder}. Deserialized by the shared snake_case {@code ObjectMapper} bean.
 */
record VertexAiVisualPlanContent(
        String thumbnailBrief,
        List<VertexAiVisualScenePlan> scenes
) {
}
