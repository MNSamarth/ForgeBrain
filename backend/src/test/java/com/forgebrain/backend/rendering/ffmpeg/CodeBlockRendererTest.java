package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeBlockRendererTest {

    private static final RenderPlan.VideoDimensions DIMENSIONS = new RenderPlan.VideoDimensions(1080, 1920);

    @Test
    void drawsEveryLineOfTheSnippetNotJustTheFocusLine() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer(
                "for (int i = 0; i < 5; i++) {\n    System.out.println(i);\n}", "System.out.println(i);", "java");

        List<String> filters = CodeBlockRenderer.buildFilters(codeLayer, 0, 5, DIMENSIONS);
        String joined = String.join(",", filters);

        assertThat(joined).contains("drawtext=text='for (int i = 0; i < 5; i++) {'");
        // Original indentation is preserved so the code card reads like real source, not a
        // flattened line dump.
        assertThat(joined).contains("drawtext=text='    System.out.println(i);'");
        assertThat(joined).contains("drawtext=text='}'");
    }

    @Test
    void highlightsOnlyTheLineMatchingTheFocusLine() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer(
                "int a = 1;\nint b = 2;", "int b = 2;", "java");

        List<String> filters = CodeBlockRenderer.buildFilters(codeLayer, 0, 5, DIMENSIONS);
        String joined = String.join(",", filters);

        assertThat(joined).contains("drawtext=text='int a = 1;':fontsize=30:fontcolor=0xc9d1d9");
        assertThat(joined).contains("drawtext=text='int b = 2;':fontsize=30:fontcolor=0x7ee787");
    }

    @Test
    void drawsAnIdeStyleHeaderWithTheLanguageLabel() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer("int a = 1;", "int a = 1;", "java");

        List<String> filters = CodeBlockRenderer.buildFilters(codeLayer, 0, 5, DIMENSIONS);
        String joined = String.join(",", filters);

        assertThat(joined).contains("java");
        assertThat(joined).contains("drawbox=x=70:y=");
    }

    @Test
    void cardIsGatedToTheScenesTimeWindow() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer("int a = 1;", "int a = 1;", "java");

        List<String> filters = CodeBlockRenderer.buildFilters(codeLayer, 3.5, 8.25, DIMENSIONS);
        String joined = String.join(",", filters);

        assertThat(joined).contains("enable='between(t\\,3.50\\,8.25)'");
    }
}
