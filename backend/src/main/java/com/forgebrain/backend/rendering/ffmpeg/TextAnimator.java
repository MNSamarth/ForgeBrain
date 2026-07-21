package com.forgebrain.backend.rendering.ffmpeg;

import java.util.Locale;

/**
 * Builds a {@code drawtext} filter fragment with a short slide-up-and-fade-in entrance instead
 * of appearing fully-formed and static the instant its scene starts (mission Part 2: every scene
 * should feel intentionally edited, not like a static slide deck). Only {@code y} (position) and
 * {@code alpha} (opacity) are ever driven by a time expression here — {@code fontsize} is always
 * the static value passed in, never animated, because animating {@code fontsize} was verified to
 * crash ffmpeg with a segmentation fault on this project's target ffmpeg build (see commit
 * history). The expressions below mirror the exact {@code if(lt(t\,X)\,...\,Y)} shape verified
 * safe against a real ffmpeg binary before being encoded here.
 */
final class TextAnimator {

    private static final double ENTRANCE_SECONDS = 0.35;
    private static final double SLIDE_PIXELS = 46;

    private TextAnimator() {
    }

    static String drawText(String text, String colorHex, int fontSize, int targetY, double sceneStart,
            double sceneEnd) {
        double entranceEnd = sceneStart + ENTRANCE_SECONDS;
        double slideSpeed = SLIDE_PIXELS / ENTRANCE_SECONDS;
        String yExpr = "'if(lt(t\\," + fmt(entranceEnd) + ")\\," + targetY + "+(" + fmt(entranceEnd)
                + "-t)*" + fmt(slideSpeed) + "\\," + targetY + ")'";
        String alphaExpr = "'if(lt(t\\," + fmt(entranceEnd) + ")\\,(t-" + fmt(sceneStart) + ")/"
                + fmt(ENTRANCE_SECONDS) + "\\,1)'";
        return "drawtext=text='" + FfmpegTextEscaper.escape(text) + "':fontsize=" + fontSize
                + ":fontcolor=" + colorHex + ":x=(w-text_w)/2:y=" + yExpr + ":alpha=" + alphaExpr
                + ":enable='between(t\\," + fmt(sceneStart) + "\\," + fmt(sceneEnd) + ")'";
    }

    static String fmt(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }
}
