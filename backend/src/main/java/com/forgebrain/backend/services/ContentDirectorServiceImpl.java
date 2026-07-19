package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.ContentStrategy.CodeStyle;
import com.forgebrain.backend.models.ContentStrategy.CtaStyle;
import com.forgebrain.backend.models.ContentStrategy.EmotionalGoal;
import com.forgebrain.backend.models.ContentStrategy.HookType;
import com.forgebrain.backend.models.ContentStrategy.Pacing;
import com.forgebrain.backend.models.ContentStrategy.ScenePacingEntry;
import com.forgebrain.backend.models.ContentStrategy.TeachingStyle;
import com.forgebrain.backend.models.ContentStrategy.VisualStyle;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, rule-based implementation of {@link ContentDirectorService}. Every decision
 * is a fixed rule over the lesson's own content — see brain/content-director-spec.md Section
 * 5 for the heuristic tables these rules follow. Given the same lesson, this always produces
 * the same strategy.
 *
 * <p>Not a Spring bean: {@link VertexAiContentDirectorServiceImpl} is the {@link
 * ContentDirectorService} bean and constructs this directly as its fallback, exactly as {@link
 * ResearchServiceImpl} and {@link LessonServiceImpl} are used by their respective Vertex AI
 * implementations.
 */
public class ContentDirectorServiceImpl implements ContentDirectorService {

    private static final String CONTENT_DIRECTOR_VERSION = "1.0.0-heuristic";

    @Override
    public ContentStrategy decideStrategy(Lesson lesson, MemoryState.TopicRecord topicMemory) {
        boolean isRevision = topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT;

        HookType hookType = decideHookType(lesson);
        String hookReason = explainHookType(hookType, lesson);

        TeachingStyle teachingStyle = mapTeachingStyle(lesson.teachingStyle());
        String teachingStyleReason = "Derived from the lesson's own teaching_style (" + lesson.teachingStyle()
                + "), mapped to the closest opening posture.";

        EmotionalGoal emotionalGoal = isRevision ? EmotionalGoal.SURPRISE : mapEmotionalGoal(hookType);
        String emotionalGoalReason = isRevision
                ? "Forced to surprise for this revision, to land the fix more memorably than the prior attempt."
                : "Matches the chosen hook_type per the standard hook-to-emotion mapping.";

        Pacing pacing = lesson.audienceLevel() == Topic.Difficulty.ADVANCED ? Pacing.SLOW : Pacing.MEDIUM;
        String pacingReason = "Derived from audience_level (" + lesson.audienceLevel() + ").";
        List<ScenePacingEntry> scenePacing = buildScenePacing(lesson.targetDurationSeconds());

        VisualStyle visualStyle = (hookType == HookType.BEGINNER_MISTAKE || hookType == HookType.COMMON_BUG)
                ? VisualStyle.HIGHLIGHT_ANIMATION
                : VisualStyle.FULL_SCREEN_TYPOGRAPHY;

        CodeStyle codeStyle = (hookType == HookType.BEGINNER_MISTAKE || hookType == HookType.COMMON_BUG)
                ? CodeStyle.BUG_EXAMPLE
                : CodeStyle.MINIMAL_EXAMPLE;

        CtaStyle ctaStyle = mapCtaStyle(teachingStyle);

        String retentionGoal = "The viewer should stay through the "
                + (lesson.commonMistakes().isEmpty() ? "core explanation" : "reveal of the common mistake")
                + " to reach the takeaway: " + lesson.beginnerTakeaway();

        int estimatedWatchTime = (int) Math.round(lesson.targetDurationSeconds() * 0.85);

        ConfidenceNotes confidenceNotes = buildConfidenceNotes(lesson, isRevision);

        return new ContentStrategy(
                lesson.topicId(),
                lesson.topicTitle(),
                hookType,
                hookReason,
                teachingStyle,
                teachingStyleReason,
                emotionalGoal,
                emotionalGoalReason,
                pacing,
                pacingReason,
                scenePacing,
                visualStyle,
                List.of(),
                "Default visual technique for this hook type; not yet tuned against real strategy_performance data.",
                codeStyle,
                "Follows directly from hook_type (" + hookType + ").",
                ctaStyle,
                "Matches the chosen opening posture (" + teachingStyle + ").",
                retentionGoal,
                estimatedWatchTime,
                confidenceNotes,
                lesson.targetDurationSeconds(),
                CONTENT_DIRECTOR_VERSION,
                Instant.now(),
                lesson.lessonVersion()
        );
    }

