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
 * @param outputDirectory   directory where rendered {@code .mp4} files (and their working
 *                          {@code .srt}/thumbnail files) are written, one subfolder per render
 * @param voiceoverAssetsDirectory directory {@link com.forgebrain.backend.rendering.ffmpeg.PlaceholderAssetResolver}
 *                          checks for a real narration file before falling back to silent audio
 *                          — see "Add audio support" in backend/README.md. Voice Generation
 *                          (TODO.md 1.8) isn't implemented, so this directory is normally empty;
 *                          it exists as the documented convention a future implementation would
 *                          write into.
 */
@ConfigurationProperties(prefix = "forgebrain.rendering")
public record RenderingConfig(
        String ffmpegPath,
        String outputDirectory,
        String voiceoverAssetsDirectory
) {
}
