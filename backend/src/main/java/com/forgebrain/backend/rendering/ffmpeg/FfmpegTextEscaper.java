package com.forgebrain.backend.rendering.ffmpeg;

/**
 * Escapes text for safe use inside an FFmpeg {@code drawtext} filter's single-quoted {@code
 * text=} value. FFmpeg's filtergraph mini-language treats {@code :} as an option separator, "%"
 * as the start of a text-expansion token, and (within an already single-quoted value) a literal
 * {@code '} can only be embedded via the close-escape-reopen sequence {@code '\''} — the same
 * trick POSIX shells use for the same reason. Verified against a real {@code ffmpeg} binary
 * before being encoded here (see commit history); every rule below is load-bearing, not
 * defensive guessing.
 */
final class FfmpegTextEscaper {

    private FfmpegTextEscaper() {
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("%", "\\%")
                .replace("'", "'\\''");
    }
}
