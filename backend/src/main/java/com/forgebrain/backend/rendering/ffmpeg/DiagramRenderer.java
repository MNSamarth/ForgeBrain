package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.RenderPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a scene's list-like on-screen text (2+ short items — e.g. "JVM", "Bytecode", "Machine
 * Code", or "Windows" / "Mac" / "Linux") into a simple vertical flow diagram: stacked accent
 * cards connected by a plain vertical bar, instead of the same items stacked as plain static
 * paragraphs (mission Part 3: explain concepts visually, not with prose). The connector is a
 * solid {@code drawbox} bar rather than an arrow glyph, deliberately avoiding any dependency on
 * font glyph coverage — this environment's ffmpeg build already logs a {@code Fontconfig error}
 * on startup, so a missing-glyph "tofu box" for a Unicode arrow is a real risk not worth taking
 * for a purely decorative connector.
 */
final class DiagramRenderer {

    private static final int CARD_HEIGHT = 96;
    private static final int CARD_GAP = 56;
    private static final int MARGIN_X = 90;
    private static final int FONT_SIZE = 40;
    private static final int CONNECTOR_WIDTH = 6;

    private DiagramRenderer() {
    }

    static List<String> buildFilters(List<String> items, String accentColorHex, String accentCardColor,
            double sceneStart, double sceneEnd, RenderPlan.VideoDimensions dimensions) {
        String enable = enableClause(sceneStart, sceneEnd);
        int cardWidth = dimensions.width() - 2 * MARGIN_X;
        int totalHeight = items.size() * CARD_HEIGHT + Math.max(0, items.size() - 1) * CARD_GAP;
        int y = Math.max(280, (dimensions.height() - totalHeight) / 2 - 40);

        List<String> filters = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            filters.add("drawbox=x=" + MARGIN_X + ":y=" + y + ":w=" + cardWidth + ":h=" + CARD_HEIGHT
                    + ":color=" + accentCardColor + ":t=fill:enable=" + enable);
            filters.add("drawbox=x=" + MARGIN_X + ":y=" + y + ":w=" + CONNECTOR_WIDTH + ":h=" + CARD_HEIGHT
                    + ":color=" + accentColorHex + ":t=fill:enable=" + enable);
            filters.add("drawtext=text='" + FfmpegTextEscaper.escape(items.get(i)) + "':fontsize=" + FONT_SIZE
                    + ":fontcolor=white:x=(w-text_w)/2:y=" + (y + (CARD_HEIGHT - FONT_SIZE) / 2)
                    + ":enable=" + enable);
            if (i < items.size() - 1) {
                int connectorX = dimensions.width() / 2 - CONNECTOR_WIDTH / 2;
                filters.add("drawbox=x=" + connectorX + ":y=" + (y + CARD_HEIGHT) + ":w=" + CONNECTOR_WIDTH
                        + ":h=" + CARD_GAP + ":color=" + accentColorHex + ":t=fill:enable=" + enable);
            }
            y += CARD_HEIGHT + CARD_GAP;
        }
        return filters;
    }

    private static String enableClause(double start, double end) {
        return "'between(t\\," + fmt(start) + "\\," + fmt(end) + ")'";
    }

    private static String fmt(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }
}
