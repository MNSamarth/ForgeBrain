package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptServiceImplTest {

    private ScriptServiceImpl scriptService;
    private Lesson lesson;
    private ContentStrategy strategy;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader).research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        lesson = new LessonServiceImpl().generateLesson(research, null, null);
        strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        scriptService = new ScriptServiceImpl();
    }

    @Test
    void fullSpokenScriptIsExactlyTheConcatenationOfEveryStructuredField() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        StringBuilder expected = new StringBuilder();
        expected.append(script.hook()).append(' ').append(script.introLine());
        script.mainScript().forEach(beat -> expected.append(' ').append(beat.spokenLine()));
        script.codeNarration().spokenLines().forEach(line -> expected.append(' ').append(line));
        expected.append(' ').append(script.recapLine()).append(' ').append(script.ctaLine());

        assertThat(script.fullSpokenScript()).isEqualTo(expected.toString());
    }

    @Test
    void wordCountMatchesTheActualWordCountOfTheFullScript() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        int actualWordCount = script.fullSpokenScript().trim().split("\\s+").length;
        assertThat(script.wordCount()).isEqualTo(actualWordCount);
    }

    @Test
    void estimatedDurationFollowsThe2Point5WordsPerSecondFormula() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        double expectedDuration = Math.round((script.wordCount() / 2.5) * 10.0) / 10.0;
        assertThat(script.estimatedDurationSeconds()).isEqualTo(expectedDuration);
    }

    @Test
    void subtitleSegmentsConcatenateBackToTheFullScript() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        String reconstructed = String.join(" ", script.subtitleSegments().stream().map(Script.SubtitleSegment::text).toList());
        assertThat(reconstructed).isEqualTo(script.fullSpokenScript());
    }

    @Test
    void echoesHookTypeAndTeachingStyleExactlyFromTheStrategy() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.hookType()).isEqualTo(strategy.hookType());
        assertThat(script.teachingStyle()).isEqualTo(strategy.teachingStyle());
    }

    @Test
    void scriptStaysShortForABeginnerReel() {
        Script script = scriptService.generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);

        assertThat(script.wordCount()).isLessThan(150);
    }
}
