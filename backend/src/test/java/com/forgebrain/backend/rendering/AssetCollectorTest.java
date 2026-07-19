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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetCollectorTest {

    private final AssetCollector assetCollector = new AssetCollector();

    private RenderPlan buildRealRenderPlan() {
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
        Script script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        Storyboard storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
        return new RenderPlanBuilder().build(storyboard);
    }

    @Test
    void collectsGlobalFontsMusicAndWatermarkAsReelWideAssets() {
        RenderPlan plan = buildRealRenderPlan();
        RenderAssetManifest manifest = assetCollector.collect(plan);

        assertThat(manifest.topicId()).isEqualTo(plan.topicId());
        assertThat(manifest.basedOnRenderPlanVersion()).isEqualTo(plan.renderPlanVersion());
        assertThat(manifest.totalAssetCount()).isEqualTo(manifest.assets().size());

        Map<String, RenderAssetManifest.AssetReference> byId = manifest.assets().stream()
                .collect(java.util.stream.Collectors.toMap(RenderAssetManifest.AssetReference::assetId, a -> a));

        RenderAssetManifest.AssetReference heading = byId.get(AssetCategory.FONT + ":" + plan.fonts().heading());
        assertThat(heading).isNotNull();
        assertThat(heading.usedBySceneIds()).isEmpty();

        RenderAssetManifest.AssetReference music = byId.get(AssetCategory.MUSIC + ":" + plan.audio().backgroundMusicRef());
        assertThat(music).isNotNull();
        assertThat(music.usedBySceneIds()).isEmpty();

        RenderAssetManifest.AssetReference watermark = byId.get(AssetCategory.WATERMARK + ":watermark/forgebrain-default");
        assertThat(watermark).isNotNull();
        assertThat(watermark.usedBySceneIds()).isEmpty();
    }

    @Test
    void collectsSceneScopedCodeFontAndScreenshotAttributedToTheirScene() {
        RenderPlan plan = buildRealRenderPlan();
        RenderAssetManifest manifest = assetCollector.collect(plan);

        SceneRenderPlan codeScene = plan.scenes().stream().filter(s -> s.codeLayer() != null).findFirst().orElseThrow();

        RenderAssetManifest.AssetReference codeFont = manifest.assets().stream()
                .filter(a -> a.category() == AssetCategory.FONT && a.ref().equals(plan.fonts().code()))
                .findFirst().orElseThrow();
        assertThat(codeFont.usedBySceneIds()).containsExactly(codeScene.sceneId());

        RenderAssetManifest.AssetReference screenshot = manifest.assets().stream()
                .filter(a -> a.category() == AssetCategory.GENERATED_CODE_SCREENSHOT)
                .findFirst().orElseThrow();
        assertThat(screenshot.ref()).isEqualTo("code-screenshot/" + codeScene.sceneId());
        assertThat(screenshot.usedBySceneIds()).containsExactly(codeScene.sceneId());
    }

    @Test
    void deduplicatesTheSameAssetReferencedByMultipleScenesIntoOneEntryWithBothSceneIdsMerged() {
        RenderPlan.GlobalAssetRef sharedIconRef = new RenderPlan.GlobalAssetRef(AssetCategory.ICON, "icon/shared-highlight");
        SceneRenderPlan sceneOne = minimalScene("scene-1", 0, 5, List.of(sharedIconRef));
        SceneRenderPlan sceneTwo = minimalScene("scene-2", 5, 10, List.of(sharedIconRef));
        RenderPlan plan = minimalPlan(List.of(sceneOne, sceneTwo));

        RenderAssetManifest manifest = assetCollector.collect(plan);

        List<RenderAssetManifest.AssetReference> iconEntries = manifest.assets().stream()
                .filter(a -> a.category() == AssetCategory.ICON)
                .toList();
        assertThat(iconEntries).hasSize(1);
        assertThat(iconEntries.get(0).usedBySceneIds()).containsExactlyInAnyOrder("scene-1", "scene-2");
    }

    private SceneRenderPlan minimalScene(String sceneId, double start, double end, List<RenderPlan.GlobalAssetRef> assetRefs) {
        return new SceneRenderPlan(
                sceneId, start, end, end - start, Scene.SceneType.EXPLANATION,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(), null, "no motion", List.of(), assetRefs,
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
    }

    private RenderPlan minimalPlan(List<SceneRenderPlan> scenes) {
        double total = scenes.get(scenes.size() - 1).endTime();
        SubtitleTimeline subtitles = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED,
                List.of(new SubtitleTimeline.SubtitleCue(1, scenes.get(0).sceneId(), 0, total, "hello", List.of())),
                total, "1.0.0-heuristic");
        return new RenderPlan(
                "topic", "Topic", new RenderPlan.VideoDimensions(1080, 1920), 30, total, scenes,
                new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular"), subtitles,
                new RenderPlan.AudioPlan("voiceover/topic", "music/lofi-focus", -18.0), List.of(), List.of(),
                Storyboard.RenderStyle.DARK_MODE_IDE, Storyboard.AspectRatio.RATIO_9_16, "1.0.0", Instant.now(),
                "1.0.0-heuristic");
    }
}
