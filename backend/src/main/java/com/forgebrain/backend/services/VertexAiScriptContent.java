package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.util.List;

/**
 * The subset of {@link Script} that Vertex AI is asked to generate — see
 * {@link ScriptPromptBuilder}. Deserialized from the model's JSON response by the shared
 * snake_case {@link com.fasterxml.jackson.databind.ObjectMapper} bean.
 *
 * <p>Deliberately narrower than {@link Script}: {@code hookType}/{@code teachingStyle} are not
 * requested here because they are echoed verbatim from the {@link
 * com.forgebrain.backend.models.ContentStrategy} the script is bound to, never chosen by the
 * model (script-spec.md Section 3). {@link CodeNarrationDraft} omits {@code codeSnippet}: the
 * on-screen code is carried directly from the lesson's own {@code core_example.code_sketch}, not
 * re-produced by the model. {@code fullSpokenScript}, {@code
 * wordCount}, {@code estimatedDurationSeconds}, and {@code subtitleSegments} are also absent —
 * all four are derived fields computed by {@link VertexAiScriptServiceImpl} from the fields
 * below, which guarantees they stay internally consistent (script-spec.md Section 9) rather than
 * depending on the model to keep four dependent values in agreement.
 */
record VertexAiScriptContent(
        String hook,
        String introLine,
        List<Script.ScriptBeat> mainScript,
        CodeNarrationDraft codeNarration,
        String recapLine,
        String ctaLine,
        List<Script.SceneTextEntry> sceneText,
        Script.Tone tone,
        ConfidenceNotes confidenceNotes
) {

    record CodeNarrationDraft(List<String> spokenLines, String focusLine) {
    }
}
