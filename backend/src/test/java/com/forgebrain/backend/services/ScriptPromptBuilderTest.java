package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptPromptBuilderTest {

    private static final Lesson FOR_LOOP_LESSON = new Lesson(
            "java-for-loop",
            "The For Loop",
            "Understand how a for loop repeats a block a fixed number of times.",
            "This lesson walks through a for loop counting from zero.",
            List.of("The header has init, condition, and update", "The condition is checked before every iteration"),
            List.of("Start with the loop header", "Explain the condition check", "Show the body executing"),
            new Lesson.CoreExample("A for loop printing numbers 0 through 4",
                    "for (int i = 0; i < 5; i++) { System.out.println(i); }",
                    "Highlight the condition i < 5 as the loop runs"),
            "A for loop is like a countdown timer that stops itself once it hits zero.",
            List.of("Forgetting to update the loop variable causes an infinite loop"),
            List.of("Never imply a for loop always counts upward"),
            "A for loop repeats its body until its condition becomes false.",
            "Watch what happens when a loop forgets to update its own counter.",
            List.of("Highlight the loop variable each time it changes"),
            new ConfidenceNotes(ConfidenceLevel.HIGH, List.of(), List.of()),
            Topic.Difficulty.BEGINNER,
            40,
            Lesson.TeachingStyle.PROBLEM_FIRST,
            "1.0.0-heuristic",
            Instant.now(),
            "1.0.0-heuristic"
    );

    private static final ContentStrategy COMMON_BUG_STRATEGY = new ContentStrategy(
            "java-for-loop",
            "The For Loop",
            ContentStrategy.HookType.COMMON_BUG,
            "The common_mistakes entry about forgetting the update step is the strongest hook.",
            ContentStrategy.TeachingStyle.CODE_FIRST,
            "The lesson is problem-first and the core_example is the clearest way in.",
            ContentStrategy.EmotionalGoal.SURPRISE,
            "A common-bug hook resolved cleanly lands as surprise.",
            ContentStrategy.Pacing.FAST,
            "Several short step_by_step beats suit a quick cut pace.",
            List.of(new ContentStrategy.ScenePacingEntry("cold-open infinite loop", 6),
                    new ContentStrategy.ScenePacingEntry("explain the header", 14),
                    new ContentStrategy.ScenePacingEntry("fix and recap", 12)),
            ContentStrategy.VisualStyle.HIGHLIGHT_ANIMATION,
            List.of("Highlight the loop variable each time it changes"),
            "Matches the core_example's focus_note on the condition check.",
            ContentStrategy.CodeStyle.BUG_EXAMPLE,
            "Frames the lesson's own core_example as the bug that gets fixed.",
            ContentStrategy.CtaStyle.TRY_THIS_YOURSELF,
            "A bug fix invites the viewer to try triggering it themselves.",
            "The viewer stays to see why the loop never stops.",
            34,
            new ConfidenceNotes(ConfidenceLevel.HIGH, List.of(), List.of()),
            40,
            "1.0.0-heuristic",
            Instant.now(),
            "1.0.0-heuristic"
    );

    @Test
    void includesLessonAndStrategyContextAndRequiredJsonFieldNames() {
        String prompt = ScriptPromptBuilder.build(FOR_LOOP_LESSON, COMMON_BUG_STRATEGY);

        assertThat(prompt)
                .contains("The For Loop")
                .contains(FOR_LOOP_LESSON.lessonObjective())
                .contains("BEGINNER")
                .contains("40 seconds")
                .contains("for (int i = 0; i < 5; i++)")
                .contains("Forgetting to update the loop variable causes an infinite loop")
                .contains("Never imply a for loop always counts upward")
                .contains("\"hook\"")
                .contains("\"intro_line\"")
                .contains("\"main_script\"")
                .contains("\"code_narration\"")
                .contains("\"recap_line\"")
                .contains("\"cta_line\"")
                .contains("\"scene_text\"")
                .contains("\"tone\"")
                .contains("\"confidence_notes\"");
    }

    @Test
    void reflectsTheBindingStrategyLiterallyInThePrompt() {
        String prompt = ScriptPromptBuilder.build(FOR_LOOP_LESSON, COMMON_BUG_STRATEGY);

        assertThat(prompt)
                .contains("hook_type = COMMON_BUG")
                .contains("teaching_style = CODE_FIRST")
                .contains("pacing = FAST")
                .contains("code_style = BUG_EXAMPLE")
                .contains("cta_style = TRY_THIS_YOURSELF")
                .contains("emotional_goal = SURPRISE")
                .contains("cold-open infinite loop")
                .contains("explain the header")
                .contains("fix and recap");
    }

    @Test
    void listsToneValuesInUnderscoreFormAndInstructsJsonOnlyWithShortSpokenLines() {
        String prompt = ScriptPromptBuilder.build(FOR_LOOP_LESSON, COMMON_BUG_STRATEGY);

        assertThat(prompt).contains("DIRECT_AND_PUNCHY").contains("WARM_ENCOURAGING").contains("CALM_CONFIDENT");
        assertThat(prompt).containsIgnoringCase("ONLY a single valid JSON object");
        assertThat(prompt).contains("Do not include markdown formatting");
        assertThat(prompt).contains("Short sentences");
        assertThat(prompt).contains("Teach nothing new beyond what is given below");
    }
}
