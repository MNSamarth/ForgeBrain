package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.util.List;

/**
 * The subset of {@link ContentStrategy} that Vertex AI is asked to generate — see {@link
 * ContentDirectorPromptBuilder}. Deserialized from the model's JSON response by the shared
 * snake_case {@link com.fasterxml.jackson.databind.ObjectMapper} bean; every other {@code
 * ContentStrategy} field ({@code topicId}, {@code topicTitle}, {@code targetDurationSeconds},
 * and the version/traceability fields) stays assembled deterministically by {@link
 * VertexAiContentDirectorServiceImpl}. Reuses {@link ContentStrategy.ScenePacingEntry} and
 * {@link ConfidenceNotes} directly rather than duplicating their shape, since both already match
 * the JSON the model is asked to return field-for-field.
 */
record VertexAiContentStrategy(
        ContentStrategy.HookType hookType,
        String hookReason,
        ContentStrategy.TeachingStyle teachingStyle,
        String teachingStyleReason,
        ContentStrategy.EmotionalGoal emotionalGoal,
        String emotionalGoalReason,
        ContentStrategy.Pacing pacing,
        String pacingReason,
        List<ContentStrategy.ScenePacingEntry> scenePacing,
        ContentStrategy.VisualStyle visualStyle,
        List<String> supportingVisuals,
        String visualStyleReason,
        ContentStrategy.CodeStyle codeStyle,
        String codeStyleReason,
        ContentStrategy.CtaStyle ctaStyle,
        String ctaReason,
        String retentionGoal,
        Integer estimatedWatchTime,
        ConfidenceNotes confidenceNotes
) {
}
