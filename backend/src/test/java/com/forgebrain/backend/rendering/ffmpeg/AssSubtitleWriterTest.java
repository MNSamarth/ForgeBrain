package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssSubtitleWriterTest {

    @Test
    void writesAScriptInfoAndStyleHeaderSizedToTheVideo() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, List.of(),
                5.0, "1.0.0");

        String ass = AssSubtitleWriter.toAss(timeline, 1080, 1920);

        assertThat(ass).contains("[Script Info]");
        assertThat(ass).contains("PlayResX: 1080");
        assertThat(ass).contains("PlayResY: 1920");
        assertThat(ass).contains("[V4+ Styles]");
        assertThat(ass).contains("[Events]");
    }

    @Test
    void writesOneDialogueLinePerCueWithMinuteSecondCentisecondTimecodes() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED,
                List.of(new SubtitleTimeline.SubtitleCue(1, "scene-1", 1.5, 3.25, "Hello there", List.of())),
                3.25, "1.0.0");

        String ass = AssSubtitleWriter.toAss(timeline, 1080, 1920);

        assertThat(ass).contains("Dialogue: 0,0:00:01.50,0:00:03.25,Default,,0,0,0,,Hello there");
    }

    @Test
    void wrapsEmphasisWordsInAnInlineColorOverrideTag() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.KARAOKE_HIGHLIGHT,
                List.of(new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 2, "This is a test word", List.of("test"))),
                2, "1.0.0");

        String ass = AssSubtitleWriter.toAss(timeline, 1080, 1920);

        assertThat(ass).contains("This is a {\\c&H00FFFF&}test{\\c&HFFFFFF&} word");
    }

    @Test
    void doesNotEmphasizeAnythingWhenNoEmphasisWordsAreGiven() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED,
                List.of(new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 2, "Plain caption", List.of())), 2,
                "1.0.0");

        String ass = AssSubtitleWriter.toAss(timeline, 1080, 1920);

        assertThat(ass).contains("Plain caption");
        assertThat(ass).doesNotContain("{\\c&H00FFFF&}");
    }
}
