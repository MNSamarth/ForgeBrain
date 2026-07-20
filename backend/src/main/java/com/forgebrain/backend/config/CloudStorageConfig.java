package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloud Storage configuration. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.cloud-storage.*} in application.yml.
 *
 * <p>{@code enabled}/{@code mediaBucket}/{@code outputPrefix}/{@code projectId} back {@link
 * com.forgebrain.backend.job.CloudStorageOutputStorage} — the reel job output layer's Cloud
 * Storage seam (see {@link com.forgebrain.backend.job.OutputStorage}). Disabled by default in
 * every committed profile, in which case {@link com.forgebrain.backend.job.LocalOutputStorage}
 * is used instead and none of these fields are read.
 *
 * @param enabled      when {@code true}, reel job output is uploaded to {@code mediaBucket}
 *                     instead of written to local disk; when {@code false} (the default),
 *                     local storage is used and the rest of this record is ignored
 * @param mediaBucket bucket for finished audio/video/subtitle artifacts (voice, subtitles,
 *                    renderer output) — required when {@code enabled} is {@code true}
 * @param assetsBucket bucket for the {@code assets/} catalog (fonts, music, themes) resolved
 *                     by Asset Management
 * @param tempBucket  bucket for in-progress render working files
 * @param outputPrefix object-name prefix applied ahead of {@code <jobId>/<fileName>} for every
 *                      upload, e.g. {@code "reels"} yields {@code reels/<jobId>/reel.mp4}
 * @param projectId    GCP project ID for the Cloud Storage client; blank uses the project
 *                     associated with Application Default Credentials
 */
@ConfigurationProperties(prefix = "forgebrain.cloud-storage")
public record CloudStorageConfig(
        boolean enabled,
        String mediaBucket,
        String assetsBucket,
        String tempBucket,
        String outputPrefix,
        String projectId
) {
}
