package com.forgebrain.backend.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Saves each {@link PipelineExecutionReport} as one JSON file under the configured reports
 * directory.
 */
@Component
public class ReportWriterImpl implements ReportWriter {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(java.time.ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final File reportsDirectory;

    public ReportWriterImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.reportsDirectory = new File(localStorageConfig.executionReportDirectory());
    }

    @Override
    public void write(PipelineExecutionReport report) {
        if (!reportsDirectory.exists() && !reportsDirectory.mkdirs()) {
            throw new ConfigurationException("Could not create reports directory at "
                    + reportsDirectory.getAbsolutePath());
        }
        String fileName = "pipeline-report-" + FILE_TIMESTAMP.format(report.executionStart()) + ".json";
        File outputFile = new File(reportsDirectory, fileName);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write pipeline execution report to "
                    + outputFile.getAbsolutePath(), e);
        }
    }
}
