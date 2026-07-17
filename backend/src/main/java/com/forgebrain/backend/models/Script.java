package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * A reel script: the actual spoken words and on-screen text generated from a lesson and a
 * content strategy. Mirrors {@code brain/script-output-schema.json}. {@code fullSpokenScript}
 * is derived — it must equal the concatenation of {@code hook}, {@code introLine}, every
 * {@code mainScript} line, every {@code codeNarration} line, {@code recapLine}, and
 * {@code ctaLine} in delivery order (see brain/script-spec.md Section 9).
 *
 * @see <a href="../../../../../../../../brain/script-output-schema.json">brain/script-output-schema.json</a>
 */
public record Script(
        String topicId,
        String topicTitle,
        String variantId,
        String hook,
        String introLine,
        List<ScriptBeat> mainScript,
        CodeNarration codeNarration,
        String recapLine,
        String ctaLine,
        String fullSpokenScript,
        List<SceneTextEntry> sceneText,
        List<SubtitleSegment> subtitleSegments,
        int wordCount,
        double estimatedDurationSeconds,
        Tone tone,
        ContentStrategy.HookType hookType,
        ContentStrategy.TeachingStyle teachingStyle,
        ConfidenceNotes confidenceNotes,
        int targetDurationSeconds,
        Topic.Difficulty audienceLevel,
        Platform platform,
        String scriptVersion,
        Instant generatedAt,
        String basedOnLessonVersion,
        String basedOnContentDirectorVersion
) {

    public record ScriptBeat(String beat, String spokenLine) {
    }

    public record CodeNarration(List<String> spokenLines, String codeSnippet, String focusLine) {
    }

    public record SceneTextEntry(String sceneReference, String text) {
    }

    public record SubtitleSegment(
            int order,
            String text,
            SourceField sourceField,
            double estimatedDurationSeconds,
            List<String> emphasisWords
    ) {
        public enum SourceField {
            HOOK, INTRO_LINE, MAIN_SCRIPT, CODE_NARRATION, RECAP_LINE, CTA_LINE
        }
    }

    public enum Tone {
        ENERGETIC, CALM_CONFIDENT, PLAYFUL, DIRECT_AND_PUNCHY, WARM_ENCOURAGING
    }

    public enum Platform {
        YOUTUBE_SHORTS, INSTAGRAM_REELS, TIKTOK, GENERIC_VERTICAL_SHORT
    }
}
