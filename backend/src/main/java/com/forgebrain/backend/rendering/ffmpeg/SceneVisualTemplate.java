package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.models.Scene;

/**
 * Per-{@link Scene.SceneType} visual identity — accent color, heading size, whether an accent
 * card sits behind the heading, and a short "kicker" label — so every scene type reads visually
 * distinct instead of every scene rendering as identical centered text on one flat background
 * (mission Part 1: a consistent visual language where hook/problem/code/diagram/explanation/
 * summary/CTA scenes each look like themselves). Every {@code headingFontSize} here is a static
 * value used only as a {@code drawtext fontsize=} argument, never as a time-varying expression —
 * animating {@code fontsize} was verified to crash ffmpeg with a segmentation fault on this
 * project's target ffmpeg build (see commit history / RenderCommandBuilder), so scene "punch" is
 * expressed through size choice and card treatment, not size animation.
 */
record SceneVisualTemplate(
        String accentColorHex,
        String accentCardColor,
        int headingFontSize,
        boolean showAccentCard,
        String kicker,
        boolean centered
) {

    /**
     * @return the heading's vertical anchor for this template, given the reel's actual output
     * height — {@code centered} templates (HOOK/CTA) anchor around the vertical middle
     * proportionally rather than a fixed pixel value, so the layout stays sane across every
     * {@link com.forgebrain.backend.models.Storyboard.AspectRatio}, not just 9:16.
     */
    int textYFor(int videoHeight) {
        return centered ? videoHeight / 2 - headingFontSize / 2 : 260;
    }

    static SceneVisualTemplate forSceneType(Scene.SceneType sceneType) {
        return switch (sceneType) {
            case HOOK -> new SceneVisualTemplate("0xff6b6b", "0xff6b6b@0.20", 78, true, "", true);
            case SETUP -> new SceneVisualTemplate("0x58a6ff", "0x58a6ff@0.16", 56, true, "SETUP", false);
            case EXPLANATION -> new SceneVisualTemplate("0x58a6ff", "0x58a6ff@0.14", 52, false, "", false);
            case CODE_REVEAL -> new SceneVisualTemplate("0x3fb950", "0x3fb950@0.16", 46, false, "CODE", false);
            case STEP_BREAKDOWN -> new SceneVisualTemplate("0xd29922", "0xd29922@0.16", 46, false, "STEP BY STEP", false);
            case MISTAKE_HIGHLIGHT -> new SceneVisualTemplate("0xf85149", "0xf85149@0.20", 54, true, "WATCH OUT", false);
            case COMPARISON -> new SceneVisualTemplate("0xa371f7", "0xa371f7@0.16", 46, false, "COMPARE", false);
            case RECAP -> new SceneVisualTemplate("0x58a6ff", "0x58a6ff@0.16", 54, true, "RECAP", false);
            case CTA -> new SceneVisualTemplate("0x3fb950", "0x3fb950@0.22", 62, true, "", true);
        };
    }
}
