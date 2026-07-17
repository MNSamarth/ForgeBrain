package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * Voice Generation's output: rendered narration audio per scene, plus the real measured
 * timing that supersedes the storyboard's word-count estimate from this point in the
 * pipeline forward (see renderer/voice-spec.md Section 4). Mirrors
 * {@code renderer/voice-schema.json}.
 *
 * @see <a href="../../../../../../../../renderer/voice-schema.json">renderer/voice-schema.json</a>
 */
public record VoiceResult(
        String topicId,
        String topicTitle,
        VoiceProfile voiceProfile,
        List<SceneAudio> scenes,
        double totalEstimatedDurationSeconds,
        double totalActualDurationSeconds,
        double totalDurationDriftSeconds,
        boolean driftExceedsThreshold,
        double driftThresholdSeconds,
        AudioFormat audioFormat,
        int sampleRateHz,
        ConfidenceNotes confidenceNotes,
        String voiceVersion,
        Instant generatedAt,
        String basedOnStoryboardVersion
) {

    public record VoiceProfile(String voiceId, String languageCode, double speakingRate, double pitch) {
    }

    public record SceneAudio(
            String sceneId,
            String audioFileUri,
            double estimatedDurationSeconds,
            double actualDurationSeconds,
            double durationDriftSeconds,
            List<WordTiming> wordTimings
    ) {
    }

    public record WordTiming(String word, double startTime, double endTime) {
    }

    public enum AudioFormat {
        AUDIO_MPEG, AUDIO_WAV, AUDIO_OGG
    }
}
