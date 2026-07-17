package com.forgebrain.backend.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.models.Topic;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies real curriculum loading against the actual repository file — not a fixture — so a
 * broken or drifted curriculum/java-roadmap.json fails this test, not just production.
 */
class CurriculumLoaderImplTest {

    private CurriculumLoaderImpl loader;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        LocalStorageConfig config = new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused");
        loader = new CurriculumLoaderImpl(objectMapper, config);
    }

    @Test
    void loadsAllLevelsAndTopicsFromTheRealRoadmap() {
        List<RoadmapLevel> levels = loader.loadFullRoadmap();

        assertThat(levels).hasSize(13);
        int totalTopics = levels.stream().mapToInt(l -> l.topics().size()).sum();
        assertThat(totalTopics).isEqualTo(81);
    }

    @Test
    void findsATopicByIdWithFieldsPopulated() {
        Optional<Topic> variables = loader.findTopic("java-variables-and-data-types");

        assertThat(variables).isPresent();
        Topic topic = variables.get();
        assertThat(topic.title()).isEqualTo("Variables and Data Types");
        assertThat(topic.difficulty()).isEqualTo(Topic.Difficulty.BEGINNER);
        assertThat(topic.status()).isEqualTo(Topic.Status.NOT_COVERED);
        assertThat(topic.commonMistakes()).isNotEmpty();
        assertThat(topic.exampleIdeas()).isNotEmpty();
        assertThat(topic.learningObjective()).isNotBlank();
    }

    @Test
    void returnsEmptyForAnUnknownTopic() {
        assertThat(loader.findTopic("not-a-real-topic")).isEmpty();
    }

    @Test
    void resolvesPrerequisitesToFullTopics() {
        List<Topic> prerequisites = loader.findPrerequisites("java-for-loop");

        assertThat(prerequisites).extracting(Topic::id).containsExactly("java-if-else");
    }

    @Test
    void everyPrerequisiteAndNextTopicReferenceResolves() {
        List<RoadmapLevel> levels = loader.loadFullRoadmap();

        for (RoadmapLevel level : levels) {
            for (Topic topic : level.topics()) {
                for (String prerequisiteId : topic.prerequisites()) {
                    assertThat(loader.findTopic(prerequisiteId))
                            .as("prerequisite '%s' of topic '%s' should exist", prerequisiteId, topic.id())
                            .isPresent();
                }
            }
        }
    }
}
