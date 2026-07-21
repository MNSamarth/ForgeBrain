package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * The Visual Director stage's output: a scene-by-scene visual direction layer sitting between a
 * committed {@link Storyboard} and rendering — it decides HOW each scene should look (framing,
 * composition, motion, imagery) the same way {@link ContentStrategy} decides how a lesson should
 * be taught, without changing the storyboard's own scene content, timing, or scene count.
 * {@link VisualScenePlan#sceneId()} and {@link VisualScenePlan#durationSeconds()} are always
 * assembled deterministically from the storyboard scene they correspond to (one-to-one, in
 * order), never invented by a generative source — timing is already final by the time this stage
 * runs, so nothing here is allowed to disagree with it.
 */
public record VisualPlan(
        String topicId,
        String topicTitle,
        List<VisualScenePlan> scenes,
        String thumbnailBrief,
        ConfidenceNotes confidenceNotes,
        String visualPlanVersion,
        Instant generatedAt,
        String basedOnStoryboardVersion
) {

    /** The scene primitive the Visual Director chose for one scene — a distinct concept from
     * {@link Scene.SceneType}: this is about visual treatment, not narrative function. */
    public enum ScenePrimitive {
        HOOK, COMPARISON, DIAGRAM, CODE, FLOW, ARCHITECTURE, WALKTHROUGH, RECAP, CTA
    }

    /** How much of the frame a scene's visual should claim, and in what arrangement. */
    public enum Composition {
        FULL_BLEED, SPLIT_SCREEN, CENTERED_CARD, NESTED_BOXES, CODE_PANEL, DIAGRAM_FLOW
    }

    /**
     * @param sceneId            the {@link Scene#sceneId()} this direction applies to
     * @param scenePrimitive     which visual primitive this scene should render as
     * @param hookIntent         for hook-primitive scenes, what curiosity gap the visual should
     *                           open; empty for other primitives
     * @param visualGoal         the one thing this scene's visual must communicate
     * @param composition        how the frame is arranged
     * @param cameraMotion       free-text motion direction (pan/zoom/focus-shift intent)
     * @param backgroundStyle    free-text background treatment direction
     * @param foregroundElements short labels/items this scene's visual should feature
     * @param textOverlay        the on-screen text treatment direction (not the exact copy —
     *                           that stays owned by {@link Script}/{@link Scene#onScreenText()})
     * @param codeBlockHint      which line/aspect of the scene's code to feature, or {@code null}
     *                           if this scene has no code block
     * @param diagramType        the kind of diagram (e.g. {@code "flow"}, {@code "architecture"}),
     *                           or {@code null} if this scene isn't diagram-primitive
     * @param imagePrompt        a structured illustration/image-generation prompt brief for this
     *                           scene, or {@code null} if the scene is intentionally text-first —
     *                           ready for an image generator to consume later; nothing in this
     *                           codebase calls one yet (see backend/README.md)
     * @param motionCue          free-text emphasis/motion timing direction
     * @param transitionIn       reuses {@link Scene.TransitionStyle} directly rather than
     *                           duplicating it
     * @param transitionOut      reuses {@link Scene.TransitionStyle} directly rather than
     *                           duplicating it
     * @param durationSeconds    carried from the corresponding storyboard scene, not generated
     */
    public record VisualScenePlan(
            String sceneId,
            ScenePrimitive scenePrimitive,
            String hookIntent,
            String visualGoal,
            Composition composition,
            String cameraMotion,
            String backgroundStyle,
            List<String> foregroundElements,
            String textOverlay,
            String codeBlockHint,
            String diagramType,
            String imagePrompt,
            String motionCue,
            Scene.TransitionStyle transitionIn,
            Scene.TransitionStyle transitionOut,
            double durationSeconds
    ) {
    }
}
