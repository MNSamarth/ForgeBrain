package com.forgebrain.backend.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.models.VoiceResult.SceneAudio;
import com.forgebrain.backend.services.AssetServiceImpl;
import com.forgebrain.backend.services.ContentDirectorServiceImpl;
import com.forgebrain.backend.services.LessonServiceImpl;
import com.forgebrain.backend.services.ResearchServiceImpl;
import com.forgebrain.backend.services.ScriptServiceImpl;
import com.forgebrain.backend.services.StoryboardServiceImpl;
import com.forgebrain.backend.services.SubtitleServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds a real {@link Storyboard} through the actual (heuristic) pipeline chain — exactly the
 * pattern {@code StoryboardServiceImplTest} uses — rather than hand-authoring one, so these
 * assertions exercise {@link RenderPlanBuilder} against genuine pipeline output.
 */
class RenderPlanBuilderTest {

    @TempDir
    java.nio.file.Path tempDir;

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

    // --- Enriched (Voice/Subtitle/Asset-aware) overload -----------------------------------------

    private static final double SCALE_FACTOR = 1.5;

    private VoiceResult scaledVoiceResult() {
        List<SceneAudio> scenes = storyboard.scenes().stream()
                .map(scene -> new SceneAudio(scene.sceneId(), "voiceover/" + storyboard.topicId(),
                        scene.duration(), round(scene.duration() * SCALE_FACTOR), 0.0, List.of()))
                .toList();
        double totalActual = scenes.stream().mapToDouble(SceneAudio::actualDurationSeconds).sum();
        return new VoiceResult(storyboard.topicId(), storyboard.topicTitle(),
                new VoiceResult.VoiceProfile("en-US-Neural2-C", "en-US", 1.0, 0.0), scenes,
                storyboard.totalDurationSeconds(), totalActual, round(totalActual - storyboard.totalDurationSeconds()),
                false, 2.0, VoiceResult.AudioFormat.AUDIO_WAV, 44100,
                new com.forgebrain.backend.shared.ConfidenceNotes(
                        com.forgebrain.backend.shared.ConfidenceLevel.MEDIUM, List.of(), List.of()),
                "1.0.0-test-fixture", java.time.Instant.now(), storyboard.storyboardVersion());
    }

