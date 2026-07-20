package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.models.VideoPackage;

/**
 * The finished outcome of one {@link ReelExportService#exportReel()} run: everything written to
 * disk, plus the {@link VideoPackage} and {@link ReelExportReport} describing how it got there.
 * All paths point inside the same output folder — see backend/README.md's "End-to-End Reel
 * Export" section for the exact contents.
 */
public record ReelExportResult(
        String runId,
        String topicId,
        String topicTitle,
        String outputDirectory,
        String videoFilePath,
        String metadataFilePath,
        String subtitleFilePath,
        String reportFilePath,
        VideoPackage videoPackage,
        ReelExportReport report
) {
}
