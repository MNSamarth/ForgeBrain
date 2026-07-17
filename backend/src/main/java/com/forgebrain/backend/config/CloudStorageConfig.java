package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloud Storage configuration. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.cloud-storage.*} in application.yml.
 *
 * @param mediaBucket bucket for finished audio/video/subtitle artifacts (voice, subtitles,
 *                    renderer output)
 * @param assetsBucket bucket for the {@code assets/} catalog (fonts, music, themes) resolved
 *                     by Asset Management
 * @param tempBucket  bucket for in-progress render working files
 */
@ConfigurationProperties(prefix = "forgebrain.cloud-storage")
public record CloudStorageConfig(
        String mediaBucket,
        String assetsBucket,
        String tempBucket
) {
}
