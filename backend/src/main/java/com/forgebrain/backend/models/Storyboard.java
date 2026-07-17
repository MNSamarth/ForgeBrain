package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * A render-ready, scene-by-scene visual plan built from a {@link Script}. Mirrors
 * {@code brain/storyboard-output-schema.json}. Scenes are contiguous by construction: each
 * scene's {@code startTime} equals the previous scene's {@code endTime} (see
 * brain/storyboard-spec.md Section 10).
 *
 * @see <a href="../../../../../../../../brain/storyboard-output-schema.json">brain/storyboard-output-schema.json</a>
 */
public record Storyboard(
        String topicId,
        String topicTitle,
        double totalDurationSeconds,
        int sceneCount,
        List<Scene> scenes,
        List<String> sceneOrder,
        ContentStrategy.VisualStyle visualStyle,
        AnimationStyle animationStyle,
        SubtitleStyle subtitleStyle,
        CodeStyle codeStyle,
        Scene.TransitionStyle transitionStyle,
        PacingProfile pacingProfile,
        List<EmphasisPoint> emphasisPoints,
        ConfidenceNotes confidenceNotes,
        Script.Platform platform,
        AspectRatio aspectRatio,
        RenderStyle renderStyle,
        int targetDurationSeconds,
        String storyboardVersion,
        Instant generatedAt,
        String basedOnScriptVersion
) {

    public record PacingProfile(
            ContentStrategy.Pacing tier,
            double averageSceneDurationSeconds,
            double shortestSceneDurationSeconds,
            double longestSceneDurationSeconds
    ) {
    }

    public record EmphasisPoint(String sceneId, String moment, String reason) {
    }

    public enum AnimationStyle {
        SNAPPY_CUTS, SMOOTH_TRANSITIONS, KINETIC_TYPOGRAPHY, MINIMAL_STATIC, DYNAMIC_CAMERA
    }

    public enum SubtitleStyle {
        BOLD_CENTERED, KARAOKE_HIGHLIGHT, MINIMAL_BOTTOM_THIRD, BOXED_CAPTION
    }

    public enum CodeStyle {
        TYPING_ANIMATION, LINE_BY_LINE_REVEAL, STATIC_PANEL_WITH_HIGHLIGHT,
        BEFORE_AFTER_COMPARISON, CURSOR_WALKTHROUGH
    }

    public enum AspectRatio {
        RATIO_9_16, RATIO_1_1, RATIO_4_5
    }

    public enum RenderStyle {
        DARK_MODE_IDE, MINIMAL_LIGHT, NEON_TECH, TERMINAL_RETRO
    }
}
