package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.util.List;

/**
 * The subset of {@link Lesson} that Vertex AI is asked to generate — see
 * {@link LessonPromptBuilder}. Deserialized from the model's JSON response by the shared
 * snake_case {@link com.fasterxml.jackson.databind.ObjectMapper} bean; every other {@code
 * Lesson} field ({@code topicId}, {@code topicTitle}, {@code audienceLevel}, {@code
 * targetDurationSeconds}, {@code teachingStyle}, and the version/traceability fields) stays
 * assembled deterministically by {@link VertexAiLessonServiceImpl}. Reuses {@link
 * Lesson.CoreExample} and {@link ConfidenceNotes} directly rather than duplicating their shape,
 * since both already match the JSON the model is asked to return field-for-field.
 */
record VertexAiLessonContent(
        String lessonObjective,
        String lessonSummary,
        List<String> keyPoints,
        List<String> stepByStepExplanation,
        Lesson.CoreExample coreExample,
        String analogy,
        List<String> commonMistakes,
        List<String> whatToAvoidSaying,
        String beginnerTakeaway,
        String retentionHook,
        List<String> visualNotes,
        ConfidenceNotes confidenceNotes
) {
}
