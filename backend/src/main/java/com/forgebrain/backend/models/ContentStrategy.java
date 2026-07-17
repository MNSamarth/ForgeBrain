package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * The Content Director's output: a content strategy deciding how a lesson is presented
 * (hook, pacing, tone, visuals) without writing any dialogue. Mirrors
 * {@code brain/content-director-schema.json}.
 *
 * @see <a href="../../../../../../../../brain/content-director-schema.json">brain/content-director-schema.json</a>
 */
public record ContentStrategy(
        String topicId,
        String topicTitle,
        HookType hookType,
        String hookReason,
        TeachingStyle teachingStyle,
        String teachingStyleReason,
        EmotionalGoal emotionalGoal,
        String emotionalGoalReason,
        Pacing pacing,
        String pacingReason,
        List<ScenePacingEntry> scenePacing,
        VisualStyle visualStyle,
        List<String> supportingVisuals,
        String visualStyleReason,
        CodeStyle codeStyle,
        String codeStyleReason,
        CtaStyle ctaStyle,
        String ctaReason,
        String retentionGoal,
        int estimatedWatchTime,
        ConfidenceNotes confidenceNotes,
        int targetDurationSeconds,
        String contentDirectorVersion,
        Instant generatedAt,
        String basedOnLessonVersion
) {

    public record ScenePacingEntry(String scene, double durationSeconds) {
    }

    public enum HookType {
        BEGINNER_MISTAKE, MYTH, QUESTION, CHALLENGE, INTERVIEW_QUESTION,
        BEFORE_VS_AFTER, HIDDEN_FEATURE, COMMON_BUG, PRODUCTIVITY_TIP, PERFORMANCE_COMPARISON
    }

    public enum TeachingStyle {
        EXPLAIN_FIRST, CODE_FIRST, ANALOGY_FIRST, VISUAL_FIRST, QUESTION_FIRST
    }

    public enum EmotionalGoal {
        SURPRISE, CURIOSITY, CONFIDENCE, RELIEF, SATISFACTION
    }

    public enum Pacing {
        FAST, MEDIUM, SLOW
    }

    public enum VisualStyle {
        FULL_SCREEN_TYPOGRAPHY, CODE_ANIMATION, DIAGRAM, CURSOR_MOVEMENT,
        HIGHLIGHT_ANIMATION, SPLIT_SCREEN, TIMELINE
    }

    public enum CodeStyle {
        MINIMAL_EXAMPLE, COMPARISON_EXAMPLE, BUG_EXAMPLE, OPTIMIZATION_EXAMPLE, INTERVIEW_EXAMPLE
    }

    public enum CtaStyle {
        FOLLOW, SAVE, COMMENT, TRY_THIS_YOURSELF, NEXT_LESSON_TEASER
    }
}
