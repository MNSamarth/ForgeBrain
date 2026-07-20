package com.forgebrain.backend.job;

import java.nio.file.Path;

/**
 * Persists {@link ReelJobReport}s as {@code report.json} inside the render output folder for the
 * job it describes — mirrors {@code com.forgebrain.backend.pipeline.ReelExportReportWriter}'s
 * convention exactly.
 */
public interface ReelJobReportWriter {

    /**
     * @return the absolute path of the written report file
     */
    String write(ReelJobReport report, Path outputDirectory);
}
