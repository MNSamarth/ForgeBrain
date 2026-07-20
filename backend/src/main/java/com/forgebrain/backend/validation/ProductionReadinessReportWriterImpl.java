package com.forgebrain.backend.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class ProductionReadinessReportWriterImpl implements ProductionReadinessReportWriter {

    private final ObjectMapper objectMapper;
    private final LocalStorageConfig localStorageConfig;

    public ProductionReadinessReportWriterImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.localStorageConfig = localStorageConfig;
    }

    @Override
    public String write(ProductionReadinessReport report) {
        File directory = new File(localStorageConfig.executionReportDirectory(), "validation");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ConfigurationException("Could not create validation report directory at "
                    + directory.getAbsolutePath());
        }
        File file = new File(directory, report.validationId() + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write validation report to " + file.getAbsolutePath(), e);
        }
        return file.getAbsolutePath();
    }
}
