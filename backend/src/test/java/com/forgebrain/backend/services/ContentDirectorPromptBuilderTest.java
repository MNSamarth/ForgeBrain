package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentDirectorPromptBuilderTest {

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

    @Test
    void includesLessonContextAndRequiredJsonFieldNames() {
        String prompt = ContentDirectorPromptBuilder.build(FOR_LOOP_LESSON, null);

        assertThat(prompt)
                .contains("The For Loop")
                .contains(FOR_LOOP_LESSON.lessonObjective())
                .contains("BEGINNER")
                .contains("40 seconds")
                .contains("PROBLEM_FIRST")
                .contains("\"hook_type\"")
                .contains("\"hook_reason\"")
                .contains("\"teaching_style\"")
                .contains("\"teaching_style_reason\"")
                .contains("\"emotional_goal\"")
                .contains("\"emotional_goal_reason\"")
                .contains("\"pacing\"")
                .contains("\"pacing_reason\"")
                .contains("\"scene_pacing\"")
                .contains("\"visual_style\"")
                .contains("\"supporting_visuals\"")
                .contains("\"visual_style_reason\"")
                .contains("\"code_style\"")
                .contains("\"code_style_reason\"")
                .contains("\"cta_style\"")
                .contains("\"cta_reason\"")
                .contains("\"retention_goal\"")
                .contains("\"estimated_watch_time\"")
                .contains("\"confidence_notes\"")
                .contains("Forgetting to update the loop variable causes an infinite loop")
                .contains("Highlight the loop variable each time it changes");
    }

    @Test
    void listsEnumValuesInUnderscoreFormAndInstructsJsonOnlyWithNoDialogue() {
        String prompt = ContentDirectorPromptBuilder.build(FOR_LOOP_LESSON, null);

        assertThat(prompt).contains("BEGINNER_MISTAKE").contains("BEFORE_VS_AFTER").contains("COMMON_BUG");
        assertThat(prompt).contains("TRY_THIS_YOURSELF").contains("NEXT_LESSON_TEASER");
        assertThat(prompt).containsIgnoringCase("ONLY a single valid JSON object");
        assertThat(prompt).contains("Do not include markdown formatting");
        assertThat(prompt).contains("never the exact words");
        assertThat(prompt).doesNotContain("hyphens:\n  \"hook_type\": one of beginner-mistake");
    }

    @Test
    void addsRevisionContextOnlyWhenTopicNeedsRevisit() {
        MemoryState.TopicRecord needsRevisit = new MemoryState.TopicRecord(
                "java-for-loop", "The For Loop", Topic.Status.NEEDS_REVISIT, Topic.Difficulty.BEGINNER, 1,
                Instant.now(), Instant.now(), 1, 0.4, 0.3, null, MemoryState.Priority.HIGH, null, List.of(),
                "hook landed flat, viewers dropped off early");

        String withRevision = ContentDirectorPromptBuilder.build(FOR_LOOP_LESSON, needsRevisit);
        String withoutRevision = ContentDirectorPromptBuilder.build(FOR_LOOP_LESSON, null);

        assertThat(withRevision).contains("0.4").contains("hook landed flat, viewers dropped off early");
        assertThat(withoutRevision).doesNotContain("being revised");
    }
}
