package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.VisualPlan;
import java.util.List;

/**
 * The subset of {@link VisualPlan.VisualScenePlan} that Vertex AI is asked to generate per scene
 * — see {@link VisualDirectorPromptBuilder}. {@code sceneId} and {@code durationSeconds} are
 * deliberately absent: {@link VertexAiVisualDirectorServiceImpl} assembles them deterministically
 * by zipping this response's {@code scenes} array against the storyboard's own scenes by index.
 */
record VertexAiVisualScenePlan(
        VisualPlan.ScenePrimitive scenePrimitive,
        String hookIntent,
        String visualGoal,
        VisualPlan.Composition composition,
        String cameraMotion,
        String backgroundStyle,
        List<String> foregroundElements,
        String textOverlay,
        String codeBlockHint,
        String diagramType,
        String imagePrompt,
        String motionCue,
        Scene.TransitionStyle transitionIn,
        Scene.TransitionStyle transitionOut
) {
}
