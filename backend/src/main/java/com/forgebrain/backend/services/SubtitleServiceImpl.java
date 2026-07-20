package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.SubtitleResult.ReconciledSegment;
import com.forgebrain.backend.models.SubtitleResult.SafeRegion;
import com.forgebrain.backend.models.SubtitleResult.SceneSubtitles;
import com.forgebrain.backend.models.SubtitleResult.SceneSubtitles.ReconciliationMethod;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.models.VoiceResult.SceneAudio;
import com.forgebrain.backend.models.VoiceResult.WordTiming;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Real {@link SubtitleService} implementation: reconciles the storyboard's word-count-estimated
 * subtitle timing against {@link VoiceResult}'s real, measured audio timing, per {@code
 * renderer/subtitle-spec.md} Section 4's two reconciliation methods.
 *
 * <p>Both methods share one idea — <b>real per-scene duration is authoritative, estimated
 * duration is not</b> — so this class first builds a cumulative reel timeline from each scene's
 * real {@link SceneAudio#actualDurationSeconds()} (not the storyboard's estimate), then places
 * each scene's reconciled segments within that scene's real window:
 *
 * <ul>
 *   <li><b>Word-alignment</b> (preferred) — when a scene's {@code wordTimings} is populated,
 *       each original subtitle segment's word count determines how many consecutive word
 *       timings it consumes; the segment's real start/end come directly from the first and last
 *       consumed word's timestamps, offset into the scene's real window.</li>
 *   <li><b>Proportional-estimate</b> (fallback) — when {@code wordTimings} is empty (which is
 *       what {@link VoiceServiceImpl}'s current fallback path always reports, honestly, since no
 *       real TTS alignment data exists yet), each segment's original scene-relative timing is
 *       scaled by {@code actualDurationSeconds / estimatedDurationSeconds} and offset into the
 *       scene's real window — preserving relative timing, per spec.</li>
 * </ul>
 */
@Component
public class SubtitleServiceImpl implements SubtitleService {

    private static final String SUBTITLE_VERSION = "1.0.0-heuristic";
    private static final double SAFE_REGION_TOP_PERCENT = 10.0;
    private static final double SAFE_REGION_BOTTOM_PERCENT = 18.0;

    @Override
    public SubtitleResult generateSubtitles(Storyboard storyboard, VoiceResult voiceResult) {
        Map<String, SceneAudio> audioByScene = new HashMap<>();
        for (SceneAudio sceneAudio : voiceResult.scenes()) {
            audioByScene.put(sceneAudio.sceneId(), sceneAudio);
        }

        List<SceneSubtitles> sceneSubtitles = new ArrayList<>();
        List<String> flaggedFallbackScenes = new ArrayList<>();
        double cursor = 0.0;

        for (Scene scene : storyboard.scenes()) {
            SceneAudio sceneAudio = audioByScene.get(scene.sceneId());
            double realSceneStart = round(cursor);
            double realDuration = sceneAudio != null ? sceneAudio.actualDurationSeconds() : scene.duration();
            cursor = realSceneStart + realDuration;

            boolean hasWordTimings = sceneAudio != null && !sceneAudio.wordTimings().isEmpty();
            List<ReconciledSegment> segments;
            ReconciliationMethod method;
            if (hasWordTimings) {
                segments = alignByWords(scene, sceneAudio, realSceneStart);
                method = ReconciliationMethod.WORD_ALIGNMENT;
            } else {
                segments = scaleProportionally(scene, sceneAudio, realSceneStart);
                method = ReconciliationMethod.PROPORTIONAL_ESTIMATE;
                flaggedFallbackScenes.add(scene.sceneId());
            }

            sceneSubtitles.add(new SceneSubtitles(scene.sceneId(), method, segments));
        }

        double totalDuration = round(cursor);
        SubtitleResult.Format format = storyboard.subtitleStyle() == Storyboard.SubtitleStyle.KARAOKE_HIGHLIGHT
                && flaggedFallbackScenes.size() < storyboard.scenes().size()
                ? SubtitleResult.Format.ASS
                : SubtitleResult.Format.SRT;

        List<String> flagged = new ArrayList<>();
        if (!flaggedFallbackScenes.isEmpty()) {
            flagged.add("Scenes using the proportional-estimate fallback (no real word timings available): "
                    + String.join(", ", flaggedFallbackScenes));
        }
        if (voiceResult.driftExceedsThreshold()) {
            flagged.add("Underlying VoiceResult reported drift exceeding its threshold; subtitle timing"
                    + " still reconciles correctly against the real audio, but the audio itself may need review.");
        }

        return new SubtitleResult(
                storyboard.topicId(),
                storyboard.topicTitle(),
                storyboard.subtitleStyle(),
                format,
                null,
                new SafeRegion(SAFE_REGION_TOP_PERCENT, SAFE_REGION_BOTTOM_PERCENT),
                sceneSubtitles,
                totalDuration,
                new ConfidenceNotes(flaggedFallbackScenes.isEmpty() ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM,
                        flagged, List.of()),
                SUBTITLE_VERSION,
                Instant.now(),
                storyboard.storyboardVersion(),
                voiceResult.voiceVersion()
        );
    }

    private List<ReconciledSegment> alignByWords(Scene scene, SceneAudio sceneAudio, double realSceneStart) {
        List<WordTiming> wordTimings = sceneAudio.wordTimings();
        List<ReconciledSegment> segments = new ArrayList<>();
        int wordCursor = 0;

        for (Scene.TimedSubtitleSegment segment : scene.subtitleSegments()) {
            int wordCount = countWords(segment.text());
            if (wordCursor + wordCount > wordTimings.size()) {
                segments.add(scaleSegmentProportionally(scene, segment, sceneAudio, realSceneStart));
                continue;
            }
            WordTiming firstWord = wordTimings.get(wordCursor);
            WordTiming lastWord = wordTimings.get(wordCursor + wordCount - 1);
            segments.add(new ReconciledSegment(
                    segment.text(),
                    round(realSceneStart + firstWord.startTime()),
                    round(realSceneStart + lastWord.endTime()),
                    segment.emphasisWords()));
            wordCursor += wordCount;
        }
        return segments;
    }

    private List<ReconciledSegment> scaleProportionally(Scene scene, SceneAudio sceneAudio, double realSceneStart) {
        List<ReconciledSegment> segments = new ArrayList<>();
        for (Scene.TimedSubtitleSegment segment : scene.subtitleSegments()) {
            segments.add(scaleSegmentProportionally(scene, segment, sceneAudio, realSceneStart));
        }
        return segments;
    }

    private ReconciledSegment scaleSegmentProportionally(Scene scene, Scene.TimedSubtitleSegment segment,
            SceneAudio sceneAudio, double realSceneStart) {
        double estimatedSceneDuration = scene.duration();
        double actualSceneDuration = sceneAudio != null ? sceneAudio.actualDurationSeconds() : estimatedSceneDuration;
        double scaleFactor = estimatedSceneDuration > 0 ? actualSceneDuration / estimatedSceneDuration : 1.0;

        double relativeStart = segment.startTime() - scene.startTime();
        double relativeEnd = segment.endTime() - scene.startTime();

        return new ReconciledSegment(
                segment.text(),
                round(realSceneStart + relativeStart * scaleFactor),
                round(realSceneStart + relativeEnd * scaleFactor),
                segment.emphasisWords());
    }

    private static int countWords(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
