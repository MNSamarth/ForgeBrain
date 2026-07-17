package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LessonServiceImplTest {

    private LessonServiceImpl lessonService;
    private ResearchResult research;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused"));
        var researchService = new ResearchServiceImpl(curriculumLoader);
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        research = researchService.research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        lessonService = new LessonServiceImpl();
    }

    @Test
    void narrowsResearchIntoExactlyOneOfEverything() {
        Lesson lesson = lessonService.generateLesson(research, null, null);

        assertThat(lesson.coreExample()).isNotNull();
        assertThat(lesson.analogy()).isEqualTo(research.simpleAnalogy());
        assertThat(lesson.beginnerTakeaway()).isNotBlank();
        assertThat(lesson.retentionHook()).isNotBlank();
        assertThat(lesson.keyPoints()).isNotEmpty();
        assertThat(lesson.basedOnResearchVersion()).isEqualTo(research.researchVersion());
    }

    @Test
    void picksProblemFirstWhenResearchHasMisconceptionsAndNoStyleWasRequested() {
        Lesson lesson = lessonService.generateLesson(research, null, null);

        assertThat(lesson.teachingStyle()).isEqualTo(Lesson.TeachingStyle.PROBLEM_FIRST);
        assertThat(lesson.confidenceNotes().flaggedUncertainties())
                .anyMatch(note -> note.contains("teaching_style"));
    }

    @Test
    void respectsAnExplicitlyRequestedTeachingStyle() {
        Lesson lesson = lessonService.generateLesson(research, null, Lesson.TeachingStyle.STORY_DRIVEN);

        assertThat(lesson.teachingStyle()).isEqualTo(Lesson.TeachingStyle.STORY_DRIVEN);
        assertThat(lesson.confidenceNotes().flaggedUncertainties())
                .noneMatch(note -> note.contains("teaching_style"));
    }
}
