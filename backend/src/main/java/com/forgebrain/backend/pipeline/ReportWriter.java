package com.forgebrain.backend.pipeline;

/**
 * Persists pipeline execution reports.
 */
public interface ReportWriter {

    void write(PipelineExecutionReport report);
}
