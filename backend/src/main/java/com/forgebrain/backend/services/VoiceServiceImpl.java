package com.forgebrain.backend.services;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.models.VoiceResult.AudioFormat;
import com.forgebrain.backend.models.VoiceResult.SceneAudio;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link VoiceService} implementation, and the seam a future Google Cloud Text-to-Speech
 * integration (renderer/voice-spec.md Section 6) plugs into without changing this contract.
 *
 * <p>Two exercised paths, chosen the same way as every other stage's Vertex/heuristic split in
 * this codebase — real signal when available, a documented fallback otherwise:
 *
 * <ul>
 *   <li><b>Real narration file found</b> at {@code <voiceoverAssetsDirectory>/<topicId>.{mp3,wav}}
 *       — its real duration is measured via {@code ffprobe} and distributed across scenes
 *       proportionally to each scene's estimated share of the storyboard's total (the same
 *       "proportional-estimate" idea {@code renderer/subtitle-spec.md} Section 4 uses when word
 *       timings aren't available — applied here one level up, since no real TTS engine is wired
 *       to produce per-scene files or word-level timing yet).</li>
 *   <li><b>No real file found</b> (the normal case today — no TTS provider is wired) — a real
 *       silent WAV track is synthesized via {@code ffmpeg} at exactly the storyboard's estimated
 *       total duration and written to that same conventional path, so every scene's {@code
 *       actualDurationSeconds} equals its estimate (zero drift, honestly reported) and the
 *       renderer has a genuine audio file to mix in rather than needing its own separate
 *       fallback. This is the documented fallback the mission's Part 3 asks for — not a stub.</li>
 * </ul>
 *
 * <p>Both paths report {@code wordTimings} as empty, which {@code renderer/voice-spec.md}
 * Section 8 explicitly sanctions ("word_timings may be an empty array") — {@link
 * SubtitleServiceImpl} falls back to its own proportional-estimate method in that case, per
 * {@code renderer/subtitle-spec.md} Section 4.
 */
