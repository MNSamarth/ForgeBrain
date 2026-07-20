package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.RenderPlanBuilder;
import com.forgebrain.backend.rendering.RenderValidator;
import com.forgebrain.backend.services.ContentDirectorServiceImpl;
import com.forgebrain.backend.services.LessonServiceImpl;
import com.forgebrain.backend.services.ResearchServiceImpl;
import com.forgebrain.backend.services.ScriptServiceImpl;
import com.forgebrain.backend.services.StoryboardServiceImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegRenderEngineTest {

    @TempDir
    Path tempDir;

    private RenderPlan realRenderPlan() {
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
        Storyboard storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
        return new RenderPlanBuilder().build(storyboard);
    }

    private FfmpegRenderEngine engine(String ffmpegPath) {
        RenderingConfig config = new RenderingConfig(ffmpegPath, "ffprobe", tempDir.resolve("renders").toString(),
                tempDir.resolve("voiceover").toString(), tempDir.resolve("assets").toString());
        return new FfmpegRenderEngine(new RenderValidator(), new PlaceholderAssetResolver(config), config,
                new FfmpegProcessRunner(config));
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

    // --- Render input handling / failure cases -------------------------------------------------

    @Test
    void rejectsAnInvalidRenderPlanBeforeAttemptingToRender() {
        RenderPlan invalidPlan = new RenderPlan(
                "topic", "Topic", new RenderPlan.VideoDimensions(1080, 1920), 30, 10, List.of(),
                new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular"),
                new com.forgebrain.backend.rendering.SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED,
                        List.of(), 10, "1.0.0-heuristic"),
                new RenderPlan.AudioPlan("voiceover/topic", "music/lofi-focus", -18.0), List.of(), List.of(),
                Storyboard.RenderStyle.DARK_MODE_IDE, Storyboard.AspectRatio.RATIO_9_16, "1.0.0", Instant.now(),
                "1.0.0-heuristic");

        assertThatThrownBy(() -> engine("this-binary-should-never-be-invoked").render(invalidPlan))
                .isInstanceOf(RenderExecutionException.class)
                .hasMessageContaining("failed validation");
    }

    @Test
    void wrapsAMissingFfmpegBinaryInAClearException() {
        RenderPlan plan = realRenderPlan();

        assertThatThrownBy(() -> engine("this-binary-does-not-exist-forgebrain-test").render(plan))
                .isInstanceOf(RenderExecutionException.class)
                .hasMessageContaining("Could not start 'this-binary-does-not-exist-forgebrain-test'");
    }

    // --- Integration-style test for the render pipeline boundary -------------------------------

    @Test
    void rendersARealPlayableMp4WithASilentAudioFallbackWhenFfmpegIsAvailable() throws IOException {
        assumeTrue(isFfmpegAvailable(), "ffmpeg is not installed/on PATH in this environment; skipping.");

        RenderPlan plan = realRenderPlan();
        VideoPackage videoPackage = engine("ffmpeg").render(plan);

        Path videoFile = Path.of(videoPackage.videoFileUri());
        assertThat(Files.isRegularFile(videoFile)).isTrue();
        assertThat(Files.size(videoFile)).isGreaterThan(0);

        assertThat(videoPackage.topicId()).isEqualTo(plan.topicId());
        assertThat(videoPackage.topicTitle()).isEqualTo(plan.topicTitle());
        assertThat(videoPackage.durationSeconds()).isEqualTo(plan.totalDurationSeconds());
        assertThat(videoPackage.resolution()).isEqualTo("1080x1920");
        assertThat(videoPackage.aspectRatio()).isEqualTo(Storyboard.AspectRatio.RATIO_9_16);
        assertThat(videoPackage.videoCodec()).isEqualTo(VideoPackage.VideoCodec.H264);
        assertThat(videoPackage.audioCodec()).isEqualTo(VideoPackage.AudioCodec.AAC);
        assertThat(videoPackage.checksum()).isNotBlank();
        assertThat(videoPackage.fileSizeBytes()).isEqualTo(Files.size(videoFile));

        assertThat(videoPackage.thumbnailFrameUri()).isNotNull();
        assertThat(Files.isRegularFile(Path.of(videoPackage.thumbnailFrameUri()))).isTrue();
    }
}
