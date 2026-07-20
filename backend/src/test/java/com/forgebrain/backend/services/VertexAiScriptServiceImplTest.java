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
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link VertexAiScriptServiceImpl} against a mocked {@link VertexAiClient}, wired
 * through a real {@link AiGatewayImpl} (retries/caching disabled so each scenario below is a
 * single deterministic attempt) — no real network call is made. Covers: successful generation is
 * parsed and assembled correctly (including that the derived fields stay internally consistent),
 * and every way the gateway can be "unavailable" (missing config, thrown exception, malformed
 * JSON, incomplete JSON) correctly falls back to the heuristic script.
 */
class VertexAiScriptServiceImplTest {

    private ObjectMapper objectMapper;
    private Lesson lesson;
    private ContentStrategy strategy;
    private VertexAiClient vertexAiClient;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper,
                new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        var researchService = new ResearchServiceImpl(curriculumLoader);
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = researchService.research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        lesson = new LessonServiceImpl().generateLesson(research, null, null);
        strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        vertexAiClient = mock(VertexAiClient.class);
    }

    private VertexAiScriptServiceImpl service(VertexAiConfig config) {
        AiGateway aiGateway = new AiGatewayImpl(vertexAiClient, new PromptRegistryImpl(config),
                new AiGatewayConfig(0, 0, 1.0, 5000, false), new InMemoryAiResponseCache(),
                new InMemoryPromptMetricsRecorder(), objectMapper);
        return new VertexAiScriptServiceImpl(aiGateway);
    }

    private static VertexAiConfig config(String projectId, String scriptModel) {
        return new VertexAiConfig(projectId, "us-central1", "", "", scriptModel, "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
    }

    @Test
    void parsesAValidVertexAiResponseIntoAScriptWithConsistentDerivedFields() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String json = """
                {
                  "hook": "Watch what happens when this loop forgets to update itself.",
                  "intro_line": "Here's why a for loop can run forever by accident.",
                  "main_script": [
                    { "beat": "explain the header", "spoken_line": "The header has three parts: start, condition, and update." },
                    { "beat": "explain the condition", "spoken_line": "Java checks that condition before every single lap." }
                  ],
                  "code_narration": {
                    "spoken_lines": ["Here the loop counts from zero up to four."],
                    "focus_line": "i < 5"
                  },
                  "recap_line": "A for loop keeps going until its condition turns false.",
                  "cta_line": "Try changing the update step yourself and see what breaks.",
                  "scene_text": [
                    { "scene_reference": "cold-open infinite loop", "text": "INFINITE LOOP" },
                    { "scene_reference": "explain the header", "text": "INIT - CONDITION - UPDATE" }
                  ],
                  "tone": "DIRECT_AND_PUNCHY",
                  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [] }
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(json, "gemini-2.0-flash-001", "STOP"));

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-vertex-ai");
        assertThat(script.hook()).contains("forgets to update");
        assertThat(script.mainScript()).hasSize(2);
        assertThat(script.codeNarration().codeSnippet()).isEqualTo(lesson.coreExample().codeSketch());
        assertThat(script.codeNarration().focusLine()).isEqualTo("i < 5");
        assertThat(script.sceneText()).hasSize(2);
        assertThat(script.tone()).isEqualTo(Script.Tone.DIRECT_AND_PUNCHY);
        assertThat(script.confidenceNotes().overallConfidence().name()).isEqualToIgnoringCase("high");

        // Derived fields must be genuinely computed, not asserted by the model.
        StringBuilder expected = new StringBuilder();
        expected.append(script.hook()).append(' ').append(script.introLine());
        script.mainScript().forEach(beat -> expected.append(' ').append(beat.spokenLine()));
        script.codeNarration().spokenLines().forEach(line -> expected.append(' ').append(line));
        expected.append(' ').append(script.recapLine()).append(' ').append(script.ctaLine());
        assertThat(script.fullSpokenScript()).isEqualTo(expected.toString());

        int actualWordCount = script.fullSpokenScript().trim().split("\\s+").length;
        assertThat(script.wordCount()).isEqualTo(actualWordCount);

        double expectedDuration = Math.round((script.wordCount() / 2.5) * 10.0) / 10.0;
        assertThat(script.estimatedDurationSeconds()).isEqualTo(expectedDuration);

        String reconstructed = String.join(" ",
                script.subtitleSegments().stream().map(Script.SubtitleSegment::text).toList());
        assertThat(reconstructed).isEqualTo(script.fullSpokenScript());

        // hookType/teachingStyle are echoed verbatim from the strategy, never chosen by the model.
        assertThat(script.hookType()).isEqualTo(strategy.hookType());
        assertThat(script.teachingStyle()).isEqualTo(strategy.teachingStyle());
        assertThat(script.basedOnLessonVersion()).isEqualTo(lesson.lessonVersion());
        assertThat(script.basedOnContentDirectorVersion()).isEqualTo(strategy.contentDirectorVersion());
    }

    @Test
    void fallsBackToHeuristicScriptWhenTheClientReportsMissingConfiguration() {
        VertexAiConfig config = config("", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicScriptAndSkipsTheClientWhenScriptModelIsBlank() {
        VertexAiConfig config = config("demo-project", "");

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-heuristic");
        verifyNoInteractions(vertexAiClient);
    }

    @Test
    void fallsBackToHeuristicScriptWhenTheClientThrows() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("simulated network failure"));

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicScriptWhenTheResponseIsNotValidJson() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "gemini-2.0-flash-001", "STOP"));

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicScriptWhenTheResponseIsMissingRequiredFields() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String incompleteJson = """
                {
                  "hook": "Watch what happens when this loop forgets to update itself.",
                  "main_script": []
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(incompleteJson, "gemini-2.0-flash-001", "STOP"));

        Script script = service(config).generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.scriptVersion()).isEqualTo("1.0.0-heuristic");
    }
}