    private HookType decideHookType(Lesson lesson) {
        if (!lesson.commonMistakes().isEmpty()) {
            return lesson.teachingStyle() == Lesson.TeachingStyle.PROBLEM_FIRST
                    ? HookType.COMMON_BUG
                    : HookType.BEGINNER_MISTAKE;
        }
        return HookType.QUESTION;
    }

    private String explainHookType(HookType hookType, Lesson lesson) {
        return switch (hookType) {
            case COMMON_BUG -> "The lesson's own common_mistakes and problem-first teaching_style point directly at a bug-driven hook.";
            case BEGINNER_MISTAKE -> "The lesson names a specific common mistake (" + lesson.commonMistakes().get(0) + "), the strongest available hook material.";
            default -> "The lesson has no common_mistakes to lead with, so the hook opens on the core question instead.";
        };
    }

    private TeachingStyle mapTeachingStyle(Lesson.TeachingStyle lessonStyle) {
        return switch (lessonStyle) {
            case DIRECT_EXPLANATION -> TeachingStyle.EXPLAIN_FIRST;
            case PROBLEM_FIRST, COMPARISON_BASED -> TeachingStyle.CODE_FIRST;
            case STORY_DRIVEN -> TeachingStyle.ANALOGY_FIRST;
            case MYTH_BUSTING -> TeachingStyle.QUESTION_FIRST;
        };
    }

    private EmotionalGoal mapEmotionalGoal(HookType hookType) {
        return switch (hookType) {
            case BEGINNER_MISTAKE, COMMON_BUG, MYTH -> EmotionalGoal.SURPRISE;
            case QUESTION, CHALLENGE -> EmotionalGoal.CURIOSITY;
            case PERFORMANCE_COMPARISON -> EmotionalGoal.SATISFACTION;
            default -> EmotionalGoal.CONFIDENCE;
        };
    }

    private CtaStyle mapCtaStyle(TeachingStyle teachingStyle) {
        return switch (teachingStyle) {
            case CODE_FIRST -> CtaStyle.TRY_THIS_YOURSELF;
            case EXPLAIN_FIRST -> CtaStyle.SAVE;
            default -> CtaStyle.FOLLOW;
        };
    }

    private List<ScenePacingEntry> buildScenePacing(int targetDurationSeconds) {
        return List.of(
                new ScenePacingEntry("hook", round(targetDurationSeconds * 0.15)),
                new ScenePacingEntry("body", round(targetDurationSeconds * 0.45)),
                new ScenePacingEntry("example", round(targetDurationSeconds * 0.25)),
                new ScenePacingEntry("recap-and-cta", round(targetDurationSeconds * 0.15))
        );
    }

    private ConfidenceNotes buildConfidenceNotes(Lesson lesson, boolean isRevision) {
        List<String> flagged = new ArrayList<>();
        flagged.add("Hook, teaching-style, and visual choices were made via fixed deterministic rules,"
                + " not yet informed by real strategy_performance data (see brain/content-director-spec.md Section 8).");
        if (isRevision) {
            flagged.add("This is a revision. Strategy was deliberately shifted toward a sharper,"
                    + " more surprising hook rather than repeating the prior underperforming approach.");
        }
        return new ConfidenceNotes(lesson.confidenceNotes().overallConfidence(), flagged, List.of());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
