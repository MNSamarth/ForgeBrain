package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.config.TextToSpeechConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * This sandbox has no Application Default Credentials configured, so {@code
 * TextToSpeechClient.create()} always fails here — exactly the scenario {@link
 * GoogleCloudTextToSpeechVoiceServiceImpl} is built to handle gracefully. These tests exercise
 * that fallback path for real, the same way {@code CloudConnectivityCheckerImplTest} exercises
 * "no credentials" for Vertex AI, rather than mocking the Google SDK client class directly.
 */
class GoogleCloudTextToSpeechVoiceServiceImplTest {

    @TempDir
    Path tempDir;

    private Storyboard storyboard;
    private RenderingConfig renderingConfig;

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
        var research = new com.forgebrain.backend.services.ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        Script script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);

        renderingConfig = new RenderingConfig("ffmpeg", "ffprobe", tempDir.resolve("renders").toString(),
                tempDir.resolve("voiceover").toString(), tempDir.resolve("assets").toString());
    }

    private static TextToSpeechConfig config() {
        return new TextToSpeechConfig(true, false, "en-US", "en-US-Neural2-D", 1.0, 0.0);
    }

    private static TextToSpeechConfig strictConfig() {
        return new TextToSpeechConfig(true, true, "en-US", "en-US-Neural2-D", 1.0, 0.0);
    }

    private static VoiceResult.VoiceProfile voiceProfile() {
        return new VoiceResult.VoiceProfile("en-US-Neural2-D", "en-US", 1.0, 0.0);
    }

    @Test
    void fallsBackToTheProvidedVoiceServiceWhenNoCredentialsAreAvailable() {
        VoiceResult sentinel = new VoiceResult("java-for-loop", "The For Loop", voiceProfile(), List.of(), 10, 10,
                0, false, 2.0, VoiceResult.AudioFormat.AUDIO_WAV, 44100,
                new ConfidenceNotes(ConfidenceLevel.LOW, List.of("stub"), List.of()), "stub-version", Instant.now(),
                "1.0.0");
        VoiceService stubFallback = (sb, profile) -> sentinel;

        GoogleCloudTextToSpeechVoiceServiceImpl service = new GoogleCloudTextToSpeechVoiceServiceImpl(config(),
                renderingConfig, new FfmpegProcessRunner(renderingConfig), stubFallback);

        VoiceResult result = service.generateVoice(storyboard, voiceProfile());

        assertThat(result).isSameAs(sentinel);
    }

    @Test
    void neverThrowsEvenThoughSynthesisFailsInThisEnvironment() {
        GoogleCloudTextToSpeechVoiceServiceImpl service = new GoogleCloudTextToSpeechVoiceServiceImpl(config(),
                renderingConfig, new FfmpegProcessRunner(renderingConfig),
                new VoiceServiceImpl(renderingConfig, new FfmpegProcessRunner(renderingConfig)));

        VoiceResult result = service.generateVoice(storyboard, voiceProfile());

        assertThat(result.topicId()).isEqualTo("java-for-loop");
        assertThat(result.scenes()).hasSize(storyboard.scenes().size());
    }

    // ----------------------------------------------------------------------- strict cloud mode

    @Test
    void throwsInsteadOfFallingBackWhenStrictAndSynthesisFails() {
        VoiceService fallbackThatShouldNeverBeCalled = (sb, profile) -> {
            throw new AssertionError("strict mode must not consult the fallback VoiceService");
        };
        GoogleCloudTextToSpeechVoiceServiceImpl service = new GoogleCloudTextToSpeechVoiceServiceImpl(strictConfig(),
                renderingConfig, new FfmpegProcessRunner(renderingConfig), fallbackThatShouldNeverBeCalled);

        assertThatThrownBy(() -> service.generateVoice(storyboard, voiceProfile()))
                .isInstanceOf(com.forgebrain.backend.exceptions.RenderExecutionException.class)
                .hasMessageContaining("forgebrain.text-to-speech.strict is true");
    }

    @Test
    void nonStrictModeStillFallsBackAsTheIntentionalLocalDevBehavior() {
        VoiceResult sentinel = new VoiceResult("java-for-loop", "The For Loop", voiceProfile(), List.of(), 10, 10,
                0, false, 2.0, VoiceResult.AudioFormat.AUDIO_WAV, 44100,
                new ConfidenceNotes(ConfidenceLevel.LOW, List.of("stub"), List.of()), "stub-version", Instant.now(),
                "1.0.0");
        VoiceService stubFallback = (sb, profile) -> sentinel;
        GoogleCloudTextToSpeechVoiceServiceImpl service = new GoogleCloudTextToSpeechVoiceServiceImpl(config(),
                renderingConfig, new FfmpegProcessRunner(renderingConfig), stubFallback);

        VoiceResult result = service.generateVoice(storyboard, voiceProfile());

        assertThat(result).isSameAs(sentinel);
    }
}
