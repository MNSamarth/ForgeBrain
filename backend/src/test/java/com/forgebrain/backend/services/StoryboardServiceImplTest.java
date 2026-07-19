package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoryboardServiceImplTest {

    private StoryboardServiceImpl storyboardService;
    private Script script;
    private ContentStrategy strategy;

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
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboardService = new StoryboardServiceImpl();
    }

    @Test
    void firstSceneIsAlwaysTypedHookRegardlessOfItsSourceField() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        assertThat(storyboard.scenes().get(0).sceneType()).isEqualTo(Scene.SceneType.HOOK);
    }

    @Test
    void scenesAreContiguousWithNoGapsOrOverlaps() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        double cursor = 0.0;
        for (Scene scene : storyboard.scenes()) {
            assertThat(scene.startTime()).isEqualTo(cursor);
            cursor = scene.endTime();
        }
        assertThat(storyboard.totalDurationSeconds()).isEqualTo(cursor);
    }

    @Test
    void everySceneVoiceoverConcatenatesBackToTheFullScript() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        String reconstructed = String.join(" ", storyboard.scenes().stream().map(Scene::voiceoverText).toList());
        assertThat(reconstructed).isEqualTo(script.fullSpokenScript());
    }

    @Test
    void exactlyOneSceneHasACodeBlockAndItMatchesTheScript() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        long codeSceneCount = storyboard.scenes().stream().filter(s -> s.codeBlock() != null).count();
        assertThat(codeSceneCount).isEqualTo(1);
        Scene codeScene = storyboard.scenes().stream().filter(s -> s.codeBlock() != null).findFirst().orElseThrow();
        assertThat(codeScene.codeBlock().codeSnippet()).isEqualTo(script.codeNarration().codeSnippet());
    }

    @Test
    void sceneCountMatchesScenesListSizeAndSceneOrderMatchesSceneIds() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        assertThat(storyboard.sceneCount()).isEqualTo(storyboard.scenes().size());
        assertThat(storyboard.sceneOrder()).containsExactlyElementsOf(storyboard.scenes().stream().map(Scene::sceneId).toList());
    }

    @Test
    void eachMainScriptBeatBecomesItsOwnExplanationScene() {
        Storyboard storyboard = storyboardService.generateStoryboard(script, strategy);

        long explanationScenes = storyboard.scenes().stream().filter(s -> s.sceneType() == Scene.SceneType.EXPLANATION).count();
        assertThat(explanationScenes).isEqualTo(script.mainScript().size());
    }
}
