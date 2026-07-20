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
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link VertexAiContentDirectorServiceImpl} against a mocked {@link VertexAiClient},
 * wired through a real {@link AiGatewayImpl} (retries/caching disabled so each scenario below is
 * a single deterministic attempt) — no real network call is made. Covers: successful generation
 * is parsed and assembled correctly, and every way the gateway can be "unavailable" (missing
 * config, thrown exception, malformed JSON, incomplete JSON) correctly falls back to the
 * heuristic strategy.
 */
class VertexAiContentDirectorServiceImplTest {

    private ObjectMapper objectMapper;
    private Lesson lesson;
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
        vertexAiClient = mock(VertexAiClient.class);
    }

    private VertexAiContentDirectorServiceImpl service(VertexAiConfig config) {
        AiGateway aiGateway = new AiGatewayImpl(vertexAiClient, new PromptRegistryImpl(config),
                new AiGatewayConfig(0, 0, 1.0, 5000, false), new InMemoryAiResponseCache(),
                new InMemoryPromptMetricsRecorder(), objectMapper);
        return new VertexAiContentDirectorServiceImpl(aiGateway);
    }

    private static VertexAiConfig config(String projectId, String contentDirectorModel) {
        return new VertexAiConfig(projectId, "us-central1", "", "", "", contentDirectorModel,
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json");
    }

    @Test
    void parsesAValidVertexAiResponseIntoAContentStrategy() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String json = """
                {
                  "hook_type": "COMMON_BUG",
                  "hook_reason": "The lesson's common_mistakes entry about forgetting the update step is the strongest hook.",
                  "teaching_style": "CODE_FIRST",
                  "teaching_style_reason": "The lesson is problem-first and the core_example is the clearest way in.",
                  "emotional_goal": "SURPRISE",
                  "emotional_goal_reason": "A common-bug hook resolved cleanly lands as surprise.",
                  "pacing": "FAST",
                  "pacing_reason": "Several short step_by_step beats suit a quick cut pace.",
                  "scene_pacing": [
                    { "scene": "cold-open infinite loop", "duration_seconds": 6 },
                    { "scene": "explain the header", "duration_seconds": 14 },
                    { "scene": "fix and recap", "duration_seconds": 12 }
                  ],
                  "visual_style": "HIGHLIGHT_ANIMATION",
                  "supporting_visuals": ["Highlight the loop variable each time it changes"],
                  "visual_style_reason": "Matches the core_example's focus_note on the condition check.",
                  "code_style": "BUG_EXAMPLE",
                  "code_style_reason": "Frames the lesson's own core_example as the bug that gets fixed.",
                  "cta_style": "TRY_THIS_YOURSELF",
                  "cta_reason": "A bug fix invites the viewer to try triggering it themselves.",
                  "retention_goal": "The viewer stays to see why the loop never stops.",
                  "estimated_watch_time": 34,
                  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [] }
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(json, "gemini-2.0-flash-001", "STOP"));

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-vertex-ai");
        assertThat(strategy.hookType()).isEqualTo(ContentStrategy.HookType.COMMON_BUG);
        assertThat(strategy.teachingStyle()).isEqualTo(ContentStrategy.TeachingStyle.CODE_FIRST);
        assertThat(strategy.emotionalGoal()).isEqualTo(ContentStrategy.EmotionalGoal.SURPRISE);
        assertThat(strategy.pacing()).isEqualTo(ContentStrategy.Pacing.FAST);
        assertThat(strategy.scenePacing()).hasSize(3);
        assertThat(strategy.visualStyle()).isEqualTo(ContentStrategy.VisualStyle.HIGHLIGHT_ANIMATION);
        assertThat(strategy.codeStyle()).isEqualTo(ContentStrategy.CodeStyle.BUG_EXAMPLE);
        assertThat(strategy.ctaStyle()).isEqualTo(ContentStrategy.CtaStyle.TRY_THIS_YOURSELF);
        assertThat(strategy.estimatedWatchTime()).isEqualTo(34);
        assertThat(strategy.confidenceNotes().overallConfidence().name()).isEqualToIgnoringCase("high");

        // Deterministic fields still come from the lesson, not the model.
        assertThat(strategy.topicId()).isEqualTo(lesson.topicId());
        assertThat(strategy.topicTitle()).isEqualTo(lesson.topicTitle());
        assertThat(strategy.targetDurationSeconds()).isEqualTo(lesson.targetDurationSeconds());
        assertThat(strategy.basedOnLessonVersion()).isEqualTo(lesson.lessonVersion());
    }

    @Test
    void fallsBackToHeuristicStrategyWhenTheClientReportsMissingConfiguration() {
        VertexAiConfig config = config("", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicStrategyAndSkipsTheClientWhenContentDirectorModelIsBlank() {
        VertexAiConfig config = config("demo-project", "");

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-heuristic");
        verifyNoInteractions(vertexAiClient);
    }

    @Test
    void fallsBackToHeuristicStrategyWhenTheClientThrows() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("simulated network failure"));

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicStrategyWhenTheResponseIsNotValidJson() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "gemini-2.0-flash-001", "STOP"));

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicStrategyWhenTheResponseIsMissingRequiredFields() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String incompleteJson = """
                {
                  "hook_type": "COMMON_BUG",
                  "hook_reason": "Only a hook was provided."
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(incompleteJson, "gemini-2.0-flash-001", "STOP"));

        ContentStrategy strategy = service(config).decideStrategy(lesson, null);

        assertThat(strategy.contentDirectorVersion()).isEqualTo("1.0.0-heuristic");
    }
}
