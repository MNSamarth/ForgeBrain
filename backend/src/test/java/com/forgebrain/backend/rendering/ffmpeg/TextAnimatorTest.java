package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextAnimatorTest {

    @Test
    void drawsTheEscapedTextWithAStaticFontSize() {
        String filter = TextAnimator.drawText("Hello: world", "white", 64, 300, 2.0, 6.0);

        assertThat(filter).contains("drawtext=text='Hello\\: world'");
        assertThat(filter).contains(":fontsize=64:");
    }

    @Test
    void animatesOnlyYAndAlphaNeverFontSize() {
        String filter = TextAnimator.drawText("Hi", "white", 64, 300, 2.0, 6.0);

        assertThat(filter).contains("y='if(lt(t\\,2.35)");
        assertThat(filter).contains("alpha='if(lt(t\\,2.35)");
        assertThat(filter).doesNotContain("fontsize='");
    }

    @Test
    void gatesVisibilityToTheScenesStartAndEndWindow() {
        String filter = TextAnimator.drawText("Hi", "white", 64, 300, 2.0, 6.0);

        assertThat(filter).contains("enable='between(t\\,2.00\\,6.00)'");
    }
}
