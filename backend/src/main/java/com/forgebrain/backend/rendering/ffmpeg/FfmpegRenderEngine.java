package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.rendering.RenderEngine;
import com.forgebrain.backend.rendering.RenderPlan;
import com.forgebrain.backend.rendering.RenderValidationResult;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue.Severity;
import com.forgebrain.backend.rendering.RenderValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The first real {@link RenderEngine} implementation: converts a validated {@link RenderPlan}
 * into a playable MP4 by shelling out to a local {@code ffmpeg} binary — one reusable 9:16
 * vertical template (background color, timed text overlays, burned-in subtitles, a short fade
 * in/out), no video composition library or SaaS dependency. See backend/README.md's "Storyboard
 * to MP4" section for the full pipeline this sits at the end of, and its "Local FFmpeg
 * Requirement" note for what running this actually needs installed.
 *
 * <p>Every render is local and deterministic: given the same {@link RenderPlan} and the same
 * placeholder-resolved assets, the constructed {@code ffmpeg} command is byte-for-byte
 * identical (see {@link RenderCommandBuilder}) — only the output file's embedded timestamp
 * metadata and this class's own {@code generatedAt}/{@code packageId} vary between runs.
 */
@Component
public class FfmpegRenderEngine implements RenderEngine {

    private static final Logger log = LoggerFactory.getLogger(FfmpegRenderEngine.class);
    private static final String OUTPUT_FILE_NAME = "reel.mp4";
    private static final String SUBTITLE_FILE_NAME = "subtitles.ass";
    private static final String THUMBNAIL_FILE_NAME = "thumbnail.jpg";

    private final RenderValidator renderValidator;
    private final PlaceholderAssetResolver assetResolver;
    private final RenderingConfig renderingConfig;
    private final FfmpegProcessRunner processRunner;

    public FfmpegRenderEngine(RenderValidator renderValidator, PlaceholderAssetResolver assetResolver,
            RenderingConfig renderingConfig, FfmpegProcessRunner processRunner) {
        this.renderValidator = renderValidator;
        this.assetResolver = assetResolver;
        this.renderingConfig = renderingConfig;
        this.processRunner = processRunner;
    }

    @Override
    public VideoPackage render(RenderPlan renderPlan) {
        RenderValidationResult validation = renderValidator.validate(renderPlan);
        if (!validation.valid()) {
            throw new RenderExecutionException("RenderPlan for topic '" + renderPlan.topicId()
                    + "' failed validation, refusing to render: " + describeErrors(validation));
        }

        Path renderDirectory = Path.of(renderingConfig.outputDirectory(),
                renderPlan.topicId() + "-" + System.currentTimeMillis());
        try {
            Files.createDirectories(renderDirectory);
        } catch (IOException e) {
            throw new RenderExecutionException(
                    "Could not create render output directory '" + renderDirectory + "'.", e);
        }

        writeSubtitleFile(renderPlan, renderDirectory);

        Optional<Path> voiceoverPath = assetResolver.resolveVoiceoverPath(renderPlan.topicId());
        if (voiceoverPath.isEmpty()) {
            log.warn("No real narration file found for topic '{}' in {}; rendering with a silent audio track."
                    + " This is the documented fallback until Voice Generation is implemented.",
                    renderPlan.topicId(), renderingConfig.voiceoverAssetsDirectory());
        }

        List<String> renderCommand = RenderCommandBuilder.build(renderPlan, renderingConfig.ffmpegPath(),
                OUTPUT_FILE_NAME, SUBTITLE_FILE_NAME, voiceoverPath.orElse(null));
        processRunner.run(renderCommand, renderDirectory);

        Path videoFile = renderDirectory.resolve(OUTPUT_FILE_NAME);
        verifyOutputFile(videoFile);

        Path thumbnailFile = renderDirectory.resolve(THUMBNAIL_FILE_NAME);
        extractThumbnail(renderPlan, thumbnailFile, renderDirectory);

        return buildVideoPackage(renderPlan, videoFile, thumbnailFile);
    }

    private void writeSubtitleFile(RenderPlan renderPlan, Path renderDirectory) {
        try {
            Files.writeString(renderDirectory.resolve(SUBTITLE_FILE_NAME),
                    AssSubtitleWriter.toAss(renderPlan.subtitles(), renderPlan.dimensions().width(),
                            renderPlan.dimensions().height()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RenderExecutionException("Could not write subtitle file for topic '"
                    + renderPlan.topicId() + "'.", e);
        }
    }

    private void extractThumbnail(RenderPlan renderPlan, Path thumbnailFile, Path renderDirectory) {
        List<String> thumbnailCommand = ThumbnailCommandBuilder.build(renderPlan, renderingConfig.ffmpegPath(),
                thumbnailFile.getFileName().toString());
        try {
            processRunner.run(thumbnailCommand, renderDirectory);
        } catch (RenderExecutionException e) {
            log.warn("Thumbnail generation failed for topic '{}'; the video itself rendered successfully, "
                    + "continuing without a thumbnail.", renderPlan.topicId(), e);
        }
    }

    private void verifyOutputFile(Path videoFile) {
        long size;
        try {
            size = Files.size(videoFile);
        } catch (IOException e) {
            throw new RenderExecutionException(
                    "ffmpeg reported success but no output file exists at '" + videoFile + "'.", e);
        }
        if (size == 0) {
            throw new RenderExecutionException("ffmpeg produced an empty output file at '" + videoFile + "'.");
        }
    }

    private VideoPackage buildVideoPackage(RenderPlan renderPlan, Path videoFile, Path thumbnailFile) {
        long fileSize;
        String checksum;
        try {
            fileSize = Files.size(videoFile);
            checksum = sha256(videoFile);
        } catch (IOException e) {
            throw new RenderExecutionException("Could not finalize video package for '" + videoFile + "'.", e);
        }

        return new VideoPackage(
                UUID.randomUUID().toString(),
                null,
                renderPlan.topicId(),
                renderPlan.topicTitle(),
                videoFile.toAbsolutePath().toString(),
                Files.isRegularFile(thumbnailFile) ? thumbnailFile.toAbsolutePath().toString() : null,
                renderPlan.totalDurationSeconds(),
                renderPlan.dimensions().width() + "x" + renderPlan.dimensions().height(),
                renderPlan.aspectRatio(),
                VideoPackage.VideoCodec.H264,
                VideoPackage.AudioCodec.AAC,
                fileSize,
                checksum,
                Instant.now()
        );
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required MessageDigest algorithm.", e);
        }
    }

    private static String describeErrors(RenderValidationResult validation) {
        return validation.issues().stream()
                .filter(issue -> issue.severity() == Severity.ERROR)
                .map(ValidationIssue::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("unknown validation failure");
    }
}
