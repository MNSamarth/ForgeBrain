package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the complete {@code ffmpeg} argument list for one {@link RenderPlan} — pure
 * construction, no process execution and no file I/O, so it's testable without a real {@code
 * ffmpeg} binary. Every filter fragment here was verified against a real {@code ffmpeg} 8.x
 * binary before being encoded (see commit history); the escaping rules in particular
 * ({@link FfmpegTextEscaper}, the {@code \,} inside {@code between(t\,start\,end)}) are load
 * -bearing FFmpeg filtergraph syntax, not stylistic choices.
 *
 * <p>The single vertical reel template this produces: a solid, {@code RenderStyle}-driven
 * background color for the full duration (real per-scene backgrounds don't exist yet — Asset
 * Management isn't implemented), one {@code drawtext} overlay per scene text layer and per code
 * layer's focus line, subtitles burned in from an {@code .srt} file, and a short fade in/out as
 * the one "basic transition" this first version implements (every current storyboard scene uses
 * {@code HARD_CUT} between scenes already, which timed {@code enable=} windows reproduce
 * natively — see backend/README.md).
 *
 * <p>{@code subtitleFileName} and {@code outputFileName} are deliberately bare filenames, not
 * paths: {@link FfmpegRenderEngine} runs the process with its working directory set to the
 * render output folder specifically so this class never has to embed a Windows drive-letter
 * colon inside an FFmpeg filter string, which is the single most fragile part of the whole
 * command otherwise.
 */
final class RenderCommandBuilder {

    private static final String AUDIO_SAMPLE_RATE = "44100";
    private static final double FADE_DURATION_SECONDS = 0.3;

    private RenderCommandBuilder() {
    }

    static List<String> build(RenderPlan renderPlan, String ffmpegPath, String outputFileName,
            String subtitleFileName, Path resolvedAudioFilePath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");

        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("color=c=" + PlaceholderAssetResolver.backgroundColorHexFor(renderPlan.renderStyle())
                + ":s=" + renderPlan.dimensions().width() + "x" + renderPlan.dimensions().height()
                + ":r=" + renderPlan.fps() + ":d=" + formatSeconds(renderPlan.totalDurationSeconds()));

        if (resolvedAudioFilePath != null) {
            command.add("-i");
            command.add(resolvedAudioFilePath.toAbsolutePath().toString());
        } else {
            command.add("-f");
            command.add("lavfi");
            command.add("-t");
            command.add(formatSeconds(renderPlan.totalDurationSeconds()));
            command.add("-i");
            command.add("anullsrc=channel_layout=stereo:sample_rate=" + AUDIO_SAMPLE_RATE);
        }

        command.add("-vf");
        command.add(buildFilterChain(renderPlan, subtitleFileName));

        command.add("-map");
        command.add("0:v");
        command.add("-map");
        command.add("1:a");
        command.add("-c:v");
        command.add("libx264");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add("-shortest");
        command.add(outputFileName);

        return List.copyOf(command);
    }

    private static String buildFilterChain(RenderPlan renderPlan, String subtitleFileName) {
        String textColor = PlaceholderAssetResolver.textColorFor(renderPlan.renderStyle());
        List<String> filters = new ArrayList<>();

        for (SceneRenderPlan scene : renderPlan.scenes()) {
            int layerIndex = 0;
            for (SceneRenderPlan.TextLayer textLayer : scene.textLayers()) {
                filters.add(drawText(textLayer.text(), textColor, 64, 200 + layerIndex * 90,
                        scene.startTime(), scene.endTime()));
                layerIndex++;
            }
            if (scene.codeLayer() != null) {
                filters.add(drawCodeFocusLine(scene.codeLayer().focusLine(), textColor,
                        scene.startTime(), scene.endTime()));
            }
        }

        filters.add("subtitles=" + subtitleFileName + ":force_style='FontName=Arial,FontSize=18,"
                + "PrimaryColour=&HFFFFFF&,OutlineColour=&H000000&,BorderStyle=1,Outline=2,"
                + "Alignment=2,MarginV=140'");

        double fadeOutStart = Math.max(0, renderPlan.totalDurationSeconds() - FADE_DURATION_SECONDS);
        filters.add("fade=t=in:st=0:d=" + FADE_DURATION_SECONDS);
        filters.add("fade=t=out:st=" + formatSeconds(fadeOutStart) + ":d=" + FADE_DURATION_SECONDS);

        return String.join(",", filters);
    }

    private static String drawText(String text, String color, int fontSize, int y, double start, double end) {
        return "drawtext=text='" + FfmpegTextEscaper.escape(text) + "':fontsize=" + fontSize
                + ":fontcolor=" + color + ":x=(w-text_w)/2:y=" + y
                + ":enable='between(t\\," + formatSeconds(start) + "\\," + formatSeconds(end) + ")'";
    }

    private static String drawCodeFocusLine(String focusLine, String color, double start, double end) {
        return "drawtext=text='" + FfmpegTextEscaper.escape(focusLine) + "':fontsize=36:fontcolor=" + color
                + ":x=(w-text_w)/2:y=h-500:box=1:boxcolor=black@0.6:boxborderw=20"
                + ":enable='between(t\\," + formatSeconds(start) + "\\," + formatSeconds(end) + ")'";
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }
}
