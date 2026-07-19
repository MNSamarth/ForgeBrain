package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;

/**
 * Builds the prompt text sent to Vertex AI for the script stage. Grounds the request in a
 * committed {@link Lesson} and the binding {@link ContentStrategy} it must follow — see
 * brain/script-spec.md Section 3 for the hook_type/teaching_style/pacing/code_style/cta_style
 * mapping table this prompt enforces. Nothing here invents new topic facts, a different example,
 * or a different strategy than what was already decided upstream; the model's only job is
 * turning already-committed content into short, natural, spoken sentences (Section 6).
 *
 * <p>Asks for exactly the fields in {@link VertexAiScriptContent}. Every other {@code Script}
 * field is assembled deterministically by {@link VertexAiScriptServiceImpl}: {@code hookType}
 * and {@code teachingStyle} are echoed from the {@link ContentStrategy} verbatim (script-spec.md
 * Section 3 requires they "must match exactly" — never a model choice), {@code
 * codeNarration.codeSnippet} is carried from the lesson's own {@code core_example.code_sketch}
 * rather than regenerated (avoiding any risk of the model altering the actual on-screen code),
 * and {@code fullSpokenScript}/{@code wordCount}/{@code estimatedDurationSeconds}/{@code
 * subtitleSegments} are computed from the model's structured fields rather than requested
 * directly, so they are guaranteed internally consistent (script-spec.md Section 9) instead of
 * relying on the model to keep four derived values in agreement with each other.
 */
final class ScriptPromptBuilder {

    private ScriptPromptBuilder() {
    }

    static String build(Lesson lesson, ContentStrategy contentStrategy) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are writing the actual spoken narration and on-screen text for a short-form Java ")
                .append("programming educational video. A hook type, teaching style, and pacing have ALREADY ")
                .append("been decided for you — you must write to them, not choose your own.\n\n")
                .append("Return ONLY a single valid JSON object with exactly these fields and no others. Enum ")
                .append("values below MUST use underscores exactly as shown (not hyphens):\n")
                .append("{\n")
                .append("  \"hook\": string (the actual spoken opening line(s)),\n")
                .append("  \"intro_line\": string (one short bridging line from the hook into the teaching body),\n")
                .append("  \"main_script\": array of 2 to 4 { \"beat\": string, \"spoken_line\": string },\n")
                .append("  \"code_narration\": { \"spoken_lines\": array of 1 to 3 strings, \"focus_line\": ")
                .append("string (the exact line or fragment of the code below to highlight while this plays) },\n")
                .append("  \"recap_line\": string (one spoken sentence, the spoken version of the beginner ")
                .append("takeaway below),\n")
                .append("  \"cta_line\": string (the actual spoken call-to-action),\n")
                .append("  \"scene_text\": array of 2 to 5 { \"scene_reference\": string, \"text\": string } ")
                .append("(short on-screen keyword overlays, not full sentences, one per scene below),\n")
                .append("  \"tone\": one of ENERGETIC | CALM_CONFIDENT | PLAYFUL | DIRECT_AND_PUNCHY | ")
                .append("WARM_ENCOURAGING,\n")
                .append("  \"confidence_notes\": { \"overall_confidence\": \"high\" | \"medium\" | \"low\", ")
                .append("\"flagged_uncertainties\": array of strings (may be empty) }\n")
                .append("}\n\n")
                .append("Do not include markdown formatting, code fences, or any text outside the JSON object.\n\n")
                .append("Delivery rules (brain/script-spec.md Section 6):\n")
                .append("- Short sentences — most lines should run 8-16 words.\n")
                .append("- Clear, active verbs. Avoid passive or abstract phrasing.\n")
                .append("- Simple transitions (but, so, and then), not formal connectives.\n")
                .append("- Every line must sound like something a person would actually say out loud, not text ")
                .append("written to be read.\n")
                .append("- Teach nothing new beyond what is given below. Do not introduce a second concept, a ")
                .append("different example, or facts not present in the lesson.\n\n")
                .append("Binding strategy this script MUST follow (brain/script-spec.md Section 3):\n")
                .append("- hook_type = ").append(contentStrategy.hookType()).append(": the hook field's actual ")
                .append("content must be structurally that type (e.g. a mistake-family hook opens with the ")
                .append("mistake itself; a question hook opens with the literal question; a myth hook states, ")
                .append("then is about to undercut, the misconception).\n")
                .append("- teaching_style = ").append(contentStrategy.teachingStyle()).append(": shapes opening ")
                .append("posture — CODE_FIRST means the code narration effectively opens right after the hook; ")
                .append("ANALOGY_FIRST means the lesson's analogy appears in intro_line or the first ")
                .append("main_script beat, ahead of any code; QUESTION_FIRST means the hook itself is an open ")
                .append("question the script later answers.\n")
                .append("- pacing = ").append(contentStrategy.pacing()).append(": FAST pacing means shorter, ")
                .append("punchier beats; SLOW pacing allows a beat more room to land.\n")
                .append("- code_style = ").append(contentStrategy.codeStyle()).append(": BUG_EXAMPLE narrates ")
                .append("the failure directly; OPTIMIZATION_EXAMPLE narrates why the approach is better, not ")
                .append("just what it does; other styles narrate the example plainly.\n")
                .append("- cta_style = ").append(contentStrategy.ctaStyle()).append(": determines cta_line's ")
                .append("content directly (e.g. COMMENT asks a question inviting a reply; SAVE frames the ")
                .append("content as worth returning to).\n")
                .append("- emotional_goal = ").append(contentStrategy.emotionalGoal()).append(": informs tone.\n");

        if (!contentStrategy.scenePacing().isEmpty()) {
            prompt.append("- Scenes to write scene_text for, in order: ");
            prompt.append(contentStrategy.scenePacing().stream()
                    .map(entry -> entry.scene() + " (~" + entry.durationSeconds() + "s)")
                    .reduce((a, b) -> a + "; " + b).orElse(""));
            prompt.append('\n');
        }

        prompt.append('\n')
                .append("Lesson for topic: ").append(lesson.topicTitle()).append('\n')
                .append("Lesson objective: ").append(lesson.lessonObjective()).append('\n')
                .append("Audience level: ").append(lesson.audienceLevel()).append('\n')
                .append("Target duration: ").append(lesson.targetDurationSeconds()).append(" seconds\n")
                .append("Analogy: ").append(lesson.analogy()).append('\n')
                .append("Beginner takeaway (recap_line must be a spoken version of this): ")
                .append(lesson.beginnerTakeaway()).append('\n')
                .append("Code example: ").append(lesson.coreExample().description()).append('\n')
                .append("Code to narrate (do not alter it, only refer to specific lines/fragments in ")
                .append("focus_line):\n").append(lesson.coreExample().codeSketch()).append('\n')
                .append("Code focus note: ").append(lesson.coreExample().focusNote()).append('\n');

        if (!lesson.keyPoints().isEmpty()) {
            prompt.append("Key points to cover across main_script, in order: ")
                    .append(String.join("; ", lesson.keyPoints())).append('\n');
        }
        if (!lesson.commonMistakes().isEmpty()) {
            prompt.append("Common mistakes (the first is the strongest hook material if hook_type calls for ")
                    .append("it): ").append(String.join("; ", lesson.commonMistakes())).append('\n');
        }
        if (!lesson.whatToAvoidSaying().isEmpty()) {
            prompt.append("Never say or imply any of the following: ")
                    .append(String.join("; ", lesson.whatToAvoidSaying())).append('\n');
        }

        return prompt.toString();
    }
}
