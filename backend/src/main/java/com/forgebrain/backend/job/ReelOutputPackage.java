package com.forgebrain.backend.job;

import java.util.Map;

/**
 * The structured result of {@link OutputPackagingService#packageOutputs}: every artifact a
 * completed reel job produces, each already passed through {@link OutputStorage}. {@code files}
 * uses the same category keys consistently across this codebase's two report/result types
 * ({@code "video"}, {@code "thumbnail"}, {@code "subtitles"}, {@code "metadata"}, {@code
 * "report"}) so callers don't need to know which stage produced which file.
 */
public record ReelOutputPackage(
        String jobId,
        String sourceDirectory,
        Map<String, String> files
) {

    public String videoRef() {
        return files.get("video");
    }

    public String metadataRef() {
        return files.get("metadata");
    }
}
