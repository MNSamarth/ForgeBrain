package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dedicated, intentionally-designed thumbnail frame instead of grabbing a raw mid-video
 * frame (mission Part 7: a thumbnail should read as a thumbnail — communicate the topic/curiosity
 * in one second — not look like a random title slide). Renders one synthetic still frame: the
 * reel's hook line, bold and outlined, over a {@code RenderStyle}-driven background with a dark
 * accent band behind the text for contrast — entirely independent of the rendered video, so it
 * never depends on which frame happens to land at a given timestamp.
 *
 * <p>Prefers {@link RenderPlan#thumbnailBrief()} — the Visual Director's intentional "what should
 * this thumbnail communicate" direction — as the headline when one is available; falls back to
 * the hook scene's own on-screen text (and then the topic title) when it isn't, exactly as before
 * the Visual Director stage existed.
 */
final class ThumbnailCommandBuilder {

    private static final int MAX_LINE_CHARS = 16;
    private static final int MAX_LINES = 3;
    private static final int HEADLINE_FONT_SIZE = 84;
    private static final int LINE_SPACING = 100;
    private static final int BAND_HEIGHT = 680;

    private ThumbnailCommandBuilder() {
    }

    static List<String> build(RenderPlan renderPlan, String ffmpegPath, String outputFileName) {
        String headline = headlineFor(renderPlan);
        String backgroundColor = PlaceholderAssetResolver.backgroundColorHexFor(renderPlan.renderStyle());
        String textColor = PlaceholderAssetResolver.textColorFor(renderPlan.renderStyle());
        int width = renderPlan.dimensions().width();
        int height = renderPlan.dimensions().height();

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("color=c=" + backgroundColor + ":s=" + width + "x" + height + ":r=1:d=1");
        command.add("-vf");
        command.add(buildFilterChain(headline, textColor, width, height));
        command.add("-frames:v");
        command.add("1");
        command.add("-update");
        command.add("1");
        command.add(outputFileName);
        return List.copyOf(command);
    }

    private static String buildFilterChain(String headline, String textColor, int width, int height) {
        List<String> lines = wrapLines(headline);
        int bandY = height / 2 - BAND_HEIGHT / 2;

        List<String> filters = new ArrayList<>();
        filters.add("vignette=PI/4");
        filters.add("drawbox=x=0:y=" + bandY + ":w=" + width + ":h=" + BAND_HEIGHT + ":color=black@0.55:t=fill");
        filters.add("drawbox=x=0:y=" + bandY + ":w=16:h=" + BAND_HEIGHT + ":color=0xf85149:t=fill");

        int blockHeight = lines.size() * LINE_SPACING;
        int firstLineY = height / 2 - blockHeight / 2;
        for (int i = 0; i < lines.size(); i++) {
            int y = firstLineY + i * LINE_SPACING;
            filters.add("drawtext=text='" + FfmpegTextEscaper.escape(lines.get(i)) + "':fontsize="
                    + HEADLINE_FONT_SIZE + ":fontcolor=" + textColor + ":borderw=6:bordercolor=black"
                    + ":x=(w-text_w)/2:y=" + y);
        }
        return String.join(",", filters);
    }

    private static List<String> wrapLines(String headline) {
        String[] words = headline.trim().split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() > 0 && current.length() + 1 + word.length() > MAX_LINE_CHARS) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add(headline);
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    private static String headlineFor(RenderPlan renderPlan) {
        if (renderPlan.thumbnailBrief() != null && !renderPlan.thumbnailBrief().isBlank()) {
            return renderPlan.thumbnailBrief();
        }
        for (SceneRenderPlan scene : renderPlan.scenes()) {
            if (scene.sceneType() == Scene.SceneType.HOOK && !scene.textLayers().isEmpty()) {
                return scene.textLayers().get(0).text();
            }
        }
        if (!renderPlan.scenes().isEmpty() && !renderPlan.scenes().get(0).textLayers().isEmpty()) {
            return renderPlan.scenes().get(0).textLayers().get(0).text();
        }
        return renderPlan.topicTitle();
    }
}
