package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VisualPlan;
import com.forgebrain.backend.models.VisualPlan.Composition;
import com.forgebrain.backend.models.VisualPlan.ScenePrimitive;
import com.forgebrain.backend.models.VisualPlan.VisualScenePlan;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic implementation of {@link VisualDirectorService}: derives every visual scene
 * plan field from data the storyboard already carries (scene type, on-screen text, code block,
 * motion notes, highlighted words, transitions) rather than inventing new creative direction —
 * the same "real, exercised fallback, not a stub" shape every other AI-Gateway-backed stage in
 * this codebase falls back to (see {@link ContentDirectorServiceImpl}).
 *
 * <p>Not a Spring {@code @Component}, matching {@link ContentDirectorServiceImpl}/{@link
 * ResearchServiceImpl}/etc.: {@link VertexAiVisualDirectorServiceImpl} is the single {@code
 * @Component} for {@link VisualDirectorService} and constructs this fallback directly.
 */
public class VisualDirectorServiceImpl implements VisualDirectorService {

    private static final String VISUAL_PLAN_VERSION = "1.0.0-heuristic";

    @Override
    public VisualPlan generateVisualPlan(Script script, Storyboard storyboard) {
        List<VisualScenePlan> scenes = storyboard.scenes().stream()
                .map(scene -> toVisualScenePlan(scene, storyboard))
                .toList();

        return new VisualPlan(
                storyboard.topicId(),
                storyboard.topicTitle(),
                scenes,
                thumbnailBriefFor(script),
                new ConfidenceNotes(ConfidenceLevel.MEDIUM,
                        List.of("Visual direction is derived deterministically from the storyboard's own scene"
                                + " data (scene type, on-screen text, code block, motion notes) rather than"
                                + " independently generated creative direction — see"
                                + " VertexAiVisualDirectorServiceImpl for the AI-Gateway-backed path."),
                        List.of()),
                VISUAL_PLAN_VERSION,
                Instant.now(),
                storyboard.storyboardVersion()
        );
    }

    private VisualScenePlan toVisualScenePlan(Scene scene, Storyboard storyboard) {
        boolean isCode = scene.codeBlock() != null;
        boolean isList = !isCode && scene.onScreenText().size() > 1;

        ScenePrimitive primitive = isCode ? ScenePrimitive.CODE : isList ? ScenePrimitive.FLOW
                : primitiveFor(scene.sceneType());
        Composition composition = isCode ? Composition.CODE_PANEL : isList ? Composition.DIAGRAM_FLOW
                : compositionFor(scene.sceneType());

        return new VisualScenePlan(
                scene.sceneId(),
                primitive,
                scene.sceneType() == Scene.SceneType.HOOK ? scene.purpose() : "",
                scene.visualDescription(),
                composition,
                scene.motionNotes(),
                storyboard.renderStyle().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                scene.onScreenText(),
                String.join(" | ", scene.onScreenText()),
                isCode ? scene.codeBlock().focusLine() : null,
                composition == Composition.DIAGRAM_FLOW ? "flow" : null,
                imagePromptFor(composition, scene),
                motionCueFor(scene),
                scene.transitionIn(),
                scene.transitionOut(),
                scene.duration()
        );
    }

    private ScenePrimitive primitiveFor(Scene.SceneType sceneType) {
        return switch (sceneType) {
            case HOOK -> ScenePrimitive.HOOK;
            case SETUP, EXPLANATION -> ScenePrimitive.WALKTHROUGH;
            case CODE_REVEAL -> ScenePrimitive.CODE;
            case STEP_BREAKDOWN -> ScenePrimitive.FLOW;
            case MISTAKE_HIGHLIGHT, COMPARISON -> ScenePrimitive.COMPARISON;
            case RECAP -> ScenePrimitive.RECAP;
            case CTA -> ScenePrimitive.CTA;
        };
    }

    private Composition compositionFor(Scene.SceneType sceneType) {
        return switch (sceneType) {
            case HOOK, CTA -> Composition.FULL_BLEED;
            case SETUP, EXPLANATION, RECAP -> Composition.CENTERED_CARD;
            case CODE_REVEAL -> Composition.CODE_PANEL;
            case STEP_BREAKDOWN -> Composition.DIAGRAM_FLOW;
            case MISTAKE_HIGHLIGHT, COMPARISON -> Composition.SPLIT_SCREEN;
        };
    }

    /**
     * A structured image-generation prompt brief for compositions that call for real imagery —
     * {@code null} for {@code CODE_PANEL} (code is its own visual medium) and {@code
     * CENTERED_CARD} (a scene explicitly meant to be text-first), per this mission's Part 4.
     */
    private String imagePromptFor(Composition composition, Scene scene) {
        boolean wantsImagery = composition == Composition.FULL_BLEED || composition == Composition.SPLIT_SCREEN
                || composition == Composition.DIAGRAM_FLOW || composition == Composition.NESTED_BOXES;
        return wantsImagery ? "Illustration for: " + scene.visualDescription() : null;
    }

    private String motionCueFor(Scene scene) {
        return scene.highlightedWords().isEmpty() ? scene.motionNotes()
                : "Emphasize on: " + String.join(", ", scene.highlightedWords());
    }

    private String thumbnailBriefFor(Script script) {
        return "Bold hook visual over an accent background, communicating: " + script.hook();
    }
}
