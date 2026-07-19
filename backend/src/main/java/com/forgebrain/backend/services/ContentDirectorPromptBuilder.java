package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;

/**
 * Builds the prompt text sent to Vertex AI for the content director stage. Grounds the request
 * entirely in the committed {@link Lesson} — the seven decision areas in
 * brain/content-director-spec.md Section 5 (hook, teaching posture, emotional goal, pacing,
 * visual strategy, code framing, CTA) are judgment calls over content the lesson stage already
 * finalized; nothing here invents new topic facts or changes the lesson's concept, example, or
 * takeaway. Asks for exactly the fields in {@link VertexAiContentStrategy}; every other {@code
 * ContentStrategy} field ({@code topicId}, {@code topicTitle}, {@code targetDurationSeconds},
 * and the version/traceability fields) is assembled deterministically by {@link
 * VertexAiContentDirectorServiceImpl}.
 *
 * <p>Enum values are requested in underscore form (matching the Java enum constant names in
 * {@link com.forgebrain.backend.models.ContentStrategy}) rather than the hyphenated form used in
 * {@code brain/content-director-schema.json}, because the shared {@code ObjectMapper}'s
 * case-insensitive enum matching does not also translate hyphens to underscores — see {@link
 * com.forgebrain.backend.config.JacksonConfig}.
 */
final class ContentDirectorPromptBuilder {

    private ContentDirectorPromptBuilder() {
    }

    static String build(Lesson lesson, MemoryState.TopicRecord topicMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are directing how a committed lesson should be presented in a short-form Java ")
                .append("programming educational video. You decide HOW it is taught: hook, pacing, tone, and ")
                .append("visuals. You never write dialogue, on-screen text copy, or change the lesson's own ")
                .append("content.\n\n")
                .append("Return ONLY a single valid JSON object with exactly these fields and no others. ")
                .append("Every enum value below MUST be written exactly as shown, using underscores, never ")
                .append("hyphens:\n")
                .append("{\n")
                .append("  \"hook_type\": one of BEGINNER_MISTAKE | MYTH | QUESTION | CHALLENGE | ")
                .append("INTERVIEW_QUESTION | BEFORE_VS_AFTER | HIDDEN_FEATURE | COMMON_BUG | ")
                .append("PRODUCTIVITY_TIP | PERFORMANCE_COMPARISON,\n")
                .append("  \"hook_reason\": string,\n")
                .append("  \"teaching_style\": one of EXPLAIN_FIRST | CODE_FIRST | ANALOGY_FIRST | ")
                .append("VISUAL_FIRST | QUESTION_FIRST,\n")
                .append("  \"teaching_style_reason\": string,\n")
                .append("  \"emotional_goal\": one of SURPRISE | CURIOSITY | CONFIDENCE | RELIEF | ")
                .append("SATISFACTION,\n")
                .append("  \"emotional_goal_reason\": string,\n")
                .append("  \"pacing\": one of FAST | MEDIUM | SLOW,\n")
                .append("  \"pacing_reason\": string,\n")
                .append("  \"scene_pacing\": array of 2 to 6 { \"scene\": string, \"duration_seconds\": number },\n")
                .append("  \"visual_style\": one of FULL_SCREEN_TYPOGRAPHY | CODE_ANIMATION | DIAGRAM | ")
                .append("CURSOR_MOVEMENT | HIGHLIGHT_ANIMATION | SPLIT_SCREEN | TIMELINE,\n")
                .append("  \"supporting_visuals\": array of 0 to 2 strings,\n")
                .append("  \"visual_style_reason\": string,\n")
                .append("  \"code_style\": one of MINIMAL_EXAMPLE | COMPARISON_EXAMPLE | BUG_EXAMPLE | ")
                .append("OPTIMIZATION_EXAMPLE | INTERVIEW_EXAMPLE,\n")
                .append("  \"code_style_reason\": string,\n")
                .append("  \"cta_style\": one of FOLLOW | SAVE | COMMENT | TRY_THIS_YOURSELF | ")
                .append("NEXT_LESSON_TEASER,\n")
                .append("  \"cta_reason\": string,\n")
                .append("  \"retention_goal\": string,\n")
                .append("  \"estimated_watch_time\": integer (seconds, at or slightly below the target ")
                .append("duration),\n")
                .append("  \"confidence_notes\": { \"overall_confidence\": \"high\" | \"medium\" | \"low\", ")
                .append("\"flagged_uncertainties\": array of strings (may be empty) }\n")
                .append("}\n\n")
                .append("Do not include markdown formatting, code fences, or any text outside the JSON object.\n\n")
                .append("Rules (brain/content-director-spec.md Sections 5-7):\n")
                .append("- Decide WHICH hook/style/visual/CTA and WHY, never the exact words — no dialogue, no ")
                .append("scripted lines, no on-screen text copy.\n")
                .append("- code_style frames the lesson's own core_example; do not choose a different example.\n")
                .append("- scene_pacing may resequence the lesson's step_by_step_explanation for storytelling ")
                .append("effect, but must still cover every concept the lesson commits to, and should sum to ")
                .append("slightly less than the target duration.\n")
                .append("- Every *_reason field must name the specific lesson signal that drove the choice ")
                .append("(e.g. a common_mistakes entry, the core_example's contrast, the strength of the ")
                .append("analogy) — never just restate the decision.\n\n")
                .append("Lesson for topic: ").append(lesson.topicTitle()).append('\n')
                .append("Lesson objective: ").append(lesson.lessonObjective()).append('\n')
                .append("Lesson's own teaching_style signal (a starting point, not a constraint): ")
                .append(lesson.teachingStyle()).append('\n')
                .append("Audience level: ").append(lesson.audienceLevel()).append('\n')
                .append("Target duration: ").append(lesson.targetDurationSeconds()).append(" seconds\n")
                .append("Beginner takeaway: ").append(lesson.beginnerTakeaway()).append('\n')
                .append("Analogy: ").append(lesson.analogy()).append('\n')
                .append("Core example: ").append(lesson.coreExample().description())
                .append(" (focus: ").append(lesson.coreExample().focusNote()).append(")\n");

        if (!lesson.commonMistakes().isEmpty()) {
            prompt.append("Common mistakes (the first is the lesson's single designated one, its strongest ")
                    .append("hook material): ").append(String.join("; ", lesson.commonMistakes())).append('\n');
        }
        if (!lesson.keyPoints().isEmpty()) {
            prompt.append("Key points, in order: ").append(String.join("; ", lesson.keyPoints())).append('\n');
        }
        if (!lesson.visualNotes().isEmpty()) {
            prompt.append("Lesson's own visual notes (the first is required; draw supporting_visuals from ")
                    .append("here if relevant): ").append(String.join("; ", lesson.visualNotes())).append('\n');
        }
        prompt.append("Lesson's retention hook line (context only — do not copy it verbatim as your hook): ")
                .append(lesson.retentionHook()).append('\n');

        if (topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT) {
            prompt.append('\n')
                    .append("This topic is being revised after a prior attempt scored ")
                    .append(topicMemory.performanceScore());
            if (topicMemory.notes() != null && !topicMemory.notes().isBlank()) {
                prompt.append(", with the following noted issue: ").append(topicMemory.notes());
            }
            prompt.append(". Choose a strategy that visibly differs from a repeat of the same approach, and ")
                    .append("say what you shifted and why in confidence_notes.flagged_uncertainties.\n");
        }

        return prompt.toString();
    }
}
