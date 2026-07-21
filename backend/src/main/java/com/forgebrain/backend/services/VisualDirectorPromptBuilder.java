package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;

/**
 * Builds the prompt text sent to Vertex AI for the Visual Director stage. Grounds the request in
 * both the committed {@link Script} (for narration/hook context) and the committed {@link
 * Storyboard} (for the fixed scene structure and timing this stage directs but never changes) —
 * asks for exactly one visual scene plan per storyboard scene, in the storyboard's own order, so
 * {@link VertexAiVisualDirectorServiceImpl} can zip the response back onto each scene by index
 * rather than trusting a model-generated scene id to match. {@code duration}/{@code scene_id} are
 * therefore deliberately NOT requested — timing is already final by the time this stage runs, and
 * asking the model to restate it only invites disagreement with the real value.
 */
final class VisualDirectorPromptBuilder {

    private VisualDirectorPromptBuilder() {
    }

    static String build(Script script, Storyboard storyboard) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Visual Director for a short-form Java programming educational video. The ")
                .append("narration and on-screen text are already final — you decide HOW each scene should look: ")
                .append("composition, camera motion, imagery, and diagram/code framing. You never change scene ")
                .append("content, order, count, or timing.\n\n")
                .append("Return ONLY a single valid JSON object with exactly these fields and no others. Every ")
                .append("enum value below MUST be written exactly as shown, using underscores, never hyphens:\n")
                .append("{\n")
                .append("  \"thumbnail_brief\": string (what the thumbnail should communicate in one glance),\n")
                .append("  \"scenes\": array of exactly ").append(storyboard.sceneCount())
                .append(" objects, one per scene below IN ORDER, each:\n")
                .append("    {\n")
                .append("      \"scene_primitive\": one of HOOK | COMPARISON | DIAGRAM | CODE | FLOW | ")
                .append("ARCHITECTURE | WALKTHROUGH | RECAP | CTA,\n")
                .append("      \"hook_intent\": string (curiosity gap for HOOK scenes, empty string otherwise),\n")
                .append("      \"visual_goal\": string (the one thing this scene's visual must communicate),\n")
                .append("      \"composition\": one of FULL_BLEED | SPLIT_SCREEN | CENTERED_CARD | ")
                .append("NESTED_BOXES | CODE_PANEL | DIAGRAM_FLOW,\n")
                .append("      \"camera_motion\": string,\n")
                .append("      \"background_style\": string,\n")
                .append("      \"foreground_elements\": array of 0 to 4 short strings,\n")
                .append("      \"text_overlay\": string (treatment direction, not exact copy),\n")
                .append("      \"code_block_hint\": string or null (null unless this scene has a code block),\n")
                .append("      \"diagram_type\": string or null (null unless composition is DIAGRAM_FLOW),\n")
                .append("      \"image_prompt\": string or null (null only if the scene is intentionally ")
                .append("text-first),\n")
                .append("      \"motion_cue\": string,\n")
                .append("      \"transition_in\": one of HARD_CUT | QUICK_FADE | SLIDE | ZOOM_PUNCH | MATCH_CUT,\n")
                .append("      \"transition_out\": one of HARD_CUT | QUICK_FADE | SLIDE | ZOOM_PUNCH | MATCH_CUT\n")
                .append("    }\n")
                .append("}\n\n")
                .append("Do not include markdown formatting, code fences, or any text outside the JSON object.\n\n")
                .append("Rules:\n")
                .append("- Bias toward visual explanation over paragraphs — prefer DIAGRAM_FLOW/SPLIT_SCREEN/")
                .append("FULL_BLEED over CENTERED_CARD wherever the scene's content is a list, comparison, or ")
                .append("process, not just a single line of prose.\n")
                .append("- A scene with a code block must use scene_primitive CODE and composition CODE_PANEL.\n")
                .append("- image_prompt should be null only when the scene is genuinely a single short line of ")
                .append("text with nothing else to show.\n")
                .append("- The scenes array length MUST equal the scene count above, in the same order.\n\n")
                .append("Topic: ").append(script.topicTitle()).append('\n')
                .append("Hook line: ").append(script.hook()).append('\n')
                .append("Render style: ").append(storyboard.renderStyle()).append('\n')
                .append("Visual style: ").append(storyboard.visualStyle()).append('\n')
                .append("Scenes, in order:\n");

        int index = 1;
        for (Scene scene : storyboard.scenes()) {
            prompt.append(index++).append(". [").append(scene.sceneType()).append("] purpose: ")
                    .append(scene.purpose()).append(" | on-screen text: ")
                    .append(scene.onScreenText().isEmpty() ? "(none)" : String.join("; ", scene.onScreenText()))
                    .append(" | has code block: ").append(scene.codeBlock() != null).append('\n');
        }

        return prompt.toString();
    }
}
