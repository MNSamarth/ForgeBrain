package com.forgebrain.backend.pipeline;

import java.nio.file.Path;

/**
 * Persists {@link ReelExportReport}s. Unlike {@link ReportWriter} (which always writes to one
 * configured reports directory), this writes into the specific render output folder for the run
 * it describes, so {@code reel.mp4}, {@code subtitles.ass}, {@code metadata.json}, and this
 * report all live together — see "End-to-End Reel Export" in backend/README.md.
 */
public interface ReelExportReportWriter {

    /**
     * @return the absolute path of the written report file
     */
    String write(ReelExportReport report, Path outputDirectory);
}
