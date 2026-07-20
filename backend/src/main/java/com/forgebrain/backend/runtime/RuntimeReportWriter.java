package com.forgebrain.backend.runtime;

/**
 * Persists a {@link RuntimeReport} as {@code <runtimeId>.json} under a {@code runtime/}
 * subdirectory of {@code forgebrain.local-storage.execution-report-directory} — mirrors {@code
 * ReelJobReportWriter}'s convention exactly.
 */
public interface RuntimeReportWriter {

    /**
     * @return the absolute path of the written report file
     */
    String write(RuntimeReport report);
}
