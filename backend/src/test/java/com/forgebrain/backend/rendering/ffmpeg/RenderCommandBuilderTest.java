package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure command-construction tests — no ffmpeg process is started, matching {@link
 * RenderCommandBuilder}'s own contract.
 */
class RenderCommandBuilderTest {

    private SceneRenderPlan.TextLayer textLayer(String text) {
        return new SceneRenderPlan.TextLayer("on-screen-text", text, "heading");
    }

    private RenderPlan renderPlan(List<SceneRenderPlan> scenes) {
        double total = scenes.get(scenes.size() - 1).endTime();
        SubtitleTimeline subtitles = new SubtitleTimeline("java-for-loop", Storyboard.SubtitleStyle.BOLD_CENTERED,
                List.of(new SubtitleTimeline.SubtitleCue(1, scenes.get(0).sceneId(), 0, total, "Hi there.", List.of())),
                total, "1.0.0-heuristic");
        return new RenderPlan(
                "java-for-loop", "The For Loop", new RenderPlan.VideoDimensions(1080, 1920), 30, total, scenes,
                new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular"), subtitles,
                new RenderPlan.AudioPlan("voiceover/java-for-loop", "music/lofi-focus", -18.0), List.of(), List.of(),
                Storyboard.RenderStyle.DARK_MODE_IDE, Storyboard.AspectRatio.RATIO_9_16, "1.0.0", Instant.now(),
                "1.0.0-heuristic");
    }

    private SceneRenderPlan hookScene() {
        return new SceneRenderPlan(
                "scene-1-hook", 0, 5, 5, Scene.SceneType.HOOK,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(textLayer("THE FOR LOOP")), null, "type-on", List.of(1), List.of(),
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
    }

    private SceneRenderPlan codeScene() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer(
                "for (int i = 0; i < 5; i++) {}", "i < 5", "java");
        return new SceneRenderPlan(
                "scene-2-code-reveal", 5, 10, 5, Scene.SceneType.CODE_REVEAL,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(), codeLayer, "fade in", List.of(2),
                List.of(new RenderPlan.GlobalAssetRef(com.forgebrain.backend.rendering.AssetCategory.FONT, "JetBrainsMono-Regular")),
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
    }

    @Test
    void startsWithTheFfmpegPathAndOverwriteFlagAndEndsWithTheOutputFileName() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);

        assertThat(command.get(0)).isEqualTo("ffmpeg");
        assertThat(command.get(1)).isEqualTo("-y");
        assertThat(command.get(command.size() - 1)).isEqualTo("reel.mp4");
    }

    @Test
    void includesABackgroundColorInputMatchingDimensionsFpsAndDurationFromTheRenderStyle() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);

        int colorInputIndex = command.indexOf("color=c=0x0d1117:s=1080x1920:r=30:d=5.00");
        assertThat(colorInputIndex).isPositive();
        assertThat(command.get(colorInputIndex - 1)).isEqualTo("-i");
    }

    @Test
    void usesASilentAudioSourceWhenNoRealVoiceoverFileIsResolved() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);

        assertThat(command).contains("anullsrc=channel_layout=stereo:sample_rate=44100");
        assertThat(command).doesNotContain("data/assets/voiceover/java-for-loop.mp3");
    }

    @Test
    void usesTheRealAudioFileWhenOneIsResolved() {
        RenderPlan plan = renderPlan(List.of(hookScene()));
        Path audioPath = Path.of("data/assets/voiceover/java-for-loop.mp3");

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", audioPath);

        assertThat(command).contains(audioPath.toAbsolutePath().toString());
        assertThat(command).doesNotContain("anullsrc=channel_layout=stereo:sample_rate=44100");
    }

    @Test
    void filterChainDrawsEachSceneTextLayerWithEscapedTextAndATimedEnableWindow() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("drawtext=text='THE FOR LOOP'");
        assertThat(filterChain).contains("enable='between(t\\,0.00\\,5.00)'");
    }

    @Test
    void filterChainDrawsTheCodeLayersFocusLineForCodeScenes() {
        RenderPlan plan = renderPlan(List.of(hookScene(), codeScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("drawtext=text='i < 5'");
        assertThat(filterChain).contains("enable='between(t\\,5.00\\,10.00)'");
    }

    @Test
    void filterChainReferencesTheSubtitleFileByBareNameAndIncludesFadeInAndOut() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("subtitles=subtitles.srt:force_style=");
        assertThat(filterChain).contains("fade=t=in:st=0:d=0.3");
        assertThat(filterChain).contains("fade=t=out:st=4.70:d=0.3");
    }

    @Test
    void mapsAndEncodesWithLibx264AndAac() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);

        assertThat(command).containsSubsequence("-map", "0:v", "-map", "1:a");
        assertThat(command).containsSubsequence("-c:v", "libx264");
        assertThat(command).containsSubsequence("-c:a", "aac");
        assertThat(command).contains("-shortest");
    }

    @Test
    void escapesApostrophesAndColonsInSceneText() {
        SceneRenderPlan sceneWithTrickyText = new SceneRenderPlan(
                "scene-1-hook", 0, 3, 3, Scene.SceneType.HOOK,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(textLayer("It's a trap: watch closely")), null, "none", List.of(1), List.of(),
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
        RenderPlan plan = renderPlan(List.of(sceneWithTrickyText));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("text='It'\\''s a trap\\: watch closely'");
    }
}
