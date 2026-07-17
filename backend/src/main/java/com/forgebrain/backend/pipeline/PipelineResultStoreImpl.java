package com.forgebrain.backend.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Saves each completed {@link PipelineResult} as one pretty-printed JSON file under {@link
 * LocalStorageConfig#pipelineOutputDirectory()} — the simplest durable approach for this
 * phase's local prototype (see {@code NEXT_EXECUTION.md}), mirroring
 * {@link com.forgebrain.backend.services.MemoryServiceImpl}'s file-based pattern.
 */
@Component
public class PipelineResultStoreImpl implements PipelineResultStore {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final File outputDirectory;

    public PipelineResultStoreImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.outputDirectory = new File(localStorageConfig.pipelineOutputDirectory());
    }

    @Override
    public void save(PipelineResult result) {
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new ConfigurationException("Could not create pipeline output directory at " + outputDirectory.getAbsolutePath());
        }
        String fileName = result.topicId() + "-" + FILE_TIMESTAMP.format(result.completedAt()) + ".json";
        File outputFile = new File(outputDirectory, fileName);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write pipeline result to " + outputFile.getAbsolutePath(), e);
        }
    }
}
