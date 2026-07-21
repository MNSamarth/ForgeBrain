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
                "for (int i = 0; i < 5; i++) {\n    System.out.println(i);\n}", "System.out.println(i);", "java");
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
    void includesABackgroundColorInputOversizedForCameraPanMatchingFpsAndDurationFromTheRenderStyle() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);

        // Canvas is deliberately larger than the 1080x1920 output so the pan filter (crop with a
        // time-varying offset) has room to move without ever showing an edge.
        int colorInputIndex = command.indexOf("color=c=0x0d1117:s=1120x1960:r=30:d=5.00");
        assertThat(colorInputIndex).isPositive();
        assertThat(command.get(colorInputIndex - 1)).isEqualTo("-i");
    }

    @Test
    void filterChainCropsTheOversizedCanvasDownToTheExactOutputDimensionsWithATimeVaryingPan() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("crop=w=1080:h=1920");
        assertThat(filterChain).contains("sin(2*PI*t/8)");
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
    void filterChainDrawsEveryCodeLineAndHighlightsTheFocusLineForCodeScenes() {
        RenderPlan plan = renderPlan(List.of(hookScene(), codeScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("drawtext=text='for (int i = 0; i < 5; i++) {'");
        assertThat(filterChain)
                .contains("drawtext=text='    System.out.println(i);':fontsize=30:fontcolor=0x7ee787");
        assertThat(filterChain).contains("drawtext=text='}'");
        assertThat(filterChain).contains("enable='between(t\\,5.00\\,10.00)'");
    }

    @Test
    void filterChainReferencesTheSubtitleFileByBareNameAndIncludesFadeInAndOut() {
        RenderPlan plan = renderPlan(List.of(hookScene()));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.ass", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("subtitles=subtitles.ass");
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
    void filterChainDrawsAnAccentCardAndKickerLabelForSceneTypesThatWantOne() {
        SceneRenderPlan setupScene = new SceneRenderPlan(
                "scene-1-setup", 0, 4, 4, Scene.SceneType.SETUP,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(textLayer("Before we loop...")), null, "none", List.of(1), List.of(),
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
        RenderPlan plan = renderPlan(List.of(setupScene));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("drawbox=x=50:y=");
        assertThat(filterChain).contains("color=0x58a6ff@0.16:t=fill");
        assertThat(filterChain).contains("drawtext=text='SETUP'");
        assertThat(filterChain).contains("drawtext=text='Before we loop...'");
    }

    @Test
    void filterChainRendersAFlowDiagramForScenesWithMultipleOnScreenTextItems() {
        SceneRenderPlan stepScene = new SceneRenderPlan(
                "scene-1-steps", 0, 6, 6, Scene.SceneType.STEP_BREAKDOWN,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(textLayer("JVM"), textLayer("Bytecode"), textLayer("Machine Code")), null, "none",
                List.of(1), List.of(), Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
        RenderPlan plan = renderPlan(List.of(stepScene));

        List<String> command = RenderCommandBuilder.build(plan, "ffmpeg", "reel.mp4", "subtitles.srt", null);
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("drawtext=text='JVM'");
        assertThat(filterChain).contains("drawtext=text='Bytecode'");
        assertThat(filterChain).contains("drawtext=text='Machine Code'");
        assertThat(filterChain).contains("drawtext=text='STEP BY STEP'");
        // Three stacked cards connected by two vertical bars.
        assertThat(countOccurrences(filterChain, "color=0xd29922@0.16:t=fill")).isEqualTo(3);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
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
