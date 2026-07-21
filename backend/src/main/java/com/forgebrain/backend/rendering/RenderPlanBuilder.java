package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VisualPlan;
import com.forgebrain.backend.models.VoiceResult;
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
 * <p>Two entry points:
 *
 * <ul>
 *   <li>{@link #build(Storyboard)} — storyboard only. Where real production data doesn't exist
 *       (no {@link VoiceResult}, no {@link AssetManifest} supplied), this emits deterministic,
 *       documented placeholder references (e.g. {@code "voiceover/" + topicId}, a {@code
 *       RenderStyle}-driven font table) rather than nulls, so a {@link RenderPlan} is always a
 *       complete, internally-consistent object {@link RenderValidator} can meaningfully check.
 *       Used for quick/standalone plans and by existing tests.</li>
 *   <li>{@link #build(Storyboard, VoiceResult, SubtitleResult, AssetManifest)} — the real,
 *       fully-reconciled path {@link com.forgebrain.backend.pipeline.ReelExportServiceImpl}
 *       uses: scene timing comes from {@link VoiceResult}'s real measured durations (the timing
 *       authority from that stage forward, per {@code renderer/voice-spec.md} Section 4), caption
 *       text/timing comes from {@link SubtitleResult}'s already-reconciled segments, and
 *       fonts/music/watermark come from {@link AssetManifest}'s resolved theme instead of a
 *       guessed table.</li>
 *   <li>{@link #build(Storyboard, VoiceResult, SubtitleResult, AssetManifest, VisualPlan)} — the
 *       same reconciled path, additionally layered with the Visual Director's {@link VisualPlan}:
 *       a scene whose {@link VisualPlan.VisualScenePlan#composition()} is {@code FULL_BLEED} gets
 *       {@link SceneRenderPlan.BackgroundSpec#styleRef()} set to {@code "full-bleed-visual"} (the
 *       marker {@code RenderCommandBuilder} looks for to pick its full-bleed treatment instead of
 *       the default small accent card), and each scene's {@code motionCue}/{@code transitionIn}/
 *       {@code transitionOut} override the storyboard's own {@code motionNotes}/transitions when
 *       present — every other field stays exactly what the 4-argument overload would produce.</li>
 * </ul>
 */
@Component
public class RenderPlanBuilder {

    static final int STANDARD_FPS = 30;
    private static final String RENDER_PLAN_VERSION = "1.0.0";
    private static final String RENDER_PLAN_VERSION_RECONCILED = "1.0.0-reconciled";
    private static final double BACKGROUND_MUSIC_VOLUME_DB = -18.0;

    public RenderPlan build(Storyboard storyboard) {
        RenderPlan.VideoDimensions dimensions = dimensionsFor(storyboard.aspectRatio());
        RenderPlan.FontSet fonts = fontsFor(storyboard.renderStyle());

        SubtitleTimeline subtitles = buildSubtitleTimeline(storyboard);
        Map<String, List<Integer>> subtitleOrdersByScene = groupOrdersByScene(subtitles);

        List<SceneRenderPlan> scenes = storyboard.scenes().stream()
                .map(scene -> toSceneRenderPlan(scene, scene.startTime(), scene.endTime(), scene.duration(),
                        storyboard.renderStyle(), fonts, subtitleOrdersByScene, null))
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
                storyboard.storyboardVersion(),
                null
        );
    }

    /**
     * The reconciled path: scene timing from {@code voiceResult}'s real durations, subtitle
     * content/timing from {@code subtitleResult}'s already-reconciled segments, and asset
     * references from {@code assetManifest}'s resolved theme.
     */
    public RenderPlan build(Storyboard storyboard, VoiceResult voiceResult, SubtitleResult subtitleResult,
            AssetManifest assetManifest) {
        return buildReconciled(storyboard, voiceResult, subtitleResult, assetManifest, null);
    }

    /**
     * The reconciled path, additionally layered with the Visual Director's {@link VisualPlan} —
     * see the class-level javadoc for exactly what {@code visualPlan} changes.
     */
    public RenderPlan build(Storyboard storyboard, VoiceResult voiceResult, SubtitleResult subtitleResult,
            AssetManifest assetManifest, VisualPlan visualPlan) {
        return buildReconciled(storyboard, voiceResult, subtitleResult, assetManifest, visualPlan);
    }

    private RenderPlan buildReconciled(Storyboard storyboard, VoiceResult voiceResult, SubtitleResult subtitleResult,
            AssetManifest assetManifest, VisualPlan visualPlan) {
        RenderPlan.VideoDimensions dimensions = dimensionsFor(storyboard.aspectRatio());
        AssetManifest.ResolvedTheme theme = assetManifest.resolvedTheme();
        RenderPlan.FontSet fonts = new RenderPlan.FontSet(theme.fontHeading(), theme.fontBody(), theme.fontCode());

        Map<String, VoiceResult.SceneAudio> audioByScene = new HashMap<>();
        for (VoiceResult.SceneAudio sceneAudio : voiceResult.scenes()) {
            audioByScene.put(sceneAudio.sceneId(), sceneAudio);
        }
        Map<String, double[]> realTimingByScene = new HashMap<>();
        double cursor = 0.0;
        for (Scene scene : storyboard.scenes()) {
            VoiceResult.SceneAudio sceneAudio = audioByScene.get(scene.sceneId());
            double duration = sceneAudio != null ? sceneAudio.actualDurationSeconds() : scene.duration();
            realTimingByScene.put(scene.sceneId(), new double[] {cursor, cursor + duration, duration});
            cursor += duration;
        }
        double totalDuration = cursor;

        SubtitleTimeline subtitles = buildSubtitleTimeline(storyboard, subtitleResult);
        Map<String, List<Integer>> subtitleOrdersByScene = groupOrdersByScene(subtitles);
        Map<String, VisualPlan.VisualScenePlan> visualScenesById = visualPlan == null ? Map.of()
                : visualPlan.scenes().stream()
                        .collect(java.util.stream.Collectors.toMap(VisualPlan.VisualScenePlan::sceneId, sp -> sp));

        List<SceneRenderPlan> scenes = storyboard.scenes().stream()
                .map(scene -> {
                    double[] timing = realTimingByScene.get(scene.sceneId());
                    return toSceneRenderPlan(scene, timing[0], timing[1], timing[2], storyboard.renderStyle(),
                            fonts, subtitleOrdersByScene, visualScenesById.get(scene.sceneId()));
                })
                .toList();

        List<RenderPlan.SceneTransition> transitions = buildTransitions(storyboard.scenes());

        String voiceoverTrackRef = voiceResult.scenes().isEmpty() ? "voiceover/" + storyboard.topicId()
                : voiceResult.scenes().get(0).audioFileUri();
        RenderPlan.AudioPlan audio = new RenderPlan.AudioPlan(
                voiceoverTrackRef,
                assetManifest.backgroundMusic().trackUri(),
                assetManifest.backgroundMusic().volumeDb()
        );
        List<RenderPlan.GlobalAssetRef> globalAssetRefs = List.of(
                new RenderPlan.GlobalAssetRef(AssetCategory.WATERMARK, assetManifest.watermark().assetUri())
        );

        return new RenderPlan(
                storyboard.topicId(),
                storyboard.topicTitle(),
                dimensions,
                STANDARD_FPS,
                totalDuration,
                scenes,
                fonts,
                subtitles,
                audio,
                transitions,
                globalAssetRefs,
                storyboard.renderStyle(),
                storyboard.aspectRatio(),
                RENDER_PLAN_VERSION_RECONCILED,
                Instant.now(),
                storyboard.storyboardVersion(),
                visualPlan == null ? null : visualPlan.thumbnailBrief()
        );
    }

    private SubtitleTimeline buildSubtitleTimeline(Storyboard storyboard, SubtitleResult subtitleResult) {
        List<SubtitleTimeline.SubtitleCue> cues = new ArrayList<>();
        int order = 1;
        for (SubtitleResult.SceneSubtitles sceneSubtitles : subtitleResult.scenes()) {
            for (SubtitleResult.ReconciledSegment segment : sceneSubtitles.segments()) {
                cues.add(new SubtitleTimeline.SubtitleCue(
                        order++,
                        sceneSubtitles.sceneId(),
                        segment.startTime(),
                        segment.endTime(),
                        segment.text(),
                        segment.emphasisWords()
                ));
            }
        }
        return new SubtitleTimeline(
                storyboard.topicId(),
                subtitleResult.subtitleStyle(),
                cues,
                subtitleResult.totalDurationSeconds(),
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

    private SceneRenderPlan toSceneRenderPlan(Scene scene, double startTime, double endTime, double duration,
            Storyboard.RenderStyle renderStyle, RenderPlan.FontSet fonts,
            Map<String, List<Integer>> subtitleOrdersByScene, VisualPlan.VisualScenePlan visualScenePlan) {
        String styleRef = visualScenePlan != null && visualScenePlan.composition() == VisualPlan.Composition.FULL_BLEED
                ? SceneRenderPlan.FULL_BLEED_STYLE_REF
                : styleRefFor(renderStyle);
        SceneRenderPlan.BackgroundSpec background = new SceneRenderPlan.BackgroundSpec(
                styleRef, scene.visualDescription());

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

        String animationInstructions = visualScenePlan != null && visualScenePlan.motionCue() != null
                && !visualScenePlan.motionCue().isBlank() ? visualScenePlan.motionCue() : scene.motionNotes();
        Scene.TransitionStyle transitionIn = visualScenePlan != null && visualScenePlan.transitionIn() != null
                ? visualScenePlan.transitionIn() : scene.transitionIn();
        Scene.TransitionStyle transitionOut = visualScenePlan != null && visualScenePlan.transitionOut() != null
                ? visualScenePlan.transitionOut() : scene.transitionOut();

        return new SceneRenderPlan(
                scene.sceneId(),
                startTime,
                endTime,
                duration,
                scene.sceneType(),
                background,
                textLayers,
                codeLayer,
                animationInstructions,
                subtitleOrders,
                assetRefs,
                transitionIn,
                transitionOut
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
