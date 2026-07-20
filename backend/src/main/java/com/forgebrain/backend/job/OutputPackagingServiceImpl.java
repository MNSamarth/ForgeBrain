package com.forgebrain.backend.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.VideoPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OutputPackagingServiceImpl implements OutputPackagingService {

    private static final String METADATA_FILE_NAME = "metadata.json";

    private final ObjectMapper objectMapper;
    private final OutputStorage outputStorage;

    public OutputPackagingServiceImpl(ObjectMapper objectMapper, OutputStorage outputStorage) {
        this.objectMapper = objectMapper;
        this.outputStorage = outputStorage;
    }

    @Override
    public ReelOutputPackage packageOutputs(String jobId, Path renderDirectory, VideoPackage videoPackage,
            Path subtitleFile) {
        Path metadataFile = writeMetadata(renderDirectory, videoPackage);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("video", outputStorage.store(jobId, Path.of(videoPackage.videoFileUri())));
        if (videoPackage.thumbnailFrameUri() != null) {
            files.put("thumbnail", outputStorage.store(jobId, Path.of(videoPackage.thumbnailFrameUri())));
        }
        if (Files.isRegularFile(subtitleFile)) {
            files.put("subtitles", outputStorage.store(jobId, subtitleFile));
        }
        files.put("metadata", outputStorage.store(jobId, metadataFile));

        return new ReelOutputPackage(jobId, renderDirectory.toString(), Map.copyOf(files));
    }

    @Override
    public String storeReport(String jobId, Path reportFile) {
        return outputStorage.store(jobId, reportFile);
    }

    @Override
    public String storePublishingArtifact(String jobId, Path file) {
        return outputStorage.store(jobId, file);
    }

    private Path writeMetadata(Path renderDirectory, VideoPackage videoPackage) {
        Path metadataFile = renderDirectory.resolve(METADATA_FILE_NAME);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), videoPackage);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write metadata.json to " + metadataFile, e);
        }
        return metadataFile;
    }
}
