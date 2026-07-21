package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.SceneRenderPlan;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the complete {@code ffmpeg} argument list for one {@link RenderPlan} — pure
 * construction, no process execution and no file I/O, so it's testable without a real {@code
 * ffmpeg} binary. Every filter fragment here was verified against a real {@code ffmpeg} 8.x
 * binary before being encoded (see commit history); the escaping rules in particular
 * ({@link FfmpegTextEscaper}, the {@code \,} inside {@code between(t\,start\,end)}) are load
 * -bearing FFmpeg filtergraph syntax, not stylistic choices — as is the fact that {@code
 * fontsize} is never driven by a time expression anywhere in this file: doing so was verified to
 * crash ffmpeg with a segmentation fault on this project's target build.
 *
 * <p>The "creator-quality layer" this class composes on top of the unchanged {@link RenderPlan}/
 * {@link SceneRenderPlan} model: a continuously panning background ({@link CameraMotion}) instead
 * of one static frame; a slide-and-fade text entrance per layer ({@link TextAnimator}) instead of
 * text simply appearing; a per-{@link com.forgebrain.backend.models.Scene.SceneType} accent
 * color, card, and kicker label ({@link SceneVisualTemplate}) so scenes read as visually distinct
 * beats instead of one undifferentiated template; real multi-line syntax-styled code cards
 * ({@link CodeBlockRenderer}) instead of a single plain-text focus line; simple stacked-card flow
 * diagrams ({@link DiagramRenderer}) for scenes whose on-screen text is a list of short items
 * (e.g. "JVM" / "Bytecode" / "Machine Code") instead of the same items stacked as static
 * paragraphs; and word-highlighted {@code .ass} subtitles ({@link AssSubtitleWriter}) instead of
 * plain {@code .srt}. See backend/README.md's "Creator-quality rendering layer" section.
 *
 * <p>When a scene's {@link SceneRenderPlan.BackgroundSpec#styleRef()} is {@link
 * SceneRenderPlan#FULL_BLEED_STYLE_REF} — written by {@link com.forgebrain.backend.rendering.RenderPlanBuilder}
 * when the Visual Director marked that scene's composition as {@code FULL_BLEED} — the scene
 * renders as a full-bleed visual card (a near-full-screen accent tint with a bold centered
 * headline) instead of the default small accent card behind the heading.
 */
final class RenderCommandBuilder {

    private static final String AUDIO_SAMPLE_RATE = "44100";
    private static final double FADE_DURATION_SECONDS = 0.3;
    private static final int KICKER_FONT_SIZE = 28;
    private static final int ACCENT_CARD_VERTICAL_PAD = 60;
    private static final int ACCENT_CARD_MARGIN_X = 50;
    private static final int FULL_BLEED_MARGIN = 24;
    private static final int FULL_BLEED_STRIPE_HEIGHT = 8;

    private RenderCommandBuilder() {
    }

    static List<String> build(RenderPlan renderPlan, String ffmpegPath, String outputFileName,
            String subtitleFileName, Path resolvedAudioFilePath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");

        int canvasWidth = CameraMotion.expandedWidth(renderPlan.dimensions().width());
        int canvasHeight = CameraMotion.expandedHeight(renderPlan.dimensions().height());

        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("color=c=" + PlaceholderAssetResolver.backgroundColorHexFor(renderPlan.renderStyle())
                + ":s=" + canvasWidth + "x" + canvasHeight
                + ":r=" + renderPlan.fps() + ":d=" + formatSeconds(renderPlan.totalDurationSeconds()));

        if (resolvedAudioFilePath != null) {
            command.add("-i");
            command.add(resolvedAudioFilePath.toAbsolutePath().toString());
        } else {
            command.add("-f");
            command.add("lavfi");
            command.add("-t");
            command.add(formatSeconds(renderPlan.totalDurationSeconds()));
            command.add("-i");
            command.add("anullsrc=channel_layout=stereo:sample_rate=" + AUDIO_SAMPLE_RATE);
        }

        command.add("-vf");
        command.add(buildFilterChain(renderPlan, subtitleFileName));

        command.add("-map");
        command.add("0:v");
        command.add("-map");
        command.add("1:a");
        command.add("-c:v");
        command.add("libx264");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add("-shortest");
        command.add(outputFileName);

        return List.copyOf(command);
    }

    private static String buildFilterChain(RenderPlan renderPlan, String subtitleFileName) {
        String textColor = PlaceholderAssetResolver.textColorFor(renderPlan.renderStyle());
        RenderPlan.VideoDimensions dimensions = renderPlan.dimensions();
        List<String> filters = new ArrayList<>();

        filters.add(CameraMotion.panFilter(dimensions.width(), dimensions.height()));
        filters.add("vignette=PI/5");

        for (SceneRenderPlan scene : renderPlan.scenes()) {
            filters.addAll(sceneFilters(scene, dimensions, textColor));
        }

        filters.add("subtitles=" + subtitleFileName);

        double fadeOutStart = Math.max(0, renderPlan.totalDurationSeconds() - FADE_DURATION_SECONDS);
        filters.add("fade=t=in:st=0:d=" + FADE_DURATION_SECONDS);
        filters.add("fade=t=out:st=" + formatSeconds(fadeOutStart) + ":d=" + FADE_DURATION_SECONDS);

        return String.join(",", filters);
    }

