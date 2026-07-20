package com.forgebrain.backend.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class ReelJobReportWriterImpl implements ReelJobReportWriter {

    private final ObjectMapper objectMapper;

    public ReelJobReportWriterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String write(ReelJobReport report, Path outputDirectory) {
        File directory = outputDirectory.toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ConfigurationException("Could not create job output directory at "
                    + directory.getAbsolutePath());
        }
        File outputFile = new File(directory, "report.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write job report to " + outputFile.getAbsolutePath(), e);
        }
        return outputFile.getAbsolutePath();
    }
}
