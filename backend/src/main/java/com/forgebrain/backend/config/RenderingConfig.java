package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local FFmpeg-based rendering configuration. Bound from {@code forgebrain.rendering.*} in
 * application.yml. Kept separate from {@link LocalStorageConfig} rather than adding fields to
 * it, since {@code LocalStorageConfig} is constructed positionally by nearly every existing
 * service test — adding fields there would force unrelated test changes across the codebase.
 *
 * @param ffmpegPath        the {@code ffmpeg} executable to invoke — a bare command name
 *                          resolved via the OS {@code PATH} by default, or an absolute path
 *                          override for environments where it isn't on {@code PATH}
 * @param ffprobePath       the {@code ffprobe} executable used to measure real media file
 *                          durations (see {@link com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner})
 * @param outputDirectory   directory where rendered {@code .mp4} files (and their working
 *                          {@code .srt}/thumbnail files) are written, one subfolder per render
 * @param voiceoverAssetsDirectory directory {@link com.forgebrain.backend.rendering.ffmpeg.PlaceholderAssetResolver}
 *                          and {@link com.forgebrain.backend.services.VoiceServiceImpl} check for
 *                          a real per-topic narration file before falling back to silent audio —
 *                          see "Add audio support" in backend/README.md. Voice Generation isn't
 *                          backed by a real TTS provider yet, so this directory is normally
 *                          empty; it exists as the documented convention a future implementation
 *                          would write into (or that {@code VoiceServiceImpl} itself writes its
 *                          silent-fallback track into, so later runs stay consistent).
 * @param assetsDirectory   root directory {@link com.forgebrain.backend.services.AssetServiceImpl}
 *                          resolves fonts/backgrounds/watermark/music/code-card references
 *                          against — the local stand-in for the {@code assets/} catalog described
 *                          in {@code renderer/asset-management-spec.md} Section 4, which a real
 *                          deployment would back with Cloud Storage instead.
 */
@ConfigurationProperties(prefix = "forgebrain.rendering")
public record RenderingConfig(
        String ffmpegPath,
        String ffprobePath,
        String outputDirectory,
        String voiceoverAssetsDirectory,
        String assetsDirectory
) {
}
