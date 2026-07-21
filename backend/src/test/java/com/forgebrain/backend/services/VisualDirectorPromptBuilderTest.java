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
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisualDirectorPromptBuilderTest {

    private Script script;
    private Storyboard storyboard;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper,
                new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
    }

    @Test
    void promptNamesTheExactSceneCountAndRequiresAMatchingScenesArray() {
        String prompt = VisualDirectorPromptBuilder.build(script, storyboard);

        assertThat(prompt).contains("array of exactly " + storyboard.sceneCount() + " objects");
        assertThat(prompt).contains("The scenes array length MUST equal the scene count above");
    }

    @Test
    void promptListsEveryStoryboardSceneInOrderWithItsTypeAndPurpose() {
        String prompt = VisualDirectorPromptBuilder.build(script, storyboard);

        for (int i = 0; i < storyboard.scenes().size(); i++) {
            var scene = storyboard.scenes().get(i);
            assertThat(prompt).contains((i + 1) + ". [" + scene.sceneType() + "] purpose: " + scene.purpose());
        }
    }

    @Test
    void promptIncludesTheHookLineAndDeclaresEveryRequiredJsonField() {
        String prompt = VisualDirectorPromptBuilder.build(script, storyboard);

        assertThat(prompt).contains("Hook line: " + script.hook());
        assertThat(prompt).contains("\"thumbnail_brief\"");
        assertThat(prompt).contains("\"scene_primitive\"");
        assertThat(prompt).contains("\"composition\"");
        assertThat(prompt).contains("\"image_prompt\"");
        assertThat(prompt).contains("\"transition_in\"");
        assertThat(prompt).contains("\"transition_out\"");
    }

    @Test
    void promptDoesNotAskForDurationOrSceneIdSinceTimingIsAlreadyFinal() {
        String prompt = VisualDirectorPromptBuilder.build(script, storyboard);

        assertThat(prompt).doesNotContain("\"duration\"");
        assertThat(prompt).doesNotContain("\"scene_id\"");
    }
}
