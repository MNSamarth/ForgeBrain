package com.forgebrain.backend.job;

import com.forgebrain.backend.models.VideoPackage;
import java.nio.file.Path;

/**
 * Turns one render's raw output files (already on disk, written by {@code FfmpegRenderEngine})
 * into a structured {@link ReelOutputPackage} — writes {@code metadata.json}, then passes every
 * artifact through {@link OutputStorage} so the package is durably stored, not just left in a
 * working directory. See backend/README.md's "Output Packaging" section for the folder-layout
 * and naming conventions this produces.
 */
public interface OutputPackagingService {

    /**
     * @param renderDirectory the working directory {@code FfmpegRenderEngine} already wrote
     *                        {@code reel.mp4}/{@code thumbnail.jpg}/{@code subtitles.ass} into
     * @param subtitleFile    the subtitle file's path within {@code renderDirectory}
     */
    ReelOutputPackage packageOutputs(String jobId, Path renderDirectory, VideoPackage videoPackage,
            Path subtitleFile);

    /**
     * Stores the job's execution report, once written, through the same {@link OutputStorage}
     * seam as every other output file — kept as its own method since the report is written after
     * (and describes) the rest of the package.
     */
    String storeReport(String jobId, Path reportFile);

    /**
     * Stores one publishing-stage file (the {@code publishing-package.json} bundle, or a platform
     * adapter's dry-run payload) through the same {@link OutputStorage} seam — kept as its own
     * method rather than reusing {@link #storeReport}, since a publishing artifact isn't a report
     * and callers/logs should say so.
     */
    String storePublishingArtifact(String jobId, Path file);
}
