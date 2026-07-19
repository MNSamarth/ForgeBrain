package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchPromptBuilderTest {

    private static final Topic VARIABLES_TOPIC = new Topic(
            "java-variables-and-data-types",
            "Variables and Data Types",
            "fundamentals",
            Topic.Difficulty.BEGINNER,
            List.of("java-what-is-java"),
            List.of("java-operators"),
            "Understand how Java stores and types values in variables.",
            List.of("Forgetting that int division truncates"),
            List.of("Show int vs double division side by side"),
            Topic.Status.NOT_COVERED
    );

    @Test
    void includesTopicContextAndRequiredJsonFieldNames() {
        String prompt = ResearchPromptBuilder.build(VARIABLES_TOPIC, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(prompt)
                .contains("Variables and Data Types")
                .contains(VARIABLES_TOPIC.learningObjective())
                .contains("BEGINNER")
                .contains("45 seconds")
                .contains("\"topic_summary\"")
                .contains("\"core_concepts\"")
                .contains("\"simple_analogy\"")
                .contains("\"beginner_explanation\"")
                .contains("\"advanced_notes\"")
                .contains("\"safety_notes\"")
                .contains("Forgetting that int division truncates")
                .contains("Show int vs double division side by side");
    }

    @Test
    void instructsTheModelToReturnOnlyJson() {
        String prompt = ResearchPromptBuilder.build(VARIABLES_TOPIC, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(prompt).containsIgnoringCase("ONLY a single valid JSON object");
        assertThat(prompt).contains("Do not include markdown formatting");
    }

    @Test
    void addsRevisionContextOnlyWhenTopicNeedsRevisit() {
        MemoryState.TopicRecord needsRevisit = new MemoryState.TopicRecord(
                "java-variables-and-data-types", "Variables and Data Types", Topic.Status.NEEDS_REVISIT,
                Topic.Difficulty.BEGINNER, 1, Instant.now(), Instant.now(), 1, 0.4, 0.3, null,
                MemoryState.Priority.HIGH, null, List.of(), "explanation was too fast");

        String withRevision = ResearchPromptBuilder.build(VARIABLES_TOPIC, Topic.Difficulty.BEGINNER, 45,
                needsRevisit);
        String withoutRevision = ResearchPromptBuilder.build(VARIABLES_TOPIC, Topic.Difficulty.BEGINNER, 45, null);

        assertThat(withRevision).contains("0.4").contains("explanation was too fast");
        assertThat(withoutRevision).doesNotContain("being revised");
    }
}
