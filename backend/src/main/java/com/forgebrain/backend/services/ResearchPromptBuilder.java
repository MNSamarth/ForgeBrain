package com.forgebrain.backend.services;

import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;

/**
 * Builds the prompt text sent to Vertex AI for the research stage. Grounds the request in the
 * curriculum's own {@code learning_objective}, {@code common_mistakes}, and {@code
 * example_ideas} — the same already-curated source material {@link ResearchServiceImpl} uses —
 * so the model narrows and phrases that content rather than inventing topic facts from
 * scratch. Asks for exactly the fields in {@link VertexResearchContent}; every other {@code
 * ResearchResult} field is assembled deterministically by {@link VertexAiResearchServiceImpl}.
 */
final class ResearchPromptBuilder {

    private ResearchPromptBuilder() {
    }

    static String build(Topic curriculumContext, Topic.Difficulty audienceLevel, int targetReelLengthSeconds,
            MemoryState.TopicRecord topicMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are generating a structured research brief for a short-form Java programming ")
                .append("educational video.\n\n")
                .append("Return ONLY a single valid JSON object with exactly these fields and no others:\n")
                .append("{\n")
                .append("  \"topic_summary\": string,\n")
                .append("  \"core_concepts\": array of 2 to 4 strings,\n")
                .append("  \"simple_analogy\": string,\n")
                .append("  \"beginner_explanation\": string,\n")
                .append("  \"advanced_notes\": array of 0 to 3 strings,\n")
                .append("  \"safety_notes\": array of 0 to 2 strings\n")
                .append("}\n\n")
                .append("Do not include markdown formatting, code fences, or any text outside the JSON object.\n\n")
                .append("Topic: ").append(curriculumContext.title()).append('\n')
                .append("Learning objective: ").append(curriculumContext.learningObjective()).append('\n')
                .append("Difficulty: ").append(curriculumContext.difficulty()).append('\n')
                .append("Audience level: ").append(audienceLevel).append('\n')
                .append("Target video length: ").append(targetReelLengthSeconds).append(" seconds\n");

        if (!curriculumContext.commonMistakes().isEmpty()) {
            prompt.append("Known common mistakes learners make (never present these as correct): ")
                    .append(String.join("; ", curriculumContext.commonMistakes())).append('\n');
        }
        if (!curriculumContext.exampleIdeas().isEmpty()) {
            prompt.append("Example ideas already curated for this topic: ")
                    .append(String.join("; ", curriculumContext.exampleIdeas())).append('\n');
        }

        prompt.append('\n')
                .append("Guidance:\n")
                .append("- topic_summary: one or two sentences summarizing what this topic covers, ")
                .append("written for a beginner audience.\n")
                .append("- core_concepts: the most important ideas a viewer must understand, ordered by ")
                .append("importance.\n")
                .append("- simple_analogy: one real-world analogy that makes the concept intuitive without ")
                .append("being misleading.\n")
                .append("- beginner_explanation: a plain-language narration-ready explanation, assuming no ")
                .append("prior knowledge beyond the topic's prerequisites.\n")
                .append("- advanced_notes: caveats, edge cases, or deeper context for advanced viewers; empty ")
                .append("array if none apply.\n")
                .append("- safety_notes: warnings against common misconceptions or unsafe/incorrect practices; ")
                .append("empty array if none apply.\n");

        if (topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT) {
            prompt.append('\n')
                    .append("This topic is being revised after a prior attempt scored ")
                    .append(topicMemory.performanceScore());
            if (topicMemory.notes() != null && !topicMemory.notes().isBlank()) {
                prompt.append(", with the following noted issue: ").append(topicMemory.notes());
            }
            prompt.append(". The explanation should visibly address this.\n");
        }

        return prompt.toString();
    }
}
