package com.forgebrain.backend.services;

import java.util.List;

/**
 * The subset of {@link com.forgebrain.backend.models.ResearchResult} that Vertex AI is asked
 * to generate — see {@link ResearchPromptBuilder}. Deserialized from the model's JSON response
 * by the shared snake_case {@link com.fasterxml.jackson.databind.ObjectMapper} bean; every
 * other {@code ResearchResult} field stays sourced from curriculum data, never from the model.
 */
record VertexResearchContent(
        String topicSummary,
        List<String> coreConcepts,
        String simpleAnalogy,
        String beginnerExplanation,
        List<String> advancedNotes,
        List<String> safetyNotes
) {
}
