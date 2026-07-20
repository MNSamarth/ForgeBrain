package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FfmpegTextEscaperTest {

    @Test
    void escapesColonsBackslashesAndPercentSigns() {
        assertThat(FfmpegTextEscaper.escape("Time: 12%")).isEqualTo("Time\\: 12\\%");
        assertThat(FfmpegTextEscaper.escape("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void escapesSingleQuotesWithTheCloseEscapeReopenSequence() {
        assertThat(FfmpegTextEscaper.escape("It's a test")).isEqualTo("It'\\''s a test");
    }

    @Test
    void leavesPlainTextUnchanged() {
        assertThat(FfmpegTextEscaper.escape("THE FOR LOOP")).isEqualTo("THE FOR LOOP");
    }

    @Test
    void returnsEmptyStringForNull() {
        assertThat(FfmpegTextEscaper.escape(null)).isEmpty();
    }
}
