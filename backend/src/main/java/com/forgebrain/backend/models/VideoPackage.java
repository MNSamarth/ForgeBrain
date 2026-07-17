package com.forgebrain.backend.models;

import java.time.Instant;

/**
 * The finished video artifact produced by a successful {@link RenderJob}. Mirrors the
 * {@code videoPackage} definition in {@code renderer/render-schema.json}. The thumbnail is
 * chosen from the source storyboard's first {@code emphasisPoint}, not an arbitrary frame
 * (see renderer/render-spec.md Section 5).
 *
 * @see <a href="../../../../../../../../renderer/render-schema.json">renderer/render-schema.json</a>
 */
public record VideoPackage(
        String packageId,
        String basedOnRenderJobId,
        String topicId,
        String topicTitle,
        String videoFileUri,
        String thumbnailFrameUri,
        double durationSeconds,
        String resolution,
        Storyboard.AspectRatio aspectRatio,
        VideoCodec videoCodec,
        AudioCodec audioCodec,
        long fileSizeBytes,
        String checksum,
        Instant generatedAt
) {

    public enum VideoCodec {
        H264, H265, VP9
    }

    public enum AudioCodec {
        AAC, OPUS
    }
}
