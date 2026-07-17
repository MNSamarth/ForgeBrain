package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.Scene;

/**
 * A {@link Scene} with its abstract asset references (from {@link
 * com.forgebrain.backend.models.AssetManifest}) and audio timing (from {@link
 * com.forgebrain.backend.models.VoiceResult}) already resolved into the concrete instruction
 * set a rendering engine would actually execute — as distinct from {@code Scene} itself, which
 * still carries the storyboard's abstract style names and estimated timing.
 *
 * <p>This is a translation-layer type only; it holds no rendering logic itself (see {@link
 * RenderEngine}).
 */
public record SceneRenderInstruction(
        String sceneId,
        double resolvedStartTime,
        double resolvedEndTime,
        Scene scene,
        String resolvedAudioFileUri,
        String resolvedCodeFontUri,
        String resolvedBackgroundColorHex
) {
}
