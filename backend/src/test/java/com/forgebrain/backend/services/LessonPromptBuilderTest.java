package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LessonPromptBuilderTest {

    private static final ResearchResult VARIABLES_RESEARCH = new ResearchResult(
            "java-variables-and-data-types",
            "Variables and Data Types",
            "Variables store typed values in Java.",
            "Understand how Java stores and types values in variables.",
            Topic.Difficulty.BEGINNER,
            Topic.Difficulty.BEGINNER,
            45,
            List.of(new ResearchResult.TopicRef("java-what-is-java", "What Is Java")),
            List.of("Forgetting that int division truncates"),
            List.of("Declaration", "Initialization", "Type safety"),
            "A variable is like a labeled box that only fits one shape of item.",
            List.of("Show int vs double division side by side"),
            "You must declare a type before storing a value in Java.",
            List.of("Primitive vs reference semantics affects copying."),
            List.of("java-operators"),
            List.of("Never assume int division returns a decimal result."),
            new ConfidenceNotes(ConfidenceLevel.MEDIUM, List.of("heuristic note"), List.of()),
            List.of(),
            "1.0.0-heuristic",
            Instant.now()
    );

    @Test
    void includesResearchContextAndRequiredJsonFieldNames() {
        String prompt = LessonPromptBuilder.build(VARIABLES_RESEARCH, null, Lesson.TeachingStyle.DIRECT_EXPLANATION);

        assertThat(prompt)
                .contains("Variables and Data Types")
                .contains(VARIABLES_RESEARCH.learningObjective())
                .contains("BEGINNER")
                .contains("45 seconds")
                .contains("DIRECT_EXPLANATION")
                .contains("\"lesson_objective\"")
                .contains("\"lesson_summary\"")
                .contains("\"key_points\"")
                .contains("\"step_by_step_explanation\"")
                .contains("\"core_example\"")
                .contains("\"analogy\"")
                .contains("\"common_mistakes\"")
                .contains("\"what_to_avoid_saying\"")
                .contains("\"beginner_takeaway\"")
                .contains("\"retention_hook\"")
                .contains("\"visual_notes\"")
                .contains("\"confidence_notes\"")
                .contains("Declaration")
                .contains("Show int vs double division side by side")
                .contains("Forgetting that int division truncates")
                .contains("Never assume int division returns a decimal result.");
    }

    @Test
    void instructsTheModelToReturnOnlyJsonAndRespectTheOneOfEverythingRule() {
        String prompt = LessonPromptBuilder.build(VARIABLES_RESEARCH, null, Lesson.TeachingStyle.DIRECT_EXPLANATION);

        assertThat(prompt).containsIgnoringCase("ONLY a single valid JSON object");
        assertThat(prompt).contains("Do not include markdown formatting");
        assertThat(prompt).contains("One of Everything");
    }

    @Test
    void addsRevisionContextOnlyWhenTopicNeedsRevisit() {
        MemoryState.TopicRecord needsRevisit = new MemoryState.TopicRecord(
                "java-variables-and-data-types", "Variables and Data Types", Topic.Status.NEEDS_REVISIT,
                Topic.Difficulty.BEGINNER, 1, Instant.now(), Instant.now(), 1, 0.4, 0.3, null,
                MemoryState.Priority.HIGH, null, List.of(), "pacing too slow before the crash demo");

        String withRevision = LessonPromptBuilder.build(VARIABLES_RESEARCH, needsRevisit,
                Lesson.TeachingStyle.PROBLEM_FIRST);
        String withoutRevision = LessonPromptBuilder.build(VARIABLES_RESEARCH, null,
                Lesson.TeachingStyle.PROBLEM_FIRST);

        assertThat(withRevision).contains("0.4").contains("pacing too slow before the crash demo");
        assertThat(withoutRevision).doesNotContain("being revised");
    }
}
