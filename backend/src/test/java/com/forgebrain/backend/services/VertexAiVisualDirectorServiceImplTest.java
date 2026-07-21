package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VisualPlan;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link VertexAiVisualDirectorServiceImpl} against a mocked {@link VertexAiClient},
 * wired through a real {@link AiGatewayImpl} (retries/caching disabled) — no real network call is
 * made. Mirrors {@link VertexAiContentDirectorServiceImplTest}'s structure.
 */
class VertexAiVisualDirectorServiceImplTest {

    private ObjectMapper objectMapper;
    private Script script;
    private Storyboard storyboard;
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
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
        vertexAiClient = mock(VertexAiClient.class);
    }

    private VertexAiVisualDirectorServiceImpl service(VertexAiConfig config) {
        AiGateway aiGateway = new AiGatewayImpl(vertexAiClient, new PromptRegistryImpl(config),
                new AiGatewayConfig(0, 0, 1.0, 5000, false), new InMemoryAiResponseCache(),
                new InMemoryPromptMetricsRecorder(), objectMapper);
        return new VertexAiVisualDirectorServiceImpl(aiGateway);
    }

    private static VertexAiConfig config(String projectId, String visualDirectorModel) {
        return new VertexAiConfig(projectId, "us-central1", "", "", "", "",
                0.4, 2048, "application/json", 0.4, 2048, "application/json", 0.4, 2048, "application/json",
                visualDirectorModel, 0.4, 4096, "application/json");
    }

    private String validSceneJson(String scenePrimitive, String composition, String codeBlockHint) {
        return "{"
                + "\"scene_primitive\": \"" + scenePrimitive + "\","
                + "\"hook_intent\": \"\","
                + "\"visual_goal\": \"Show the concept clearly.\","
                + "\"composition\": \"" + composition + "\","
                + "\"camera_motion\": \"slow push in\","
                + "\"background_style\": \"dark gradient\","
                + "\"foreground_elements\": [\"loop\", \"counter\"],"
                + "\"text_overlay\": \"bold centered\","
                + "\"code_block_hint\": " + (codeBlockHint == null ? "null" : "\"" + codeBlockHint + "\"") + ","
                + "\"diagram_type\": null,"
                + "\"image_prompt\": \"A clean vector illustration of a loop.\","
                + "\"motion_cue\": \"pulse on the counter\","
                + "\"transition_in\": \"QUICK_FADE\","
                + "\"transition_out\": \"HARD_CUT\""
                + "}";
    }

    private String validResponseJsonFor(Storyboard storyboard) {
        StringBuilder scenes = new StringBuilder();
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            if (i > 0) {
                scenes.append(',');
            }
            scenes.append(validSceneJson("WALKTHROUGH", "CENTERED_CARD", null));
        }
        return "{\"thumbnail_brief\": \"Bold hook over accent background\", \"scenes\": [" + scenes + "]}";
    }

    @Test
    void parsesAValidVertexAiResponseIntoAVisualPlanZippedToStoryboardScenesByIndex() {
        VertexAiConfig config = config("demo-project", "gemini-2.5-pro");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(validResponseJsonFor(storyboard), "gemini-2.5-pro", "STOP"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-vertex-ai");
        assertThat(plan.thumbnailBrief()).isEqualTo("Bold hook over accent background");
        assertThat(plan.scenes()).hasSize(storyboard.scenes().size());
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            assertThat(plan.scenes().get(i).sceneId()).isEqualTo(storyboard.scenes().get(i).sceneId());
            assertThat(plan.scenes().get(i).durationSeconds()).isEqualTo(storyboard.scenes().get(i).duration());
            assertThat(plan.scenes().get(i).scenePrimitive()).isEqualTo(VisualPlan.ScenePrimitive.WALKTHROUGH);
            assertThat(plan.scenes().get(i).transitionIn()).isEqualTo(com.forgebrain.backend.models.Scene.TransitionStyle.QUICK_FADE);
        }
        assertThat(plan.topicId()).isEqualTo(storyboard.topicId());
        assertThat(plan.basedOnStoryboardVersion()).isEqualTo(storyboard.storyboardVersion());
    }

    @Test
    void fallsBackToHeuristicPlanWhenTheClientReportsMissingConfiguration() {
        VertexAiConfig config = config("", "gemini-2.5-pro");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-heuristic");
        assertThat(plan.scenes()).hasSize(storyboard.scenes().size());
    }

    @Test
    void fallsBackToHeuristicPlanWhenTheClientThrows() {
        VertexAiConfig config = config("demo-project", "gemini-2.5-pro");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("simulated network failure"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicPlanWhenTheResponseIsNotValidJson() {
        VertexAiConfig config = config("demo-project", "gemini-2.5-pro");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "gemini-2.5-pro", "STOP"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicPlanWhenTheScenesArrayDoesNotMatchTheStoryboardsSceneCount() {
        VertexAiConfig config = config("demo-project", "gemini-2.5-pro");
        String mismatchedJson = "{\"thumbnail_brief\": \"Bold hook\", \"scenes\": ["
                + validSceneJson("HOOK", "FULL_BLEED", null) + "]}";
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(mismatchedJson, "gemini-2.5-pro", "STOP"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-heuristic");
        assertThat(storyboard.scenes().size()).isGreaterThan(1);
    }

    @Test
    void fallsBackToHeuristicPlanWhenAResponseSceneIsMissingRequiredFields() {
        VertexAiConfig config = config("demo-project", "gemini-2.5-pro");
        StringBuilder scenes = new StringBuilder();
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            if (i > 0) {
                scenes.append(',');
            }
            scenes.append("{\"scene_primitive\": null, \"hook_intent\": \"\"}");
        }
        String incompleteJson = "{\"thumbnail_brief\": \"Bold hook\", \"scenes\": [" + scenes + "]}";
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(incompleteJson, "gemini-2.5-pro", "STOP"));

        VisualPlan plan = service(config).generateVisualPlan(script, storyboard);

        assertThat(plan.visualPlanVersion()).isEqualTo("1.0.0-heuristic");
    }
}
