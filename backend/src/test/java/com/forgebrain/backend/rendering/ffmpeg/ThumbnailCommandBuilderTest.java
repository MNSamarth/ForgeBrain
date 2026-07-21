package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThumbnailCommandBuilderTest {

    private RenderPlan planWithHook(String hookText) {
        SceneRenderPlan hookScene = new SceneRenderPlan(
                "scene-1-hook", 0, 5, 5, Scene.SceneType.HOOK,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(new SceneRenderPlan.TextLayer("on-screen-text", hookText, "heading")), null, "type-on",
                List.of(1), List.of(), Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
        SubtitleTimeline subtitles = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, List.of(),
                5, "1.0.0");
        return new RenderPlan("topic", "Topic Title", new RenderPlan.VideoDimensions(1080, 1920), 30, 5,
                List.of(hookScene), new RenderPlan.FontSet("h", "b", "c"), subtitles,
                new RenderPlan.AudioPlan("voiceover/topic", "music/lofi-focus", -18.0), List.of(), List.of(),
                Storyboard.RenderStyle.DARK_MODE_IDE, Storyboard.AspectRatio.RATIO_9_16, "1.0.0", Instant.now(),
                "1.0.0");
    }

    @Test
    void buildsASingleFrameCommandFromASyntheticLavfiSourceNotTheRenderedVideo() {
        RenderPlan plan = planWithHook("THIS BUG BREAKS EVERY LOOP");

        List<String> command = ThumbnailCommandBuilder.build(plan, "ffmpeg", "thumbnail.jpg");

        assertThat(command.get(0)).isEqualTo("ffmpeg");
        assertThat(command).contains("lavfi");
        assertThat(command).containsSubsequence("-frames:v", "1");
        assertThat(command).containsSubsequence("-update", "1");
        assertThat(command.get(command.size() - 1)).isEqualTo("thumbnail.jpg");
        assertThat(command).noneMatch(arg -> arg.contains("reel.mp4"));
    }

    @Test
    void usesTheHookScenesTextAsTheHeadline() {
        RenderPlan plan = planWithHook("THIS BUG BREAKS EVERY LOOP");

        List<String> command = ThumbnailCommandBuilder.build(plan, "ffmpeg", "thumbnail.jpg");
        String filterChain = command.get(command.indexOf("-vf") + 1);

        assertThat(filterChain).contains("BUG");
        assertThat(filterChain).contains("borderw=6:bordercolor=black");
    }
}
