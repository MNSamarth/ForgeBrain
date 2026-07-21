package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a scene's {@link SceneRenderPlan.CodeLayer} as an IDE-style code card — a dark title
 * bar plus every source line drawn individually — instead of the single plain-text focus line
 * this used to draw (mission Part 4: Java reels should actually show Java, not a text dump). The
 * line matching {@link SceneRenderPlan.CodeLayer#focusLine()} gets its own highlighted background
 * bar and an accent text color, so it reads as "the active line" the way a real editor highlights
 * a breakpoint or cursor line.
 */
final class CodeBlockRenderer {

    private static final int MAX_LINES = 8;
    private static final int LINE_FONT_SIZE = 30;
    private static final int LINE_HEIGHT = 46;
    private static final int HEADER_HEIGHT = 56;
    private static final int PADDING = 30;
    private static final int MARGIN_X = 70;
    private static final String CARD_BACKGROUND = "0x161b22@0.95";
    private static final String HEADER_BACKGROUND = "0x21262d@0.95";
    private static final String CODE_TEXT_COLOR = "0xc9d1d9";
    private static final String FOCUS_TEXT_COLOR = "0x7ee787";
    private static final String FOCUS_LINE_BACKGROUND = "0x3fb950@0.22";
    private static final String HEADER_TEXT_COLOR = "0x8b949e";

    private CodeBlockRenderer() {
    }

    static List<String> buildFilters(SceneRenderPlan.CodeLayer codeLayer, double sceneStart, double sceneEnd,
            RenderPlan.VideoDimensions dimensions) {
        List<String> lines = splitLines(codeLayer.codeSnippet());
        int cardWidth = dimensions.width() - 2 * MARGIN_X;
        int cardHeight = HEADER_HEIGHT + lines.size() * LINE_HEIGHT + 2 * PADDING;
        int cardY = Math.max(220, (dimensions.height() - cardHeight) / 2 - 80);
        int cardX = MARGIN_X;
        String enable = enableClause(sceneStart, sceneEnd);

        List<String> filters = new ArrayList<>();
        filters.add("drawbox=x=" + cardX + ":y=" + cardY + ":w=" + cardWidth + ":h=" + cardHeight
                + ":color=" + CARD_BACKGROUND + ":t=fill:enable=" + enable);
        filters.add("drawbox=x=" + cardX + ":y=" + cardY + ":w=" + cardWidth + ":h=" + HEADER_HEIGHT
                + ":color=" + HEADER_BACKGROUND + ":t=fill:enable=" + enable);
        filters.add("drawtext=text='" + FfmpegTextEscaper.escape(languageLabel(codeLayer.language()))
                + "':fontsize=22:fontcolor=" + HEADER_TEXT_COLOR + ":x=" + (cardX + 24) + ":y=" + (cardY + 16)
                + ":enable=" + enable);

        int lineY = cardY + HEADER_HEIGHT + PADDING;
        String focusLine = codeLayer.focusLine() == null ? "" : codeLayer.focusLine().trim();
        for (String line : lines) {
            boolean isFocusLine = !focusLine.isEmpty() && line.trim().equals(focusLine);
            if (isFocusLine) {
                filters.add("drawbox=x=" + (cardX + 12) + ":y=" + (lineY - 6) + ":w=" + (cardWidth - 24)
                        + ":h=" + (LINE_FONT_SIZE + 14) + ":color=" + FOCUS_LINE_BACKGROUND + ":t=fill:enable="
                        + enable);
            }
            String textColor = isFocusLine ? FOCUS_TEXT_COLOR : CODE_TEXT_COLOR;
            filters.add("drawtext=text='" + FfmpegTextEscaper.escape(line.isEmpty() ? " " : line)
                    + "':fontsize=" + LINE_FONT_SIZE + ":fontcolor=" + textColor + ":x=" + (cardX + 26)
                    + ":y=" + lineY + ":enable=" + enable);
            lineY += LINE_HEIGHT;
        }
        return filters;
    }

    private static List<String> splitLines(String codeSnippet) {
        if (codeSnippet == null || codeSnippet.isBlank()) {
            return List.of("");
        }
        String[] rawLines = codeSnippet.replace("\r\n", "\n").split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            if (lines.size() >= MAX_LINES) {
                lines.set(lines.size() - 1, "...");
                break;
            }
            lines.add(rawLine);
        }
        return lines;
    }

    private static String languageLabel(String language) {
        String lang = (language == null || language.isBlank()) ? "java" : language;
        return lang + "  -  Main." + (lang.equalsIgnoreCase("java") ? "java" : lang);
    }

    private static String enableClause(double start, double end) {
        return "'between(t\\," + fmt(start) + "\\," + fmt(end) + ")'";
    }

    private static String fmt(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }
}
