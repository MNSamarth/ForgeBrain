package com.forgebrain.backend.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Saves each {@link ReelExportReport} as {@code report.json} inside the render run's own output
 * folder — mirrors {@link ReportWriterImpl}'s JSON-file-per-run convention.
 */
@Component
public class ReelExportReportWriterImpl implements ReelExportReportWriter {

    private final ObjectMapper objectMapper;

    public ReelExportReportWriterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String write(ReelExportReport report, Path outputDirectory) {
        File directory = outputDirectory.toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ConfigurationException("Could not create reel export output directory at "
                    + directory.getAbsolutePath());
        }
        File outputFile = new File(directory, "report.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write reel export report to "
                    + outputFile.getAbsolutePath(), e);
        }
        return outputFile.getAbsolutePath();
    }
}
