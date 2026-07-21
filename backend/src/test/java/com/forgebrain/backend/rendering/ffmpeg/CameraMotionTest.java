package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CameraMotionTest {

    @Test
    void expandsWidthAndHeightBySameFixedMargin() {
        assertThat(CameraMotion.expandedWidth(1080)).isEqualTo(1120);
        assertThat(CameraMotion.expandedHeight(1920)).isEqualTo(1960);
    }

    @Test
    void panFilterCropsDownToTheExactRequestedOutputSize() {
        String filter = CameraMotion.panFilter(1080, 1920);

        assertThat(filter).startsWith("crop=w=1080:h=1920");
    }

    @Test
    void panFilterOnlyEverAnimatesTheCropOffsetNotAnySizeParameter() {
        String filter = CameraMotion.panFilter(1080, 1920);

        assertThat(filter).contains("x='").contains("sin(2*PI*t/8)'");
        assertThat(filter).contains("y='").contains("cos(2*PI*t/10)'");
        assertThat(filter).doesNotContain("w='").doesNotContain("h='");
    }
}
