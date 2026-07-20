package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoiceServiceImplTest {

    @TempDir
    Path tempDir;

    private Storyboard storyboard;
    private RenderingConfig renderingConfig;
    private VoiceServiceImpl voiceService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig(
                "../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        Script script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);

        renderingConfig = new RenderingConfig("ffmpeg", "ffprobe", tempDir.resolve("renders").toString(),
                tempDir.resolve("voiceover").toString(), tempDir.resolve("assets").toString());
        voiceService = new VoiceServiceImpl(renderingConfig, new FfmpegProcessRunner(renderingConfig));
    }

    private static boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    void fallsBackToARealSilentTrackWithZeroDriftWhenNoNarrationFileExists() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        VoiceResult result = voiceService.generateVoice(storyboard, DEFAULT_PROFILE);

        assertThat(result.voiceVersion()).contains("silent-fallback");
        assertThat(result.topicId()).isEqualTo(storyboard.topicId());
        assertThat(result.driftExceedsThreshold()).isFalse();
        assertThat(result.totalDurationDriftSeconds()).isZero();
        assertThat(result.audioFormat()).isEqualTo(VoiceResult.AudioFormat.AUDIO_WAV);

        for (int i = 0; i < storyboard.scenes().size(); i++) {
            var sceneAudio = result.scenes().get(i);
            assertThat(sceneAudio.sceneId()).isEqualTo(storyboard.scenes().get(i).sceneId());
            assertThat(sceneAudio.actualDurationSeconds()).isEqualTo(sceneAudio.estimatedDurationSeconds());
            assertThat(sceneAudio.durationDriftSeconds()).isZero();
            assertThat(sceneAudio.wordTimings()).isEmpty();
        }

        Path expectedFile = tempDir.resolve("voiceover").resolve(storyboard.topicId() + ".wav");
        assertThat(Files.isRegularFile(expectedFile)).isTrue();
        assertThat(Files.size(expectedFile)).isGreaterThan(0);
        assertThat(result.scenes().get(0).audioFileUri()).isEqualTo(expectedFile.toAbsolutePath().toString());
    }

    @Test
    void measuresRealDurationAndDistributesItProportionallyWhenANarrationFileAlreadyExists() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        Path voiceoverDir = tempDir.resolve("voiceover");
        Files.createDirectories(voiceoverDir);
        Path realFile = voiceoverDir.resolve(storyboard.topicId() + ".wav");
        double realDuration = storyboard.totalDurationSeconds() * 2;
        Process ffmpegProcess = new ProcessBuilder("ffmpeg", "-y",
                "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
                "-t", String.valueOf(realDuration), "-c:a", "pcm_s16le", realFile.getFileName().toString())
                .directory(voiceoverDir.toFile())
                .redirectErrorStream(true)
                .start();
        ffmpegProcess.getInputStream().readAllBytes();
        int exitCode = waitForProcess(ffmpegProcess);
        assertThat(exitCode).as("fixture ffmpeg command exit code").isZero();

        VoiceResult result = voiceService.generateVoice(storyboard, DEFAULT_PROFILE);

        assertThat(result.voiceVersion()).contains("real-file");
        assertThat(result.totalActualDurationSeconds()).isCloseTo(realDuration, org.assertj.core.data.Offset.offset(0.5));
        assertThat(result.totalEstimatedDurationSeconds()).isEqualTo(storyboard.totalDurationSeconds());
        // Real duration is double the estimate, so total drift should be roughly +estimate.
        assertThat(result.driftExceedsThreshold()).isTrue();

        double actualSum = result.scenes().stream().mapToDouble(VoiceResult.SceneAudio::actualDurationSeconds).sum();
        assertThat(actualSum).isCloseTo(realDuration, org.assertj.core.data.Offset.offset(1.0));
        result.scenes().forEach(sceneAudio -> assertThat(sceneAudio.audioFileUri())
                .isEqualTo(realFile.toAbsolutePath().toString()));
    }

    private static int waitForProcess(Process process) {
        try {
            process.waitFor(30, TimeUnit.SECONDS);
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for fixture ffmpeg command.", e);
        }
    }

    private static final VoiceResult.VoiceProfile DEFAULT_PROFILE =
            new VoiceResult.VoiceProfile("en-US-Neural2-C", "en-US", 1.0, 0.0);
}
