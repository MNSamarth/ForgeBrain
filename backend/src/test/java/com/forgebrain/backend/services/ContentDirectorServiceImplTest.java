package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentDirectorServiceImplTest {

    private ContentDirectorServiceImpl contentDirectorService;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig(
                "../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader).research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        lesson = new LessonServiceImpl().generateLesson(research, null, null);
        contentDirectorService = new ContentDirectorServiceImpl();
    }

    @Test
    void producesADeterministicStrategyForTheSameLesson() {
        ContentStrategy first = contentDirectorService.decideStrategy(lesson, null);
        ContentStrategy second = contentDirectorService.decideStrategy(lesson, null);

        assertThat(first.hookType()).isEqualTo(second.hookType());
        assertThat(first.teachingStyle()).isEqualTo(second.teachingStyle());
        assertThat(first.emotionalGoal()).isEqualTo(second.emotionalGoal());
    }

    @Test
    void scenePacingSumsToTheTargetDuration() {
        ContentStrategy strategy = contentDirectorService.decideStrategy(lesson, null);

        double total = strategy.scenePacing().stream().mapToDouble(ContentStrategy.ScenePacingEntry::durationSeconds).sum();
        assertThat(total).isCloseTo(lesson.targetDurationSeconds(), org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void revisionShiftsEmotionalGoalToSurpriseAndFlagsWhy() {
        MemoryState.TopicRecord needsRevisit = new MemoryState.TopicRecord(
                lesson.topicId(), lesson.topicTitle(), Topic.Status.NEEDS_REVISIT, Topic.Difficulty.BEGINNER, 1,
                Instant.now(), Instant.now(), 0, 0.4, 0.3, null, MemoryState.Priority.HIGH, null, List.of(), "weak hook");

        ContentStrategy strategy = contentDirectorService.decideStrategy(lesson, needsRevisit);

        assertThat(strategy.emotionalGoal()).isEqualTo(ContentStrategy.EmotionalGoal.SURPRISE);
        assertThat(strategy.confidenceNotes().flaggedUncertainties()).anyMatch(note -> note.contains("revision"));
    }
}
