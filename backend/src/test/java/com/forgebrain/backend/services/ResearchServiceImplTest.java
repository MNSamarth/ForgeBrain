package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResearchServiceImplTest {

    private ResearchServiceImpl researchService;
    private Topic variablesTopic;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused"));
        researchService = new ResearchServiceImpl(curriculumLoader);
        variablesTopic = curriculumLoader.findTopic("java-variables-and-data-types").orElseThrow();
    }

    @Test
    void producesASchemaValidBriefGroundedInRealCurriculumData() {
        ResearchResult result = researchService.research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.topicId()).isEqualTo("java-variables-and-data-types");
        assertThat(result.learningObjective()).isEqualTo(variablesTopic.learningObjective());
        assertThat(result.commonMisconceptions()).isEqualTo(variablesTopic.commonMistakes());
        assertThat(result.codeExampleIdeas()).isEqualTo(variablesTopic.exampleIdeas());
        assertThat(result.coreConcepts()).isNotEmpty();
        assertThat(result.simpleAnalogy()).isNotBlank();
        assertThat(result.beginnerExplanation()).isNotBlank();
        assertThat(result.safetyNotes()).isNotEmpty();
        assertThat(result.prerequisites()).hasSize(variablesTopic.prerequisites().size());
        assertThat(result.confidenceNotes().overallConfidence()).isNotNull();
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void resolvesPrerequisiteTitlesNotJustIds() {
        ResearchResult result = researchService.research(
                "java-variables-and-data-types", variablesTopic, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(result.prerequisites()).extracting(ResearchResult.TopicRef::title)
                .doesNotContain("java-jdk-jre-jvm") // should be a title, not the raw id
                .allMatch(title -> !title.startsWith("java-"));
    }

    @Test
    void flagsRevisionContextWhenTopicMemoryShowsNeedsRevisit() {
        MemoryState.TopicRecord priorAttempt = new MemoryState.TopicRecord(
                "java-arrays-basics", "Arrays Basics", Topic.Status.NEEDS_REVISIT, Topic.Difficulty.BEGINNER, 1,
                Instant.now(), Instant.now(), 0, 0.41, 0.33, null, MemoryState.Priority.HIGH, null,
                List.of(), "pacing too slow before the crash demo");

        ResearchResult result = researchService.research(
                "java-arrays-basics", variablesTopic, Topic.Difficulty.BEGINNER, 45, priorAttempt);

        assertThat(result.confidenceNotes().flaggedUncertainties())
                .anyMatch(note -> note.contains("revision") && note.contains("0.41"));
    }
}
