package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Transforms a {@link Storyboard} into a {@link RenderPlan} — pure translation, no rendering.
 * Every field on the resulting plan is derived deterministically from the storyboard and its
 * scenes; nothing here calls Vertex AI or any other generative source, matching how {@link
 * com.forgebrain.backend.services.StoryboardServiceImpl} itself stays purely mechanical.
 *
 * <p>Where the storyboard doesn't yet carry real production data — audio files (Voice
 * Generation isn't implemented), resolved asset URIs (Asset Management isn't implemented) —
 * this builder emits deterministic, documented placeholder references (e.g. {@code
 * "voiceover/" + topicId}) rather than nulls, so a {@link RenderPlan} is always a complete,
 * internally-consistent object that {@link RenderValidator} can meaningfully check. Those
 * references are placeholders to be resolved by future stages, not real files.
 */
@Component
public class RenderPlanBuilder {

    static final int STANDARD_FPS = 30;
    private static final String RENDER_PLAN_VERSION = "1.0.0";
    private static final double BACKGROUND_MUSIC_VOLUME_DB = -18.0;

    public RenderPlan build(Storyboard storyboard) {
        RenderPlan.VideoDimensions dimensions = dimensionsFor(storyboard.aspectRatio());
        RenderPlan.FontSet fonts = fontsFor(storyboard.renderStyle());

        SubtitleTimeline subtitles = buildSubtitleTimeline(storyboard);
        Map<String, List<Integer>> subtitleOrdersByScene = groupOrdersByScene(subtitles);

        List<SceneRenderPlan> scenes = storyboard.scenes().stream()
                .map(scene -> toSceneRenderPlan(scene, storyboard, fonts, subtitleOrdersByScene))
                .toList();

        List<RenderPlan.SceneTransition> transitions = buildTransitions(storyboard.scenes());
        RenderPlan.AudioPlan audio = new RenderPlan.AudioPlan(
                "voiceover/" + storyboard.topicId(),
                backgroundMusicRefFor(storyboard.renderStyle()),
                BACKGROUND_MUSIC_VOLUME_DB
        );
        List<RenderPlan.GlobalAssetRef> globalAssetRefs = List.of(
                new RenderPlan.GlobalAssetRef(AssetCategory.WATERMARK, "watermark/forgebrain-default")
        );

        return new RenderPlan(
                storyboard.topicId(),
                storyboard.topicTitle(),
                dimensions,
                STANDARD_FPS,
                storyboard.totalDurationSeconds(),
                scenes,
                fonts,
                subtitles,
                audio,
                transitions,
                globalAssetRefs,
                storyboard.renderStyle(),
                storyboard.aspectRatio(),
                RENDER_PLAN_VERSION,
                Instant.now(),
                storyboard.storyboardVersion()
        );
    }

    private SubtitleTimeline buildSubtitleTimeline(Storyboard storyboard) {
        List<SubtitleTimeline.SubtitleCue> cues = new ArrayList<>();
        int order = 1;
        for (Scene scene : storyboard.scenes()) {
            for (Scene.TimedSubtitleSegment segment : scene.subtitleSegments()) {
                cues.add(new SubtitleTimeline.SubtitleCue(
                        order++,
                        scene.sceneId(),
                        segment.startTime(),
                        segment.endTime(),
                        segment.text(),
                        segment.emphasisWords()
                ));
            }
        }
        return new SubtitleTimeline(
                storyboard.topicId(),
                storyboard.subtitleStyle(),
                cues,
                storyboard.totalDurationSeconds(),
                storyboard.storyboardVersion()
        );
    }

    private Map<String, List<Integer>> groupOrdersByScene(SubtitleTimeline timeline) {
        Map<String, List<Integer>> bySceneId = new HashMap<>();
        for (SubtitleTimeline.SubtitleCue cue : timeline.cues()) {
            bySceneId.computeIfAbsent(cue.sceneId(), id -> new ArrayList<>()).add(cue.order());
        }
        return bySceneId;
    }

    private SceneRenderPlan toSceneRenderPlan(Scene scene, Storyboard storyboard, RenderPlan.FontSet fonts,
            Map<String, List<Integer>> subtitleOrdersByScene) {
        SceneRenderPlan.BackgroundSpec background = new SceneRenderPlan.BackgroundSpec(
                styleRefFor(storyboard.renderStyle()), scene.visualDescription());

        List<SceneRenderPlan.TextLayer> textLayers = scene.onScreenText().stream()
                .map(text -> new SceneRenderPlan.TextLayer("on-screen-text", text, "heading"))
                .toList();

        SceneRenderPlan.CodeLayer codeLayer = scene.codeBlock() == null ? null
                : new SceneRenderPlan.CodeLayer(
                        scene.codeBlock().codeSnippet(), scene.codeBlock().focusLine(), scene.codeBlock().language());

        List<RenderPlan.GlobalAssetRef> assetRefs = scene.codeBlock() == null ? List.of()
                : List.of(
                        new RenderPlan.GlobalAssetRef(AssetCategory.FONT, fonts.code()),
                        new RenderPlan.GlobalAssetRef(AssetCategory.GENERATED_CODE_SCREENSHOT,
                                "code-screenshot/" + scene.sceneId()));

        List<Integer> subtitleOrders = subtitleOrdersByScene.getOrDefault(scene.sceneId(), List.of());

        return new SceneRenderPlan(
                scene.sceneId(),
                scene.startTime(),
                scene.endTime(),
                scene.duration(),
                scene.sceneType(),
                background,
                textLayers,
                codeLayer,
                scene.motionNotes(),
                subtitleOrders,
                assetRefs,
                scene.transitionIn(),
                scene.transitionOut()
        );
    }

    private List<RenderPlan.SceneTransition> buildTransitions(List<Scene> scenes) {
        List<RenderPlan.SceneTransition> transitions = new ArrayList<>();
        for (int i = 0; i < scenes.size() - 1; i++) {
            Scene current = scenes.get(i);
            Scene next = scenes.get(i + 1);
            transitions.add(new RenderPlan.SceneTransition(current.sceneId(), next.sceneId(), current.transitionOut()));
        }
        return transitions;
    }

    private RenderPlan.VideoDimensions dimensionsFor(Storyboard.AspectRatio aspectRatio) {
        return switch (aspectRatio) {
            case RATIO_9_16 -> new RenderPlan.VideoDimensions(1080, 1920);
            case RATIO_1_1 -> new RenderPlan.VideoDimensions(1080, 1080);
            case RATIO_4_5 -> new RenderPlan.VideoDimensions(1080, 1350);
        };
    }

    private RenderPlan.FontSet fontsFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular");
            case MINIMAL_LIGHT -> new RenderPlan.FontSet("Poppins-SemiBold", "Poppins-Regular", "JetBrainsMono-Regular");
            case NEON_TECH -> new RenderPlan.FontSet("Orbitron-Bold", "Inter-Regular", "FiraCode-Regular");
            case TERMINAL_RETRO -> new RenderPlan.FontSet("IBMPlexMono-Bold", "IBMPlexMono-Regular", "IBMPlexMono-Regular");
        };
    }

    private String backgroundMusicRefFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> "music/lofi-focus";
            case MINIMAL_LIGHT -> "music/soft-piano";
            case NEON_TECH -> "music/synthwave-drive";
            case TERMINAL_RETRO -> "music/chiptune-loop";
        };
    }

    private String styleRefFor(Storyboard.RenderStyle renderStyle) {
        return renderStyle.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
