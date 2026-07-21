package com.forgebrain.backend.rendering.ffmpeg;

/**
 * A slow, continuous background pan — the "camera movement" mission Part 2 asks for (no zoom,
 * pan, or focus shift previously existed; every frame was a static flat-colored rectangle).
 * Achieved by rendering the background canvas oversized by {@link #PAN_MARGIN_PX} in each
 * dimension and cropping down to the real output size with a sinusoidal, time-varying offset.
 *
 * <p>This specific approach — a fixed, pre-expanded integer-pixel canvas cropped with a {@code
 * sin}/{@code cos} offset — was chosen over percentage-based {@code scale=iw*1.08}+{@code
 * crop=iw/1.08} after that approach produced fractional-pixel rounding artifacts (a corrupted,
 * slightly-wrong-resolution output) when verified against a real ffmpeg binary; see commit
 * history for the reproduction.
 */
final class CameraMotion {

    private static final int PAN_MARGIN_PX = 40;

    private CameraMotion() {
    }

    static int expandedWidth(int outputWidth) {
        return outputWidth + PAN_MARGIN_PX;
    }

    static int expandedHeight(int outputHeight) {
        return outputHeight + PAN_MARGIN_PX;
    }

    static String panFilter(int outputWidth, int outputHeight) {
        int half = PAN_MARGIN_PX / 2;
        return "crop=w=" + outputWidth + ":h=" + outputHeight
                + ":x='" + half + "+" + half + "*sin(2*PI*t/8)'"
                + ":y='" + half + "+" + half + "*cos(2*PI*t/10)'";
    }
}
