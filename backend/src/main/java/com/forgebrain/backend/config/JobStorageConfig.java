package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local durable storage for the job layer (see {@code com.forgebrain.backend.job}). Kept
 * separate from {@link RenderingConfig} — jobs/output-storage is a distinct concern from the
 * FFmpeg render working directory, and this avoids adding fields to a record already
 * constructed positionally by several existing tests.
 *
 * @param jobsDirectory       where {@link com.forgebrain.backend.job.ReelJob} snapshots and
 *                            {@link com.forgebrain.backend.job.ReelJobReport}s are persisted,
 *                            one JSON file per job/report
 * @param outputStorageRoot   where {@link com.forgebrain.backend.job.OutputStorage} copies each
 *                            packaged output file, one subfolder per job id — the local stand-in
 *                            for a Cloud Storage bucket (see that interface's javadoc)
 */
@ConfigurationProperties(prefix = "forgebrain.jobs")
public record JobStorageConfig(
        String jobsDirectory,
        String outputStorageRoot
) {
}
