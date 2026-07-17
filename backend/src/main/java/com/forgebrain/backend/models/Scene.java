package com.forgebrain.backend.models;

import java.util.List;

/**
 * A single storyboard scene: what the audience sees, hears, and reads for one beat of a reel.
 * Mirrors the {@code scene} definition in {@code brain/storyboard-output-schema.json}. Declared
 * as a standalone top-level type (rather than nested inside {@link Storyboard}) since it is
 * referenced independently across the renderer layer (e.g. {@code failedAtSceneId} in
 * {@link RenderJob}, {@code sceneId} in {@link com.forgebrain.backend.rendering.SceneRenderInstruction}).
 *
 * @see <a href="../../../../../../../../brain/storyboard-output-schema.json">brain/storyboard-output-schema.json</a>
 */
public record Scene(
        String sceneId,
        double startTime,
        double endTime,
        double duration,
        SceneType sceneType,
        String voiceoverText,
        List<String> onScreenText,
        String visualDescription,
        CodeBlock codeBlock,
        String motionNotes,
        TransitionStyle transitionIn,
        TransitionStyle transitionOut,
        List<TimedSubtitleSegment> subtitleSegments,
        List<String> highlightedWords,
        String purpose
) {

    public enum SceneType {
        HOOK, SETUP, EXPLANATION, CODE_REVEAL, STEP_BREAKDOWN,
        MISTAKE_HIGHLIGHT, COMPARISON, RECAP, CTA
    }

    public enum TransitionStyle {
        HARD_CUT, QUICK_FADE, SLIDE, ZOOM_PUNCH, MATCH_CUT
    }

    public record CodeBlock(String codeSnippet, String focusLine, String language) {
    }

    public record TimedSubtitleSegment(String text, double startTime, double endTime, List<String> emphasisWords) {
    }
}
