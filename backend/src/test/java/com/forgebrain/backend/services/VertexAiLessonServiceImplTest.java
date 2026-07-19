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
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link VertexAiLessonServiceImpl} against a mocked {@link VertexAiClient} — no real
 * network call is made. Covers: successful Vertex AI generation is parsed and assembled
 * correctly, and every way the client can be "unavailable" (missing config, thrown exception,
 * malformed JSON, incomplete JSON) correctly falls back to the heuristic result.
 */
class VertexAiLessonServiceImplTest {

    private ObjectMapper objectMapper;
    private ResearchResult research;
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
                new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused"));
        var researchService = new ResearchServiceImpl(curriculumLoader);
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        research = researchService.research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        vertexAiClient = mock(VertexAiClient.class);
    }

    private VertexAiLessonServiceImpl service(VertexAiConfig config) {
        return new VertexAiLessonServiceImpl(vertexAiClient, config, objectMapper);
    }

    private static VertexAiConfig config(String projectId, String lessonModel) {
        return new VertexAiConfig(projectId, "us-central1", "", lessonModel, "", 0.4, 2048, "application/json");
    }

    @Test
    void parsesAValidVertexAiResponseIntoALesson() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String json = """
                {
                  "lesson_objective": "Understand how a for loop repeats a block a fixed number of times.",
                  "lesson_summary": "This lesson walks through a for loop counting from zero, tying each part of the header to what it controls.",
                  "key_points": ["The header has init, condition, and update", "The condition is checked before every iteration", "The loop variable is scoped to the loop"],
                  "step_by_step_explanation": ["Start with the loop header", "Explain the condition check", "Show the body executing", "Show the update step"],
                  "core_example": {
                    "description": "A for loop printing numbers 0 through 4",
                    "code_sketch": "for (int i = 0; i < 5; i++) { System.out.println(i); }",
                    "focus_note": "Highlight the condition i < 5 as the loop runs"
                  },
                  "analogy": "A for loop is like a countdown timer that stops itself once it hits zero.",
                  "common_mistakes": ["Forgetting to update the loop variable causes an infinite loop"],
                  "what_to_avoid_saying": ["Never imply a for loop always counts upward"],
                  "beginner_takeaway": "A for loop repeats its body until its condition becomes false.",
                  "retention_hook": "Watch what happens when a loop forgets to update its own counter.",
                  "visual_notes": ["Highlight the loop variable each time it changes"],
                  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [] }
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(json, "gemini-2.0-flash-001", "STOP"));

        Lesson lesson = service(config).generateLesson(research, null, Lesson.TeachingStyle.DIRECT_EXPLANATION);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-vertex-ai");
        assertThat(lesson.lessonObjective()).contains("for loop repeats a block");
        assertThat(lesson.keyPoints()).hasSize(3);
        assertThat(lesson.stepByStepExplanation()).hasSize(4);
        assertThat(lesson.coreExample().codeSketch()).contains("for (int i = 0");
        assertThat(lesson.analogy()).contains("countdown timer");
        assertThat(lesson.commonMistakes()).containsExactly("Forgetting to update the loop variable causes an infinite loop");
        assertThat(lesson.whatToAvoidSaying()).containsExactly("Never imply a for loop always counts upward");
        assertThat(lesson.beginnerTakeaway()).contains("repeats its body");
        assertThat(lesson.retentionHook()).contains("forgets to update");
        assertThat(lesson.visualNotes()).containsExactly("Highlight the loop variable each time it changes");
        assertThat(lesson.confidenceNotes().overallConfidence().name()).isEqualToIgnoringCase("high");

        // Deterministic fields still come from the research brief / caller, not the model.
        assertThat(lesson.topicId()).isEqualTo(research.topicId());
        assertThat(lesson.topicTitle()).isEqualTo(research.topicTitle());
        assertThat(lesson.audienceLevel()).isEqualTo(research.audienceLevel());
        assertThat(lesson.targetDurationSeconds()).isEqualTo(research.targetReelLengthSeconds());
        assertThat(lesson.teachingStyle()).isEqualTo(Lesson.TeachingStyle.DIRECT_EXPLANATION);
        assertThat(lesson.basedOnResearchVersion()).isEqualTo(research.researchVersion());
    }

    @Test
    void fallsBackToHeuristicLessonWhenTheClientReportsMissingConfiguration() {
        VertexAiConfig config = config("", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        Lesson lesson = service(config).generateLesson(research, null, null);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicLessonWhenLessonModelIsBlank() {
        VertexAiConfig config = config("demo-project", "");

        Lesson lesson = service(config).generateLesson(research, null, null);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-heuristic");
        verifyNoInteractions(vertexAiClient);
    }

    @Test
    void fallsBackToHeuristicLessonWhenTheClientThrows() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("simulated network failure"));

        Lesson lesson = service(config).generateLesson(research, null, null);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicLessonWhenTheResponseIsNotValidJson() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "gemini-2.0-flash-001", "STOP"));

        Lesson lesson = service(config).generateLesson(research, null, null);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-heuristic");
    }

    @Test
    void fallsBackToHeuristicLessonWhenTheResponseIsMissingRequiredFields() {
        VertexAiConfig config = config("demo-project", "gemini-2.0-flash-001");
        String incompleteJson = """
                {
                  "lesson_objective": "Understand for loops.",
                  "key_points": []
                }
                """;
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse(incompleteJson, "gemini-2.0-flash-001", "STOP"));

        Lesson lesson = service(config).generateLesson(research, null, null);

        assertThat(lesson.lessonVersion()).isEqualTo("1.0.0-heuristic");
    }
}
