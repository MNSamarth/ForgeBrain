package com.forgebrain.backend.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class RuntimeReportWriterImpl implements RuntimeReportWriter {

    private final ObjectMapper objectMapper;
    private final LocalStorageConfig localStorageConfig;

    public RuntimeReportWriterImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.localStorageConfig = localStorageConfig;
    }

    @Override
    public String write(RuntimeReport report) {
        File directory = new File(localStorageConfig.executionReportDirectory(), "runtime");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ConfigurationException("Could not create runtime report directory at "
                    + directory.getAbsolutePath());
        }
        File file = new File(directory, report.runtimeId() + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write runtime report to " + file.getAbsolutePath(), e);
        }
        return file.getAbsolutePath();
    }
}
