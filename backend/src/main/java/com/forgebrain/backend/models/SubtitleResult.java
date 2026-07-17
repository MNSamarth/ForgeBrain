package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * Subtitle Generation's output: final captions reconciled against {@link VoiceResult}'s real
 * audio timing, never the storyboard's original estimate (see renderer/subtitle-spec.md
 * Section 4). Mirrors {@code renderer/subtitle-schema.json}.
 *
 * @see <a href="../../../../../../../../renderer/subtitle-schema.json">renderer/subtitle-schema.json</a>
 */
public record SubtitleResult(
        String topicId,
        String topicTitle,
        Storyboard.SubtitleStyle subtitleStyle,
        Format format,
        String subtitleFileUri,
        SafeRegion safeRegion,
        List<SceneSubtitles> scenes,
        double totalDurationSeconds,
        ConfidenceNotes confidenceNotes,
        String subtitleVersion,
        Instant generatedAt,
        String basedOnStoryboardVersion,
        String basedOnVoiceVersion
) {

    public enum Format {
        SRT, VTT, ASS
    }

    public record SafeRegion(double topPercent, double bottomPercent) {
    }

    public record SceneSubtitles(String sceneId, ReconciliationMethod reconciliationMethod, List<ReconciledSegment> segments) {
        public enum ReconciliationMethod {
            WORD_ALIGNMENT, PROPORTIONAL_ESTIMATE
        }
    }

    public record ReconciledSegment(String text, double startTime, double endTime, List<String> emphasisWords) {
    }
}
