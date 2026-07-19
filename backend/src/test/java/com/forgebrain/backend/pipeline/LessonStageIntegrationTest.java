package com.forgebrain.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Lesson;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the pipeline still runs end to end with {@link
 * com.forgebrain.backend.services.VertexAiLessonServiceImpl} as the real, DI-wired {@code
 * LessonService} bean, in a full Spring context — not a mocked unit test. This sandbox has no
 * GCP credentials, so the lesson stage exercises its fallback path for real, exactly as {@code
 * PipelineOrchestratorImplTest} already proves for the research stage; this test asserts the
 * resulting {@link Lesson} is nonetheless complete and internally consistent regardless of
 * which path (Vertex AI or heuristic) actually produced it, and that its version tag is one of
 * the two the pipeline can legitimately produce.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LessonStageIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStoragePaths(DynamicPropertyRegistry registry) {
        registry.add("forgebrain.local-storage.memory-state-path",
                () -> new File(tempDir.toFile(), "memory-state.json").getAbsolutePath());
        registry.add("forgebrain.local-storage.pipeline-output-directory",
                () -> new File(tempDir.toFile(), "output").getAbsolutePath());
    }

    @Autowired
    private PipelineOrchestrator orchestrator;

    @Test
    void pipelineProducesACompleteLessonThroughTheVertexAiBackedLessonService() {
        PipelineResult result = orchestrator.runFullPipeline();

        Lesson lesson = result.lesson();
        assertThat(lesson).isNotNull();
        assertThat(lesson.lessonVersion()).isIn("1.0.0-vertex-ai", "1.0.0-heuristic");
        assertThat(lesson.topicId()).isEqualTo(result.topicId());
        assertThat(lesson.basedOnResearchVersion()).isEqualTo(result.researchResult().researchVersion());

        // "One of Everything" fields must be present regardless of which path produced them.
        assertThat(lesson.lessonObjective()).isNotBlank();
        assertThat(lesson.lessonSummary()).isNotBlank();
        assertThat(lesson.keyPoints()).isNotEmpty();
        assertThat(lesson.stepByStepExplanation()).isNotEmpty();
        assertThat(lesson.coreExample()).isNotNull();
        assertThat(lesson.coreExample().description()).isNotBlank();
        assertThat(lesson.analogy()).isNotBlank();
        assertThat(lesson.commonMistakes()).isNotEmpty();
        assertThat(lesson.beginnerTakeaway()).isNotBlank();
        assertThat(lesson.retentionHook()).isNotBlank();
        assertThat(lesson.visualNotes()).isNotEmpty();
        assertThat(lesson.confidenceNotes().overallConfidence()).isNotNull();

        // Downstream stages still consumed this lesson successfully — the pipeline shape holds.
        assertThat(result.contentStrategy().basedOnLessonVersion()).isEqualTo(lesson.lessonVersion());
        assertThat(result.script().basedOnLessonVersion()).isEqualTo(lesson.lessonVersion());
        assertThat(result.storyboard()).isNotNull();
    }
}
