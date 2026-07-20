package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.util.Locale;

/**
 * Converts a {@link SubtitleTimeline} into standard SubRip ({@code .srt}) text, the format
 * FFmpeg's {@code subtitles} filter (backed by libass) consumes for burn-in. Pure text
 * generation — {@link FfmpegRenderEngine} is the only caller that actually writes the result to
 * disk.
 */
final class SrtWriter {

    private SrtWriter() {
    }

    static String toSrt(SubtitleTimeline timeline) {
        StringBuilder srt = new StringBuilder();
        for (SubtitleTimeline.SubtitleCue cue : timeline.cues()) {
            srt.append(cue.order()).append('\n')
                    .append(timecode(cue.startTime())).append(" --> ").append(timecode(cue.endTime())).append('\n')
                    .append(cue.text()).append('\n')
                    .append('\n');
        }
        return srt.toString();
    }

    private static String timecode(double seconds) {
        long totalMillis = Math.round(seconds * 1000);
        long hours = totalMillis / 3_600_000;
        long minutes = (totalMillis % 3_600_000) / 60_000;
        long secs = (totalMillis % 60_000) / 1000;
        long millis = totalMillis % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }
}
