package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class SrtWriterTest {

    @Test
    void writesSequentialBlocksWithTimecodesAndTextSeparatedByBlankLines() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, List.of(
                new SubtitleTimeline.SubtitleCue(1, "scene-1", 0.0, 2.0, "Hello there.", List.of()),
                new SubtitleTimeline.SubtitleCue(2, "scene-2", 2.0, 4.5, "Goodbye now.", List.of())
        ), 4.5, "1.0.0-heuristic");

        String srt = SrtWriter.toSrt(timeline);

        assertThat(srt).isEqualTo(
                "1\n00:00:00,000 --> 00:00:02,000\nHello there.\n\n"
                        + "2\n00:00:02,000 --> 00:00:04,500\nGoodbye now.\n\n");
    }

    @Test
    void formatsTimecodesAcrossMinuteAndHourBoundaries() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, List.of(
                new SubtitleTimeline.SubtitleCue(1, "scene-1", 61.5, 3661.25, "Long cue.", List.of())
        ), 3661.25, "1.0.0-heuristic");

        String srt = SrtWriter.toSrt(timeline);

        assertThat(srt).contains("00:01:01,500 --> 01:01:01,250");
    }

    @Test
    void producesEmptyStringForAnEmptyTimeline() {
        SubtitleTimeline timeline = new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, List.of(),
                0.0, "1.0.0-heuristic");

        assertThat(SrtWriter.toSrt(timeline)).isEmpty();
    }
}
