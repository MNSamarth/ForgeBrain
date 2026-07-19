package com.forgebrain.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.exceptions.InvalidTopicException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The end-to-end proof this session's mission asks for: a full Spring context, real
 * dependency injection, running the actual pipeline from topic selection through storyboard
 * against the real curriculum, and asserting the result is internally consistent — not a set
 * of isolated unit tests that happen to compile, but the whole thing wired together and run.
 *
 * <p>{@code @DynamicPropertySource} redirects memory and pipeline-output storage to a JUnit
 * temp directory so this test never touches {@code backend/data/}. {@code @DirtiesContext}
 * forces a fresh Spring context (and therefore a fresh, un-cached {@code MemoryServiceImpl}
 * singleton) after each test method — without it, two {@code @Test} methods in this class
 * would share one in-memory-cached {@link com.forgebrain.backend.models.MemoryState} across
 * JUnit's undefined method execution order, exactly the kind of cross-test leakage that
 * surfaced as a real, confusing failure while writing this test and is worth guarding against
 * explicitly rather than just working around once.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PipelineOrchestratorImplTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStoragePaths(DynamicPropertyRegistry registry) {
        registry.add("forgebrain.local-storage.memory-state-path",
                () -> new File(tempDir.toFile(), "memory-state.json").getAbsolutePath());
        registry.add("forgebrain.local-storage.pipeline-output-directory",
                () -> new File(tempDir.toFile(), "output").getAbsolutePath());
        registry.add("forgebrain.local-storage.execution-report-directory",
                () -> new File(tempDir.toFile(), "reports").getAbsolutePath());
    }

    @Autowired
    private PipelineOrchestrator orchestrator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runsTheFullPipelineEndToEndAndProducesAConsistentResult() throws IOException {
        PipelineResult result = orchestrator.runFullPipeline();

        // On a fresh memory state, java-what-is-java is the only topic with no prerequisites.
        assertThat(result.topicId()).isEqualTo("java-what-is-java");

        assertThat(result.topicSelectionDecision().selectedTopicId()).isEqualTo(result.topicId());
        assertThat(result.researchResult().topicId()).isEqualTo(result.topicId());
        assertThat(result.lesson().topicId()).isEqualTo(result.topicId());
        assertThat(result.contentStrategy().topicId()).isEqualTo(result.topicId());
        assertThat(result.script().topicId()).isEqualTo(result.topicId());
        assertThat(result.storyboard().topicId()).isEqualTo(result.topicId());

        // Traceability chain: each stage names the exact version of the stage before it.
        assertThat(result.lesson().basedOnResearchVersion()).isEqualTo(result.researchResult().researchVersion());
        assertThat(result.contentStrategy().basedOnLessonVersion()).isEqualTo(result.lesson().lessonVersion());
        assertThat(result.script().basedOnLessonVersion()).isEqualTo(result.lesson().lessonVersion());
        assertThat(result.script().basedOnContentDirectorVersion()).isEqualTo(result.contentStrategy().contentDirectorVersion());
        assertThat(result.storyboard().basedOnScriptVersion()).isEqualTo(result.script().scriptVersion());

        // Strategy fidelity: the script must echo the exact hook/teaching style it was given.
        assertThat(result.script().hookType()).isEqualTo(result.contentStrategy().hookType());
        assertThat(result.script().teachingStyle()).isEqualTo(result.contentStrategy().teachingStyle());

        // The result was actually saved to disk, not just returned in memory.
        File outputDir = new File(tempDir.toFile(), "output");
        assertThat(outputDir).isDirectory();
        assertThat(outputDir.listFiles()).isNotEmpty();

        // A full execution report is generated with per-stage detail.
        File reportsDir = new File(tempDir.toFile(), "reports");
        assertThat(reportsDir).isDirectory();
        File latestReportFile = Arrays.stream(reportsDir.listFiles())
                .max(Comparator.comparing(File::getName))
                .orElseThrow();
        PipelineExecutionReport latestReport = objectMapper.readValue(latestReportFile, PipelineExecutionReport.class);
        assertThat(latestReport.finalStatus()).isEqualTo("SUCCESS");
        assertThat(latestReport.selectedTopic()).isEqualTo(result.topicId());
        assertThat(latestReport.stageResults()).extracting(StageExecutionSummary::stageName)
                .containsExactly("TOPIC_SELECTION", "RESEARCH", "LESSON", "CONTENT_DIRECTOR", "SCRIPT", "STORYBOARD");
        assertThat(latestReport.stageResults()).allMatch(StageExecutionSummary::success);
        assertThat(latestReport.errors()).isEmpty();

        // Memory now reflects that this topic is in progress, closing the loop back to memory.
        File memoryFile = new File(tempDir.toFile(), "memory-state.json");
        assertThat(memoryFile).exists();

        // A second run, immediately after, correctly refuses rather than reselecting the same
        // in-progress topic or fabricating progress: nothing in this pipeline slice ever marks
        // a topic POSTED (that's Renderer/Reviewer/Publishing, not built yet — see
        // NEXT_EXECUTION.md), so no other topic's prerequisites become satisfied either. An
        // honest failure here is the correct behavior, not a bug.
        assertThrows(InvalidTopicException.class, orchestrator::runFullPipeline);

        List<PipelineExecutionReport> reports = Arrays.stream(reportsDir.listFiles())
                .map(file -> {
                    try {
                        return objectMapper.readValue(file, PipelineExecutionReport.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        assertThat(reports).extracting(PipelineExecutionReport::finalStatus)
                .contains("SUCCESS", "FAILED");
        assertThat(reports.stream().filter(report -> "FAILED".equals(report.finalStatus())).findAny().orElseThrow()
                .stageResults()).anyMatch(summary -> !summary.success());
    }
}
