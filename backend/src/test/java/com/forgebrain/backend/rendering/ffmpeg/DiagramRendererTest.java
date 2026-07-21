package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.rendering.RenderPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagramRendererTest {

    private static final RenderPlan.VideoDimensions DIMENSIONS = new RenderPlan.VideoDimensions(1080, 1920);

    @Test
    void drawsOneCardPerItem() {
        List<String> filters = DiagramRenderer.buildFilters(List.of("JVM", "Bytecode", "Machine Code"),
                "0xd29922", "0xd29922@0.16", 0, 6, DIMENSIONS);
        String joined = String.join(",", filters);

        assertThat(joined).contains("drawtext=text='JVM'");
        assertThat(joined).contains("drawtext=text='Bytecode'");
        assertThat(joined).contains("drawtext=text='Machine Code'");
    }

    @Test
    void connectsConsecutiveCardsWithAVerticalBarNotAGlyph() {
        List<String> filters = DiagramRenderer.buildFilters(List.of("A", "B"), "0xd29922", "0xd29922@0.16", 0, 4,
                DIMENSIONS);

        // 2 cards -> 2 card fills + 2 left accent bars + 1 connector bar = 5 drawbox filters.
        long drawboxCount = filters.stream().filter(f -> f.startsWith("drawbox=")).count();
        assertThat(drawboxCount).isEqualTo(5);
        assertThat(filters).noneMatch(f -> f.contains("↓"));
    }

    @Test
    void everyElementIsGatedToTheScenesTimeWindow() {
        List<String> filters = DiagramRenderer.buildFilters(List.of("A", "B"), "0xd29922", "0xd29922@0.16", 1.5, 5.5,
                DIMENSIONS);

        assertThat(filters).allMatch(f -> f.contains("enable='between(t\\,1.50\\,5.50)'"));
    }
}
