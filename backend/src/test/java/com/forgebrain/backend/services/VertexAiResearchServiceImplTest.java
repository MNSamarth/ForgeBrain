package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.ai.AiGateway;
import com.forgebrain.backend.ai.AiGatewayImpl;
import com.forgebrain.backend.ai.InMemoryAiResponseCache;
import com.forgebrain.backend.ai.InMemoryPromptMetricsRecorder;
import com.forgebrain.backend.ai.PromptRegistryImpl;
import com.forgebrain.backend.config.AiGatewayConfig;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link VertexAiResearchServiceImpl} against a mocked {@link VertexAiClient}, wired
 * through a real {@link AiGatewayImpl} (retries/caching disabled so each scenario below is a
 * single deterministic attempt) — no real network call is made. Covers: successful generation is
 * parsed and assembled correctly, and every way the gateway can be "unavailable" (missing config,
 * thrown exception, malformed JSON, incomplete JSON) correctly falls back to the heuristic result.
 */
class VertexAiResearchServiceImplTest {

    private ObjectMapper objectMapper;
    private CurriculumLoaderImpl curriculumLoader;
    private Topic variablesTopic;
    private VertexAiClient vertexAiClient;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        curriculumLoader = new CurriculumLoaderImpl(objectMapper,
                new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        variablesTopic = curriculumLoader.findTopic("java-variables-and-data-types").orElseThrow();
        vertexAiClient = mock(VertexAiClient.class);
    }

    private VertexAiResearchServiceImpl service(VertexAiConfig config) {
        AiGateway aiGateway = new AiGatewayImpl(vertexAiClient, new PromptRegistryImpl(config),
                new AiGatewayConfig(0, 0, 1.0, 5000, false), new InMemoryAiResponseCache(),
                new InMemoryPromptMetricsRecorder(), objectMapper);
        return new VertexAiResearchServiceImpl(aiGateway, curriculumLoader);
    }

    @Test
    void parsesAValidVertexAiResponseIntoAResearchResult() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "gemini-2.0-flash-001", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
        String json = """
                {
                  "topic_summary": "Variables store typed values in Java.",
                  "core_concepts": ["Declaration", "Initialization", "Type safety"],
                  "simple_analogy": "A variable is like a labeled box that only fits one shape of item.",
                  "beginner_explanation": "You must declare a type before storing a value in Java.",
                  "advanced_notes": ["Primitive vs reference semantics affects copying."],
                  "safety_notes": ["Never assume int division returns a decimal result."]
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(json, "gemini-2.0-flash-001", "STOP"));

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-vertex-ai");
        assertThat(result.topicSummary()).isEqualTo("Variables store typed values in Java.");
        assertThat(result.coreConcepts()).containsExactly("Declaration", "Initialization", "Type safety");
        assertThat(result.simpleAnalogy()).contains("labeled box");
        assertThat(result.beginnerExplanation()).contains("declare a type");
        assertThat(result.advancedNotes()).containsExactly("Primitive vs reference semantics affects copying.");
        assertThat(result.safetyNotes()).containsExactly("Never assume int division returns a decimal result.");

        // Deterministic fields still come from curriculum data, not the model.
        assertThat(result.learningObjective()).isEqualTo(variablesTopic.learningObjective());
        assertThat(result.commonMisconceptions()).isEqualTo(variablesTopic.commonMistakes());
        assertThat(result.codeExampleIdeas()).isEqualTo(variablesTopic.exampleIdeas());
        assertThat(result.relatedTopics()).isEqualTo(variablesTopic.nextTopics());
    }

    @Test
    void fallsBackToHeuristicResearchWhenTheClientReportsMissingConfiguration() {
        // VertexAiClientImpl itself throws ConfigurationException for a blank project id (see
        // VertexAiClientImplTest); this exercises the service's dedicated catch branch for that
        // exception type against a mocked client standing in for that real behavior.
        VertexAiConfig config = new VertexAiConfig("", "us-central1", "gemini-2.0-flash-001", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicResearchWhenResearchModelIsBlank() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-heuristic");
        verifyNoInteractions(vertexAiClient);
    }

    @Test
    void fallsBackToHeuristicResearchWhenTheClientThrows() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "gemini-2.0-flash-001", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("simulated network failure"));

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicResearchWhenTheResponseIsNotValidJson() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "gemini-2.0-flash-001", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "gemini-2.0-flash-001", "STOP"));

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicResearchWhenTheResponseIsMissingRequiredFields() {
        VertexAiConfig config = new VertexAiConfig("demo-project", "us-central1", "gemini-2.0-flash-001", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
        String incompleteJson = """
                {
                  "topic_summary": "Variables store typed values in Java.",
                  "core_concepts": []
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(incompleteJson, "gemini-2.0-flash-001", "STOP"));

        ResearchResult result = service(config).research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.researchVersion()).isEqualTo("1.0.0-heuristic");
    }
}
