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
import com.forgebrain.backend.models.VisualPlan;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisualDirectorServiceImplTest {

    private final VisualDirectorServiceImpl service = new VisualDirectorServiceImpl();

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
    void producesOneVisualScenePlanPerStoryboardSceneInTheSameOrder() {
        VisualPlan plan = service.generateVisualPlan(script, storyboard);

        assertThat(plan.scenes()).hasSize(storyboard.scenes().size());
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            assertThat(plan.scenes().get(i).sceneId()).isEqualTo(storyboard.scenes().get(i).sceneId());
        }
    }

    @Test
    void routesTheHookSceneToTheHookPrimitiveWithFullBleedComposition() {
        VisualPlan plan = service.generateVisualPlan(script, storyboard);
        Scene hookScene = storyboard.scenes().get(0);
        assertThat(hookScene.sceneType()).isEqualTo(Scene.SceneType.HOOK);

        VisualPlan.VisualScenePlan hookPlan = plan.scenes().get(0);
        assertThat(hookPlan.scenePrimitive()).isEqualTo(VisualPlan.ScenePrimitive.HOOK);
        assertThat(hookPlan.composition()).isEqualTo(VisualPlan.Composition.FULL_BLEED);
        assertThat(hookPlan.hookIntent()).isEqualTo(hookScene.purpose());
    }

    @Test
    void routesTheCodeSceneToTheCodePrimitiveWithACodePanelAndNoImagePrompt() {
        VisualPlan plan = service.generateVisualPlan(script, storyboard);
        int codeIndex = indexOfSceneType(Scene.SceneType.CODE_REVEAL);

        VisualPlan.VisualScenePlan codePlan = plan.scenes().get(codeIndex);
        assertThat(codePlan.scenePrimitive()).isEqualTo(VisualPlan.ScenePrimitive.CODE);
        assertThat(codePlan.composition()).isEqualTo(VisualPlan.Composition.CODE_PANEL);
        assertThat(codePlan.codeBlockHint()).isEqualTo(storyboard.scenes().get(codeIndex).codeBlock().focusLine());
        assertThat(codePlan.imagePrompt()).isNull();
    }

    @Test
    void routesAMultiItemOnScreenTextSceneToDiagramFlow() {
        Scene stepScene = new Scene("scene-x-steps", 0, 4, 4, Scene.SceneType.STEP_BREAKDOWN, "narration",
                List.of("JVM", "Bytecode", "Machine Code"), "shows the pipeline", null, "none",
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT, List.of(), List.of(), "explains flow");
        Storyboard withStepScene = new Storyboard(storyboard.topicId(), storyboard.topicTitle(),
                storyboard.totalDurationSeconds(), 1, List.of(stepScene), List.of(stepScene.sceneId()),
                storyboard.visualStyle(), storyboard.animationStyle(), storyboard.subtitleStyle(),
                storyboard.codeStyle(), storyboard.transitionStyle(), storyboard.pacingProfile(),
                storyboard.emphasisPoints(), storyboard.confidenceNotes(), storyboard.platform(),
                storyboard.aspectRatio(), storyboard.renderStyle(), storyboard.targetDurationSeconds(),
                storyboard.storyboardVersion(), storyboard.generatedAt(), storyboard.basedOnScriptVersion());

        VisualPlan plan = service.generateVisualPlan(script, withStepScene);

        VisualPlan.VisualScenePlan stepPlan = plan.scenes().get(0);
        assertThat(stepPlan.scenePrimitive()).isEqualTo(VisualPlan.ScenePrimitive.FLOW);
        assertThat(stepPlan.composition()).isEqualTo(VisualPlan.Composition.DIAGRAM_FLOW);
        assertThat(stepPlan.diagramType()).isEqualTo("flow");
        assertThat(stepPlan.foregroundElements()).containsExactly("JVM", "Bytecode", "Machine Code");
        assertThat(stepPlan.imagePrompt()).isNotNull();
    }

    @Test
    void generatesAThumbnailBriefFromTheScriptsHookLine() {
        VisualPlan plan = service.generateVisualPlan(script, storyboard);

        assertThat(plan.thumbnailBrief()).contains(script.hook());
    }

    @Test
    void carriesTransitionsAndDurationDirectlyFromTheStoryboardScene() {
        VisualPlan plan = service.generateVisualPlan(script, storyboard);

        for (int i = 0; i < storyboard.scenes().size(); i++) {
            Scene scene = storyboard.scenes().get(i);
            VisualPlan.VisualScenePlan scenePlan = plan.scenes().get(i);
            assertThat(scenePlan.transitionIn()).isEqualTo(scene.transitionIn());
            assertThat(scenePlan.transitionOut()).isEqualTo(scene.transitionOut());
            assertThat(scenePlan.durationSeconds()).isEqualTo(scene.duration());
        }
    }

    private int indexOfSceneType(Scene.SceneType sceneType) {
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            if (storyboard.scenes().get(i).sceneType() == sceneType) {
                return i;
            }
        }
        throw new AssertionError("No scene of type " + sceneType + " in the generated storyboard fixture.");
    }
}
