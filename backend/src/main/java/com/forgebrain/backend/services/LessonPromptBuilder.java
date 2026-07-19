package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;

/**
 * Builds the prompt text sent to Vertex AI for the lesson stage. Grounds the request in the
 * research brief this lesson narrows — never inventing new topic facts, only choosing and
 * structuring which of the brief's candidates become the lesson's single throughline, per
 * brain/lesson-spec.md Section 4 (the "One of Everything" rule). Asks for exactly the fields in
 * {@link VertexAiLessonContent}; every other {@code Lesson} field ({@code topicId}, {@code
 * topicTitle}, {@code audienceLevel}, {@code targetDurationSeconds}, {@code teachingStyle}, and
 * the version/traceability fields) is assembled deterministically by
 * {@link VertexAiLessonServiceImpl}.
 */
final class LessonPromptBuilder {

    private LessonPromptBuilder() {
    }

    static String build(ResearchResult research, MemoryState.TopicRecord topicMemory,
            Lesson.TeachingStyle teachingStyle) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are narrowing a research brief into a single-concept lesson blueprint for a ")
                .append("short-form Java programming educational video.\n\n")
                .append("Return ONLY a single valid JSON object with exactly these fields and no others:\n")
                .append("{\n")
                .append("  \"lesson_objective\": string,\n")
                .append("  \"lesson_summary\": string,\n")
                .append("  \"key_points\": array of 3 to 5 strings,\n")
                .append("  \"step_by_step_explanation\": array of 3 to 6 strings,\n")
                .append("  \"core_example\": { \"description\": string, \"code_sketch\": string, ")
                .append("\"focus_note\": string },\n")
                .append("  \"analogy\": string,\n")
                .append("  \"common_mistakes\": array of 1 to 3 strings,\n")
                .append("  \"what_to_avoid_saying\": array of strings (may be empty),\n")
                .append("  \"beginner_takeaway\": string,\n")
                .append("  \"retention_hook\": string,\n")
                .append("  \"visual_notes\": array of 1 to 3 strings,\n")
                .append("  \"confidence_notes\": { \"overall_confidence\": \"high\" | \"medium\" | \"low\", ")
                .append("\"flagged_uncertainties\": array of strings (may be empty) }\n")
                .append("}\n\n")
                .append("Do not include markdown formatting, code fences, or any text outside the JSON object.\n\n")
                .append("The \"One of Everything\" rule (brain/lesson-spec.md Section 4) governs this lesson:\n")
                .append("- lesson_objective is the ONE concept this lesson teaches — everything else must serve it.\n")
                .append("- core_example is ONE chosen example, not a menu of options.\n")
                .append("- beginner_takeaway is the ONE sentence a viewer should remember.\n")
                .append("- The first entry in common_mistakes is the ONE memorable contrast or mistake; any others ")
                .append("are secondary.\n")
                .append("- The first entry in visual_notes is the ONE required visual cue; any others are ")
                .append("secondary.\n")
                .append("- key_points are ORDERED STEPS toward the single lesson_objective, not a list of ")
                .append("competing concepts.\n\n")
                .append("Research brief for topic: ").append(research.topicTitle()).append('\n')
                .append("Learning objective: ").append(research.learningObjective()).append('\n')
                .append("Topic summary: ").append(research.topicSummary()).append('\n')
                .append("Audience level: ").append(research.audienceLevel()).append('\n')
                .append("Target video length: ").append(research.targetReelLengthSeconds()).append(" seconds\n")
                .append("Required teaching style: ").append(teachingStyle).append('\n');

        if (!research.coreConcepts().isEmpty()) {
            prompt.append("Candidate core concepts (pick the throughline, do not cover all of them): ")
                    .append(String.join("; ", research.coreConcepts())).append('\n');
        }
        prompt.append("Research's simple analogy (carry it forward or refine it, but keep it the one analogy): ")
                .append(research.simpleAnalogy()).append('\n');
        prompt.append("Research's beginner explanation: ").append(research.beginnerExplanation()).append('\n');
        if (!research.codeExampleIdeas().isEmpty()) {
            prompt.append("Candidate example ideas (pick exactly one as core_example): ")
                    .append(String.join("; ", research.codeExampleIdeas())).append('\n');
        }
        if (!research.commonMisconceptions().isEmpty()) {
            prompt.append("Candidate common misconceptions (pick the most memorable one as the primary ")
                    .append("common_mistakes entry): ")
                    .append(String.join("; ", research.commonMisconceptions())).append('\n');
        }
        if (!research.safetyNotes().isEmpty()) {
            prompt.append("Safety notes to carry directly into what_to_avoid_saying: ")
                    .append(String.join("; ", research.safetyNotes())).append('\n');
        }

        prompt.append('\n')
                .append("Guidance:\n")
                .append("- If several candidate concepts are equally strong with no obvious single throughline, ")
                .append("still pick one, and name what you set aside in confidence_notes.flagged_uncertainties.\n")
                .append("- what_to_avoid_saying should be exactly the safety notes above, carried through ")
                .append("unchanged; an empty array if none were given.\n")
                .append("- confidence_notes.overall_confidence should reflect how clear-cut the narrowing choice ")
                .append("was, not the correctness of the underlying facts (that was research's job).\n");

        if (topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT) {
            prompt.append('\n')
                    .append("This topic is being revised after a prior attempt scored ")
                    .append(topicMemory.performanceScore());
            if (topicMemory.notes() != null && !topicMemory.notes().isBlank()) {
                prompt.append(", with the following noted issue: ").append(topicMemory.notes());
            }
            prompt.append(". common_mistakes should reflect what is still actually a risk, not repeat a mistake ")
                    .append("the previous revision may already have fixed. The lesson should visibly address the ")
                    .append("noted issue.\n");
        }

        return prompt.toString();
    }
}
