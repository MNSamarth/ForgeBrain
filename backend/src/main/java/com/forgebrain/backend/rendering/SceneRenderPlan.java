package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.Scene;
import java.util.List;

/**
 * One scene's complete rendering blueprint — everything a rendering engine needs to compose
 * this beat, translated from the storyboard's {@link Scene} into render-oriented terms. Distinct
 * from {@link com.forgebrain.backend.rendering.SceneRenderInstruction}: that type represents
 * abstract references already <em>resolved</em> against real asset/audio files (it doesn't exist
 * yet — nothing produces it, since Voice and Asset Management aren't implemented). This type is
 * the earlier, declarative planning artifact — it still names assets by abstract reference
 * ({@code assetRefs}), not resolved URIs — that a future step would resolve into a {@code
 * SceneRenderInstruction} once those upstream stages exist.
 *
 * @param animationInstructions free-text motion/animation directives for this scene, carried
 *                              from {@link Scene#motionNotes()} — declarative intent, not an
 *                              executable animation program
 * @param subtitleOrders        the {@link SubtitleTimeline.SubtitleCue#order()} values of every
 *                              cue produced by this scene, so a renderer can look up this
 *                              scene's captions without re-deriving them from timing overlap
 * @param assetRefs             every asset this scene references, by abstract category + name —
 *                              see {@link AssetCollector} for how these get deduplicated across
 *                              an entire {@link RenderPlan}
 */
public record SceneRenderPlan(
        String sceneId,
        double startTime,
        double endTime,
        double duration,
        Scene.SceneType sceneType,
        BackgroundSpec background,
        List<TextLayer> textLayers,
        CodeLayer codeLayer,
        String animationInstructions,
        List<Integer> subtitleOrders,
        List<RenderPlan.GlobalAssetRef> assetRefs,
        Scene.TransitionStyle transitionIn,
        Scene.TransitionStyle transitionOut
) {

    /**
     * @param styleRef    abstract background treatment name, derived from the reel's {@code
     *                    RenderStyle} (e.g. {@code "dark-mode-ide"}) — not a resolved color/asset
     * @param description carried from {@link Scene#visualDescription()}, for a human (or a
     *                    future generative rendering step) to interpret
     */
    public record BackgroundSpec(String styleRef, String description) {
    }

    /**
     * One piece of on-screen text and which font role renders it.
     *
     * @param role     what this text is for (e.g. {@code "on-screen-text"}), not a visual style
     * @param fontRole which {@link RenderPlan.FontSet} slot renders this layer ({@code
     *                 "heading"}, {@code "body"}, or {@code "code"})
     */
    public record TextLayer(String role, String text, String fontRole) {
    }

    /**
     * Present only for scenes with a {@link Scene#codeBlock()}; {@code null} otherwise.
     */
    public record CodeLayer(String codeSnippet, String focusLine, String language) {
    }
}
