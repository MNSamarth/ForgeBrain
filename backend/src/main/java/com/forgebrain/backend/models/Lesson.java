package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * A lesson blueprint: the single committed concept, example, and takeaway a reel teaches.
 * Mirrors {@code brain/lesson-output-schema.json}. See brain/lesson-spec.md Section 4, the
 * "One of Everything" rule, for why most list fields here are still expected to serve exactly
 * one underlying objective rather than several competing ones.
 *
 * @see <a href="../../../../../../../../brain/lesson-output-schema.json">brain/lesson-output-schema.json</a>
 */
public record Lesson(
        String topicId,
        String topicTitle,
        String lessonObjective,
        String lessonSummary,
        List<String> keyPoints,
        List<String> stepByStepExplanation,
        CoreExample coreExample,
        String analogy,
        List<String> commonMistakes,
        List<String> whatToAvoidSaying,
        String beginnerTakeaway,
        String retentionHook,
        List<String> visualNotes,
        ConfidenceNotes confidenceNotes,
        Topic.Difficulty audienceLevel,
        int targetDurationSeconds,
        TeachingStyle teachingStyle,
        String lessonVersion,
        Instant generatedAt,
        String basedOnResearchVersion
) {

    public record CoreExample(String description, String codeSketch, String focusNote) {
    }

    public enum TeachingStyle {
        DIRECT_EXPLANATION, PROBLEM_FIRST, STORY_DRIVEN, COMPARISON_BASED, MYTH_BUSTING
    }
}
