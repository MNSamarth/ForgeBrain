package com.forgebrain.backend.services;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.config.TextToSpeechConfig;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.models.VoiceResult.AudioFormat;
import com.forgebrain.backend.models.VoiceResult.SceneAudio;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real narration via Google Cloud Text-to-Speech (renderer/voice-spec.md Section 6, mission
 * Part 6 — "remove the silent fallback, integrate real narration"): synthesizes each scene's
 * {@code voiceoverText} as its own clip, measures its real duration via {@code ffprobe}, then
 * concatenates every clip in scene order into one combined file — preserving the existing
 * convention (see {@link VoiceServiceImpl}) that every {@link SceneAudio} in one
 * {@link VoiceResult} references the same single audio file, which {@link
 * com.forgebrain.backend.rendering.RenderPlanBuilder}'s reconciled path depends on. Falls back to
 * {@code fallback} (the silent-track {@link VoiceServiceImpl}) on any synthesis failure — a
 * missing credential, quota error, or network failure must never break rendering; it just means
 * this reel renders with the documented silent placeholder like every reel did before this class
 * existed.
 */
public class GoogleCloudTextToSpeechVoiceServiceImpl implements VoiceService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudTextToSpeechVoiceServiceImpl.class);
    private static final String VOICE_VERSION_TTS = "1.0.0-google-cloud-tts";
    private static final double DRIFT_THRESHOLD_SECONDS = 2.0;
    private static final int SAMPLE_RATE_HZ = 24000;
    private static final String DEFAULT_LANGUAGE_CODE = "en-US";

    private final TextToSpeechConfig config;
    private final RenderingConfig renderingConfig;
    private final FfmpegProcessRunner processRunner;
    private final VoiceService fallback;

    public GoogleCloudTextToSpeechVoiceServiceImpl(TextToSpeechConfig config, RenderingConfig renderingConfig,
            FfmpegProcessRunner processRunner, VoiceService fallback) {
        this.config = config;
        this.renderingConfig = renderingConfig;
        this.processRunner = processRunner;
        this.fallback = fallback;
    }

    @Override
    public VoiceResult generateVoice(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile) {
        try {
            return synthesize(storyboard, voiceProfile);
        } catch (Exception e) {
            log.warn("Google Cloud Text-to-Speech synthesis failed for topic '{}'; falling back to the silent "
                    + "placeholder track. See backend/README.md's voice pipeline notes.", storyboard.topicId(), e);
            return fallback.generateVoice(storyboard, voiceProfile);
        }
    }

    private VoiceResult synthesize(Storyboard storyboard, VoiceResult.VoiceProfile voiceProfile) throws IOException {
        Path voiceoverDirectory = Path.of(renderingConfig.voiceoverAssetsDirectory());
        Files.createDirectories(voiceoverDirectory);
        Path sceneAudioDirectory = Files.createTempDirectory(voiceoverDirectory, storyboard.topicId() + "-scenes-");

        try {
            List<Path> sceneFiles = new ArrayList<>();
            try (TextToSpeechClient client = TextToSpeechClient.create()) {
                for (Scene scene : storyboard.scenes()) {
                    sceneFiles.add(synthesizeScene(client, scene, voiceProfile, sceneAudioDirectory));
                }
            }

            Path combinedFile = voiceoverDirectory.resolve(storyboard.topicId() + ".mp3");
            concatenate(sceneFiles, combinedFile, sceneAudioDirectory);

            List<SceneAudio> scenes = new ArrayList<>();
            double totalActual = 0.0;
            for (int i = 0; i < storyboard.scenes().size(); i++) {
                Scene scene = storyboard.scenes().get(i);
                double actual = round(processRunner.probeDurationSeconds(sceneFiles.get(i), sceneAudioDirectory));
                totalActual += actual;
                scenes.add(new SceneAudio(scene.sceneId(), combinedFile.toAbsolutePath().toString(),
                        scene.duration(), actual, round(actual - scene.duration()), List.of()));
            }

            double estimatedTotal = storyboard.totalDurationSeconds();
            double totalDrift = round(totalActual - estimatedTotal);
            boolean driftExceedsThreshold = Math.abs(totalDrift) > DRIFT_THRESHOLD_SECONDS;

            log.info("Synthesized real Google Cloud Text-to-Speech narration for topic '{}': {} scenes, {}s total.",
                    storyboard.topicId(), scenes.size(), round(totalActual));

            return new VoiceResult(
                    storyboard.topicId(),
                    storyboard.topicTitle(),
                    voiceProfile,
                    scenes,
                    estimatedTotal,
                    round(totalActual),
                    totalDrift,
                    driftExceedsThreshold,
                    DRIFT_THRESHOLD_SECONDS,
                    AudioFormat.AUDIO_MPEG,
                    SAMPLE_RATE_HZ,
                    new ConfidenceNotes(driftExceedsThreshold ? ConfidenceLevel.LOW : ConfidenceLevel.HIGH,
                            driftExceedsThreshold
                                    ? List.of("Total synthesized duration drift (" + totalDrift + "s) exceeds the "
                                            + DRIFT_THRESHOLD_SECONDS + "s threshold.")
                                    : List.of(),
                            List.of()),
                    VOICE_VERSION_TTS,
                    Instant.now(),
                    storyboard.storyboardVersion()
            );
        } finally {
            deleteRecursively(sceneAudioDirectory);
        }
    }

    private Path synthesizeScene(TextToSpeechClient client, Scene scene, VoiceResult.VoiceProfile voiceProfile,
            Path sceneAudioDirectory) throws IOException {
        SynthesisInput input = SynthesisInput.newBuilder().setText(scene.voiceoverText()).build();
        VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder()
                .setLanguageCode(languageCodeFor(voiceProfile));
        if (config.voiceName() != null && !config.voiceName().isBlank()) {
            voiceBuilder.setName(config.voiceName());
        }
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .setSpeakingRate(speakingRateFor(voiceProfile))
                .setPitch(voiceProfile.pitch())
                .build();

        SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voiceBuilder.build(), audioConfig);
        Path sceneFile = sceneAudioDirectory.resolve(scene.sceneId() + ".mp3");
        Files.write(sceneFile, response.getAudioContent().toByteArray());
        return sceneFile;
    }

    private void concatenate(List<Path> sceneFiles, Path combinedFile, Path workingDirectory) throws IOException {
        Path listFile = workingDirectory.resolve("concat-list.txt");
        StringBuilder listContents = new StringBuilder();
        for (Path sceneFile : sceneFiles) {
            listContents.append("file '").append(sceneFile.getFileName()).append("'\n");
        }
        Files.writeString(listFile, listContents.toString());

        List<String> command = List.of(processRunner.ffmpegPath(), "-y",
                "-f", "concat", "-safe", "0", "-i", listFile.getFileName().toString(),
                "-c", "copy", combinedFile.toAbsolutePath().toString());
        processRunner.run(command, workingDirectory);
    }

    private double speakingRateFor(VoiceResult.VoiceProfile voiceProfile) {
        if (voiceProfile != null && voiceProfile.speakingRate() > 0) {
            return voiceProfile.speakingRate();
        }
        return config.speakingRate() > 0 ? config.speakingRate() : 1.0;
    }

    private static String languageCodeFor(VoiceResult.VoiceProfile voiceProfile) {
        return voiceProfile != null && voiceProfile.languageCode() != null && !voiceProfile.languageCode().isBlank()
                ? voiceProfile.languageCode()
                : DEFAULT_LANGUAGE_CODE;
    }

    private static void deleteRecursively(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup of temp per-scene synthesis files
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup; a leftover temp directory doesn't affect correctness
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
