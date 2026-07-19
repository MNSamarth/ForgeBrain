package com.forgebrain.backend.rendering;

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
import com.forgebrain.backend.services.ContentDirectorServiceImpl;
import com.forgebrain.backend.services.LessonServiceImpl;
import com.forgebrain.backend.services.ResearchServiceImpl;
import com.forgebrain.backend.services.ScriptServiceImpl;
import com.forgebrain.backend.services.StoryboardServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Builds a real {@link Storyboard} through the actual (heuristic) pipeline chain — exactly the
 * pattern {@code StoryboardServiceImplTest} uses — rather than hand-authoring one, so these
 * assertions exercise {@link RenderPlanBuilder} against genuine pipeline output.
 */
class RenderPlanBuilderTest {

    private RenderPlanBuilder renderPlanBuilder;
    private Storyboard storyboard;
    private Script script;

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
        var research = new ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
        renderPlanBuilder = new RenderPlanBuilder();
    }

    // --- RenderPlan generation ---------------------------------------------------------------

    @Test
    void dimensionsFpsAndDurationAreDerivedFromTheStoryboard() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        assertThat(plan.dimensions()).isEqualTo(new RenderPlan.VideoDimensions(1080, 1920));
        assertThat(plan.fps()).isEqualTo(30);
        assertThat(plan.totalDurationSeconds()).isEqualTo(storyboard.totalDurationSeconds());
        assertThat(plan.topicId()).isEqualTo(storyboard.topicId());
        assertThat(plan.basedOnStoryboardVersion()).isEqualTo(storyboard.storyboardVersion());
    }

    @Test
    void fontsAreDerivedFromTheRenderStyle() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        assertThat(storyboard.renderStyle()).isEqualTo(Storyboard.RenderStyle.DARK_MODE_IDE);
        assertThat(plan.fonts()).isEqualTo(new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular"));
    }

    @Test
    void transitionsConnectEveryConsecutiveScenePairInOrder() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        assertThat(plan.transitions()).hasSize(storyboard.scenes().size() - 1);
        for (int i = 0; i < plan.transitions().size(); i++) {
            RenderPlan.SceneTransition transition = plan.transitions().get(i);
            assertThat(transition.fromSceneId()).isEqualTo(storyboard.scenes().get(i).sceneId());
            assertThat(transition.toSceneId()).isEqualTo(storyboard.scenes().get(i + 1).sceneId());
            assertThat(transition.style()).isEqualTo(storyboard.scenes().get(i).transitionOut());
        }
    }

    @Test
    void audioAndGlobalAssetsAreDeterministicPlaceholders() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        assertThat(plan.audio().voiceoverTrackRef()).isEqualTo("voiceover/" + storyboard.topicId());
        assertThat(plan.audio().backgroundMusicRef()).isEqualTo("music/lofi-focus");
        assertThat(plan.globalAssetRefs()).containsExactly(
                new RenderPlan.GlobalAssetRef(AssetCategory.WATERMARK, "watermark/forgebrain-default"));
    }

    // --- Scene conversion ----------------------------------------------------------------------

    @Test
    void everyStoryboardSceneBecomesOneSceneRenderPlanWithMatchingTiming() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        assertThat(plan.scenes()).hasSize(storyboard.scenes().size());
        for (int i = 0; i < storyboard.scenes().size(); i++) {
            Scene source = storyboard.scenes().get(i);
            SceneRenderPlan rendered = plan.scenes().get(i);
            assertThat(rendered.sceneId()).isEqualTo(source.sceneId());
            assertThat(rendered.startTime()).isEqualTo(source.startTime());
            assertThat(rendered.endTime()).isEqualTo(source.endTime());
            assertThat(rendered.duration()).isEqualTo(source.duration());
            assertThat(rendered.sceneType()).isEqualTo(source.sceneType());
            assertThat(rendered.transitionIn()).isEqualTo(source.transitionIn());
            assertThat(rendered.transitionOut()).isEqualTo(source.transitionOut());
            assertThat(rendered.background().description()).isEqualTo(source.visualDescription());
            assertThat(rendered.animationInstructions()).isEqualTo(source.motionNotes());
        }
    }

    @Test
    void exactlyOneSceneHasACodeLayerWithMatchingContentAndAssetRefs() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        List<SceneRenderPlan> codeScenes = plan.scenes().stream().filter(s -> s.codeLayer() != null).toList();
        assertThat(codeScenes).hasSize(1);

        SceneRenderPlan codeScene = codeScenes.get(0);
        assertThat(codeScene.codeLayer().codeSnippet()).isEqualTo(script.codeNarration().codeSnippet());
        assertThat(codeScene.codeLayer().focusLine()).isEqualTo(script.codeNarration().focusLine());
        assertThat(codeScene.assetRefs()).containsExactlyInAnyOrder(
                new RenderPlan.GlobalAssetRef(AssetCategory.FONT, "JetBrainsMono-Regular"),
                new RenderPlan.GlobalAssetRef(AssetCategory.GENERATED_CODE_SCREENSHOT,
                        "code-screenshot/" + codeScene.sceneId()));

        List<SceneRenderPlan> nonCodeScenes = plan.scenes().stream().filter(s -> s.codeLayer() == null).toList();
        assertThat(nonCodeScenes).isNotEmpty();
        nonCodeScenes.forEach(scene -> assertThat(scene.assetRefs()).isEmpty());
    }

    @Test
    void hookSceneTextLayerMatchesItsOnScreenText() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        Scene hookScene = storyboard.scenes().get(0);
        SceneRenderPlan hookRenderPlan = plan.scenes().get(0);

        assertThat(hookRenderPlan.textLayers()).hasSize(hookScene.onScreenText().size());
        for (int i = 0; i < hookScene.onScreenText().size(); i++) {
            assertThat(hookRenderPlan.textLayers().get(i).text()).isEqualTo(hookScene.onScreenText().get(i));
            assertThat(hookRenderPlan.textLayers().get(i).fontRole()).isEqualTo("heading");
        }
    }

    // --- Subtitle timeline ----------------------------------------------------------------------

    @Test
    void subtitleTimelineFlattensEveryScenesSegmentsInOrderAndReconstructsTheFullScript() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        int expectedCueCount = storyboard.scenes().stream().mapToInt(s -> s.subtitleSegments().size()).sum();
        assertThat(plan.subtitles().cues()).hasSize(expectedCueCount);
        assertThat(plan.subtitles().style()).isEqualTo(storyboard.subtitleStyle());
        assertThat(plan.subtitles().totalDurationSeconds()).isEqualTo(storyboard.totalDurationSeconds());

        for (int i = 0; i < plan.subtitles().cues().size(); i++) {
            assertThat(plan.subtitles().cues().get(i).order()).isEqualTo(i + 1);
        }

        String reconstructed = String.join(" ",
                plan.subtitles().cues().stream().map(SubtitleTimeline.SubtitleCue::text).toList());
        assertThat(reconstructed).isEqualTo(script.fullSpokenScript());
    }

    @Test
    void everyScenesSubtitleOrdersMatchItsOwnSubtitleSegmentCountAndAreContiguousAcrossTheReel() {
        RenderPlan plan = renderPlanBuilder.build(storyboard);

        int totalReferenced = plan.scenes().stream().mapToInt(s -> s.subtitleOrders().size()).sum();
        assertThat(totalReferenced).isEqualTo(plan.subtitles().cues().size());

        for (int i = 0; i < plan.scenes().size(); i++) {
            SceneRenderPlan scene = plan.scenes().get(i);
            int expectedSegmentCount = storyboard.scenes().get(i).subtitleSegments().size();
            assertThat(scene.subtitleOrders()).hasSize(expectedSegmentCount);
        }

        // Every cue referenced by a scene must actually belong to that scene.
        for (SceneRenderPlan scene : plan.scenes()) {
            for (Integer order : scene.subtitleOrders()) {
                SubtitleTimeline.SubtitleCue cue = plan.subtitles().cues().get(order - 1);
                assertThat(cue.sceneId()).isEqualTo(scene.sceneId());
            }
        }
    }
}
