package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import java.time.Instant;
import java.util.List;

/**
 * Everything needed to render one reel, translated from a {@link Storyboard} by {@link
 * RenderPlanBuilder}. This is the rendering foundation's central artifact — the planning layer
 * between the AI content pipeline (which ends at {@link Storyboard}) and an eventual real
 * rendering engine (see {@link RenderEngine}). No video is produced from this type; it only
 * describes, declaratively, what a render would need to do.
 *
 * <p>Asset references throughout this plan ({@link #fonts()}, {@link #audio()}, each scene's
 * {@code assetRefs}, and {@link #globalAssetRefs()}) are abstract names, not resolved file URIs
 * — see {@link AssetCollector} for how they're gathered into one deduplicated {@link
 * RenderAssetManifest}, and {@link RenderValidator} for how this plan is checked for internal
 * consistency before anything downstream would act on it.
 *
 * @param scenes          one {@link SceneRenderPlan} per storyboard scene, in delivery order
 * @param globalAssetRefs reel-wide assets not tied to any specific scene (e.g. a watermark) —
 *                        distinct from each scene's own {@code assetRefs}
 * @param transitions     the transition between each consecutive pair of scenes
 * @param thumbnailBrief  what the thumbnail should communicate, from the Visual Director's {@link
 *                        com.forgebrain.backend.models.VisualPlan#thumbnailBrief()} — {@code null}
 *                        when no {@link com.forgebrain.backend.models.VisualPlan} was available,
 *                        in which case {@link com.forgebrain.backend.rendering.ffmpeg.ThumbnailCommandBuilder}
 *                        falls back to deriving a headline from the hook scene's own text
 */
public record RenderPlan(
        String topicId,
        String topicTitle,
        VideoDimensions dimensions,
        int fps,
        double totalDurationSeconds,
        List<SceneRenderPlan> scenes,
        FontSet fonts,
        SubtitleTimeline subtitles,
        AudioPlan audio,
        List<SceneTransition> transitions,
        List<GlobalAssetRef> globalAssetRefs,
        Storyboard.RenderStyle renderStyle,
        Storyboard.AspectRatio aspectRatio,
        String renderPlanVersion,
        Instant generatedAt,
        String basedOnStoryboardVersion,
        String thumbnailBrief
) {

    public record VideoDimensions(int width, int height) {
    }

    /**
     * The three font roles every scene's {@link SceneRenderPlan.TextLayer#fontRole()} and {@link
     * SceneRenderPlan.CodeLayer} draw from, named the same way {@link
     * com.forgebrain.backend.models.AssetManifest.ResolvedTheme} already does for consistency
     * across the codebase.
     */
    public record FontSet(String heading, String body, String code) {
    }

    /**
     * @param voiceoverTrackRef     abstract reference to this topic's narration track — not a
     *                              real audio file yet, since Voice Generation isn't implemented
     * @param backgroundMusicRef    abstract reference to a background music track
     * @param backgroundMusicVolumeDb target mix volume for the background track, relative to the
     *                              voiceover
     */
    public record AudioPlan(String voiceoverTrackRef, String backgroundMusicRef, double backgroundMusicVolumeDb) {
    }

    public record SceneTransition(String fromSceneId, String toSceneId, Scene.TransitionStyle style) {
    }

    public record GlobalAssetRef(AssetCategory category, String ref) {
    }
}