    private static List<String> sceneFilters(SceneRenderPlan scene, RenderPlan.VideoDimensions dimensions,
            String textColor) {
        SceneVisualTemplate template = SceneVisualTemplate.forSceneType(scene.sceneType());
        List<String> filters = new ArrayList<>();

        if (scene.codeLayer() != null) {
            if (!scene.textLayers().isEmpty()) {
                filters.addAll(headingFilters(scene.textLayers().get(0).text(), template, scene, dimensions,
                        textColor));
            }
            filters.addAll(CodeBlockRenderer.buildFilters(scene.codeLayer(), scene.startTime(), scene.endTime(),
                    dimensions));
        } else if (SceneRenderPlan.FULL_BLEED_STYLE_REF.equals(scene.background().styleRef())) {
            filters.addAll(fullBleedFilters(scene, template, dimensions, textColor));
        } else if (scene.textLayers().size() > 1) {
            if (!template.kicker().isEmpty()) {
                filters.add(kickerFilter(template, scene, dimensions));
            }
            List<String> items = scene.textLayers().stream().map(SceneRenderPlan.TextLayer::text).toList();
            filters.addAll(DiagramRenderer.buildFilters(items, template.accentColorHex(), template.accentCardColor(),
                    scene.startTime(), scene.endTime(), dimensions));
        } else if (!scene.textLayers().isEmpty()) {
            filters.addAll(headingFilters(scene.textLayers().get(0).text(), template, scene, dimensions, textColor));
        }

        return filters;
    }

    private static List<String> headingFilters(String text, SceneVisualTemplate template, SceneRenderPlan scene,
            RenderPlan.VideoDimensions dimensions, String textColor) {
        List<String> filters = new ArrayList<>();
        int textY = template.textYFor(dimensions.height());
        String enable = enableClause(scene.startTime(), scene.endTime());

        if (template.showAccentCard()) {
            int cardHeight = template.headingFontSize() + ACCENT_CARD_VERTICAL_PAD;
            int cardY = textY - ACCENT_CARD_VERTICAL_PAD / 2;
            int cardWidth = dimensions.width() - 2 * ACCENT_CARD_MARGIN_X;
            filters.add("drawbox=x=" + ACCENT_CARD_MARGIN_X + ":y=" + cardY + ":w=" + cardWidth
                    + ":h=" + cardHeight + ":color=" + template.accentCardColor() + ":t=fill:enable=" + enable);
        }
        if (!template.kicker().isEmpty()) {
            filters.add(kickerFilter(template, scene, dimensions));
        }
        filters.add(TextAnimator.drawText(text, textColor, template.headingFontSize(), textY, scene.startTime(),
                scene.endTime()));
        return filters;
    }

    /**
     * A near-full-screen accent tint with a top stripe and a bold centered headline — the
     * Visual Director's {@code FULL_BLEED} composition, distinct from the default small accent
     * card behind the heading (mission Part 3: "at least one scene type can render as ... a
     * full-bleed visual card").
     */
    private static List<String> fullBleedFilters(SceneRenderPlan scene, SceneVisualTemplate template,
            RenderPlan.VideoDimensions dimensions, String textColor) {
        List<String> filters = new ArrayList<>();
        String enable = enableClause(scene.startTime(), scene.endTime());
        int cardWidth = dimensions.width() - 2 * FULL_BLEED_MARGIN;
        int cardHeight = dimensions.height() - 2 * FULL_BLEED_MARGIN;

        filters.add("drawbox=x=" + FULL_BLEED_MARGIN + ":y=" + FULL_BLEED_MARGIN + ":w=" + cardWidth
                + ":h=" + cardHeight + ":color=" + template.accentCardColor() + ":t=fill:enable=" + enable);
        filters.add("drawbox=x=" + FULL_BLEED_MARGIN + ":y=" + FULL_BLEED_MARGIN + ":w=" + cardWidth
                + ":h=" + FULL_BLEED_STRIPE_HEIGHT + ":color=" + template.accentColorHex() + ":t=fill:enable="
                + enable);

        if (!scene.textLayers().isEmpty()) {
            int textY = dimensions.height() / 2 - template.headingFontSize() / 2;
            filters.add(TextAnimator.drawText(scene.textLayers().get(0).text(), textColor,
                    template.headingFontSize(), textY, scene.startTime(), scene.endTime()));
        }
        return filters;
    }

    private static String kickerFilter(SceneVisualTemplate template, SceneRenderPlan scene,
            RenderPlan.VideoDimensions dimensions) {
        int y = template.textYFor(dimensions.height()) - 70;
        return "drawtext=text='" + FfmpegTextEscaper.escape(template.kicker()) + "':fontsize=" + KICKER_FONT_SIZE
                + ":fontcolor=" + template.accentColorHex() + ":x=(w-text_w)/2:y=" + y
                + ":enable=" + enableClause(scene.startTime(), scene.endTime());
    }

    private static String enableClause(double start, double end) {
        return "'between(t\\," + formatSeconds(start) + "\\," + formatSeconds(end) + ")'";
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }
}
