package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.SubtitleTimeline;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a {@link SubtitleTimeline} into Advanced SubStation Alpha ({@code .ass}) text —
 * replaces {@link SrtWriter} as the format {@link RenderCommandBuilder} actually burns in. Plain
 * SubRip has no way to color individual words; per-word emphasis (mission Part 2 — word
 * highlighting) requires ASS's inline {@code {\c&HBBGGRR&}} override tags, which only work
 * through ffmpeg's {@code subtitles=} filter when the file itself is {@code .ass}, not
 * {@code .srt} — verified against a real ffmpeg/libass build before being encoded here (see
 * commit history). {@link SrtWriter} is left in place, unused by the active render path, so its
 * own tests keep exercising real, valid code rather than being deleted outright.
 */
final class AssSubtitleWriter {

    private static final int FONT_SIZE = 54;
    private static final String EMPHASIS_OPEN = "{\\c&H00FFFF&}";
    private static final String EMPHASIS_CLOSE = "{\\c&HFFFFFF&}";

    private AssSubtitleWriter() {
    }

    static String toAss(SubtitleTimeline timeline, int videoWidth, int videoHeight) {
        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n")
                .append("ScriptType: v4.00+\n")
                .append("PlayResX: ").append(videoWidth).append('\n')
                .append("PlayResY: ").append(videoHeight).append("\n\n")
                .append("[V4+ Styles]\n")
                .append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, "
                        + "BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, "
                        + "BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                .append("Style: Default,Arial,").append(FONT_SIZE)
                .append(",&H00FFFFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,1,3,0,2,60,60,140,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        for (SubtitleTimeline.SubtitleCue cue : timeline.cues()) {
            ass.append("Dialogue: 0,").append(timecode(cue.startTime())).append(',')
                    .append(timecode(cue.endTime())).append(",Default,,0,0,0,,")
                    .append(withEmphasis(cue.text(), cue.emphasisWords())).append('\n');
        }
        return ass.toString();
    }

    private static String withEmphasis(String text, List<String> emphasisWords) {
        String escaped = escape(text);
        if (emphasisWords == null || emphasisWords.isEmpty()) {
            return escaped;
        }
        String result = escaped;
        for (String word : emphasisWords) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String safeWord = escape(word);
            Pattern wordPattern = Pattern.compile("(?i)\\b" + Pattern.quote(safeWord) + "\\b");
            result = wordPattern.matcher(result).replaceAll(
                    Matcher.quoteReplacement(EMPHASIS_OPEN) + "$0" + Matcher.quoteReplacement(EMPHASIS_CLOSE));
        }
        return result;
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "/").replace("{", "(").replace("}", ")").replace("\n", " ");
    }

    private static String timecode(double seconds) {
        long totalCentis = Math.round(seconds * 100);
        long hours = totalCentis / 360000;
        long minutes = (totalCentis % 360000) / 6000;
        long secs = (totalCentis % 6000) / 100;
        long centis = totalCentis % 100;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, secs, centis);
    }
}