    @Test
    void enrichedBuildUsesFontsAndWatermarkAndMusicFromTheAssetManifestNotThePlaceholderTable() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest);

        assertThat(plan.fonts().heading()).isEqualTo(assetManifest.resolvedTheme().fontHeading());
        assertThat(plan.fonts().body()).isEqualTo(assetManifest.resolvedTheme().fontBody());
        assertThat(plan.fonts().code()).isEqualTo(assetManifest.resolvedTheme().fontCode());
        assertThat(plan.audio().backgroundMusicRef()).isEqualTo(assetManifest.backgroundMusic().trackUri());
        assertThat(plan.globalAssetRefs()).contains(
                new RenderPlan.GlobalAssetRef(AssetCategory.WATERMARK, assetManifest.watermark().assetUri()));
        assertThat(plan.renderPlanVersion()).isEqualTo("1.0.0-reconciled");
    }

    @Test
    void enrichedBuildsAudioTrackRefFromTheVoiceResultSoTheRendererActuallyConsumesTheNarrationOutput() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest);

        // RenderCommandBuilder passes this straight through to ffmpeg's audio input — it must be
        // exactly the file VoiceResult reports, not independently derived or reconstructed.
        assertThat(plan.audio().voiceoverTrackRef()).isEqualTo(voiceResult.scenes().get(0).audioFileUri());
    }

    @Test
    void enrichedBuildUsesVoiceResultsRealDurationsForSceneTimingInsteadOfTheStoryboardsEstimate() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest);

        // Real durations are 1.5x the estimate, so total duration must reflect that, not the
        // storyboard's original (unscaled) estimate.
        assertThat(plan.totalDurationSeconds())
                .isCloseTo(storyboard.totalDurationSeconds() * SCALE_FACTOR, org.assertj.core.data.Offset.offset(0.5));
        assertThat(plan.totalDurationSeconds()).isNotCloseTo(storyboard.totalDurationSeconds(),
                org.assertj.core.data.Offset.offset(0.5));

        // Scenes remain contiguous in the reconciled (real) timeline.
        double cursor = 0.0;
        for (SceneRenderPlan scene : plan.scenes()) {
            assertThat(scene.startTime()).isCloseTo(cursor, org.assertj.core.data.Offset.offset(0.01));
            cursor = scene.endTime();
        }
    }

    @Test
    void enrichedBuildsSubtitleTimelineComesFromTheReconciledSubtitleResultNotRawStoryboardSegments() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest);

        int expectedCueCount = subtitleResult.scenes().stream().mapToInt(s -> s.segments().size()).sum();
        assertThat(plan.subtitles().cues()).hasSize(expectedCueCount);
        assertThat(plan.subtitles().style()).isEqualTo(subtitleResult.subtitleStyle());
        assertThat(plan.subtitles().totalDurationSeconds()).isEqualTo(subtitleResult.totalDurationSeconds());

        // Reconciled (scaled) timing, not the storyboard's original unscaled segment timing.
        SubtitleTimeline.SubtitleCue firstCue = plan.subtitles().cues().get(0);
        Scene.TimedSubtitleSegment firstOriginalSegment = storyboard.scenes().get(0).subtitleSegments().get(0);
        assertThat(firstCue.startTime())
                .isCloseTo(firstOriginalSegment.startTime() * SCALE_FACTOR, org.assertj.core.data.Offset.offset(0.1));
    }

    // ----------------------------------------------------------------- Visual Director overload

    @Test
    void visualPlanOverloadMarksFullBleedScenesAndSetsTheThumbnailBrief() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);
        com.forgebrain.backend.models.VisualPlan visualPlan =
                new com.forgebrain.backend.services.VisualDirectorServiceImpl().generateVisualPlan(script, storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest, visualPlan);

        // The heuristic Visual Director marks the HOOK scene (always index 0) FULL_BLEED.
        assertThat(plan.scenes().get(0).background().styleRef()).isEqualTo(SceneRenderPlan.FULL_BLEED_STYLE_REF);
        assertThat(plan.thumbnailBrief()).isEqualTo(visualPlan.thumbnailBrief());
        assertThat(plan.renderPlanVersion()).isEqualTo("1.0.0-reconciled");
    }

    @Test
    void visualPlanOverloadOverridesMotionCueAndTransitionsWhenPresent() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);
        com.forgebrain.backend.models.VisualPlan visualPlan =
                new com.forgebrain.backend.services.VisualDirectorServiceImpl().generateVisualPlan(script, storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest, visualPlan);

        for (int i = 0; i < storyboard.scenes().size(); i++) {
            var visualScenePlan = visualPlan.scenes().get(i);
            var renderScene = plan.scenes().get(i);
            assertThat(renderScene.animationInstructions()).isEqualTo(visualScenePlan.motionCue());
            assertThat(renderScene.transitionIn()).isEqualTo(visualScenePlan.transitionIn());
            assertThat(renderScene.transitionOut()).isEqualTo(visualScenePlan.transitionOut());
        }
    }

    @Test
    void fourArgumentOverloadStillLeavesEveryStyleRefAtTheRenderStyleDefaultAndThumbnailBriefNull() {
        VoiceResult voiceResult = scaledVoiceResult();
        SubtitleResult subtitleResult = new SubtitleServiceImpl().generateSubtitles(storyboard, voiceResult);
        RenderingConfig renderingConfig = new RenderingConfig("ffmpeg", "ffprobe",
                tempDir.resolve("renders").toString(), tempDir.resolve("voiceover").toString(),
                tempDir.resolve("empty-assets").toString());
        AssetManifest assetManifest = new AssetServiceImpl(renderingConfig).resolveAssets(storyboard);

        RenderPlan plan = renderPlanBuilder.build(storyboard, voiceResult, subtitleResult, assetManifest);

        assertThat(plan.thumbnailBrief()).isNull();
        assertThat(plan.scenes()).noneMatch(
                scene -> SceneRenderPlan.FULL_BLEED_STYLE_REF.equals(scene.background().styleRef()));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
