package com.forgebrain.backend.validation;

/**
 * Persists a {@link ProductionReadinessReport} as {@code <validationId>.json} under a {@code
 * validation/} subdirectory of {@code forgebrain.local-storage.execution-report-directory} —
 * mirrors {@code RuntimeReportWriter}'s convention exactly.
 */
public interface ProductionReadinessReportWriter {

    /**
     * @return the absolute path of the written report file
     */
    String write(ProductionReadinessReport report);
}