@Component
public class VoiceServiceImpl implements VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceServiceImpl.class);
    private static final String VOICE_VERSION_REAL_FILE = "1.0.0-real-file";
    private static final String VOICE_VERSION_SILENT_FALLBACK = "1.0.0-silent-fallback";
    private static final int SAMPLE_RATE_HZ = 44100;
    private static final double DRIFT_THRESHOLD_SECONDS = 2.0;

    private final RenderingConfig renderingConfig;
    private final FfmpegProcessRunner processRunner;

    public VoiceServiceImpl(RenderingConfig renderingConfig, FfmpegProcessRunner processRunner) {
        this.renderingConfig = renderingConfig;
        this.processRunner = processRunner;
    }

    @Override
    public VoiceResult generateVoice(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile) {
        Path voiceoverDirectory = Path.of(renderingConfig.voiceoverAssetsDirectory());
        try {
            Files.createDirectories(voiceoverDirectory);
        } catch (IOException e) {
            throw new RenderExecutionException(
                    "Could not create voiceover assets directory '" + voiceoverDirectory + "'.", e);
        }

        Optional<Path> realFile = findRealNarrationFile(voiceoverDirectory, storyboard.topicId());
        if (realFile.isPresent()) {
            return buildFromRealFile(storyboard, voiceProfile, realFile.get(), voiceoverDirectory);
        }
        return buildSilentFallback(storyboard, voiceProfile, voiceoverDirectory);
    }

    private Optional<Path> findRealNarrationFile(Path voiceoverDirectory, String topicId) {
        for (String extension : new String[] {"mp3", "wav"}) {
            Path candidate = voiceoverDirectory.resolve(topicId + "." + extension);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private VoiceResult buildFromRealFile(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile,
            Path realFile, Path workingDirectory) {
        double realTotalDuration = processRunner.probeDurationSeconds(realFile, workingDirectory);
        double estimatedTotalDuration = storyboard.totalDurationSeconds();
        double scaleFactor = estimatedTotalDuration > 0 ? realTotalDuration / estimatedTotalDuration : 1.0;

        List<SceneAudio> scenes = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            double actual = round(scene.duration() * scaleFactor);
            scenes.add(new SceneAudio(scene.sceneId(), realFile.toAbsolutePath().toString(),
                    scene.duration(), actual, round(actual - scene.duration()), List.of()));
        }

        double totalDrift = round(realTotalDuration - estimatedTotalDuration);
        boolean driftExceedsThreshold = Math.abs(totalDrift) > DRIFT_THRESHOLD_SECONDS;

        List<String> flagged = new ArrayList<>();
        flagged.add("Real narration file found at '" + realFile + "'; per-scene timing is distributed"
                + " proportionally from the file's total measured duration, not independently measured per"
                + " scene (no per-scene files or word-level alignment data are available yet).");
        if (driftExceedsThreshold) {
            flagged.add("Total duration drift (" + totalDrift + "s) exceeds the " + DRIFT_THRESHOLD_SECONDS
                    + "s threshold — the real narration runs meaningfully longer or shorter than the script's"
                    + " word-count estimate.");
        }

        log.info("Using real narration file for topic '{}': {} (measured {}s vs estimated {}s).",
                storyboard.topicId(), realFile, realTotalDuration, estimatedTotalDuration);

        return new VoiceResult(
                storyboard.topicId(),
                storyboard.topicTitle(),
                voiceProfile,
                scenes,
                estimatedTotalDuration,
                round(realTotalDuration),
                totalDrift,
                driftExceedsThreshold,
                DRIFT_THRESHOLD_SECONDS,
                audioFormatFor(realFile),
                SAMPLE_RATE_HZ,
                new ConfidenceNotes(driftExceedsThreshold ? ConfidenceLevel.LOW : ConfidenceLevel.MEDIUM,
                        flagged, List.of()),
                VOICE_VERSION_REAL_FILE,
                Instant.now(),
                storyboard.storyboardVersion()
        );
    }

    private VoiceResult buildSilentFallback(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile,
            Path voiceoverDirectory) {
        Path silentFile = voiceoverDirectory.resolve(storyboard.topicId() + ".wav");
        List<String> command = List.of(processRunner.ffmpegPath(), "-y",
                "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=" + SAMPLE_RATE_HZ,
                "-t", String.format(Locale.ROOT, "%.2f", storyboard.totalDurationSeconds()),
                "-c:a", "pcm_s16le", silentFile.getFileName().toString());
        processRunner.run(command, voiceoverDirectory);

        List<SceneAudio> scenes = storyboard.scenes().stream()
                .map(scene -> new SceneAudio(scene.sceneId(), silentFile.toAbsolutePath().toString(),
                        scene.duration(), scene.duration(), 0.0, List.<VoiceResult.WordTiming>of()))
                .toList();

        log.warn("No real narration file found for topic '{}' in {}; synthesized a silent placeholder track at"
                + " {}. This is the documented fallback until a real Text-to-Speech provider is wired — see"
                + " backend/README.md's voice pipeline notes.",
                storyboard.topicId(), voiceoverDirectory, silentFile);

        return new VoiceResult(
                storyboard.topicId(),
                storyboard.topicTitle(),
                voiceProfile,
                scenes,
                storyboard.totalDurationSeconds(),
                storyboard.totalDurationSeconds(),
                0.0,
                false,
                DRIFT_THRESHOLD_SECONDS,
                AudioFormat.AUDIO_WAV,
                SAMPLE_RATE_HZ,
                new ConfidenceNotes(ConfidenceLevel.LOW, List.of(
                        "Silent placeholder audio — no real Text-to-Speech provider is wired yet (see"
                                + " TODO.md 1.8 and renderer/voice-spec.md Section 6). Every scene's"
                                + " actualDurationSeconds equals its estimate by construction; this is not a"
                                + " real measurement."), List.of()),
                VOICE_VERSION_SILENT_FALLBACK,
                Instant.now(),
                storyboard.storyboardVersion()
        );
    }

    private static AudioFormat audioFormatFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return AudioFormat.AUDIO_MPEG;
        }
        if (name.endsWith(".ogg")) {
            return AudioFormat.AUDIO_OGG;
        }
        return AudioFormat.AUDIO_WAV;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
