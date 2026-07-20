package com.forgebrain.backend.job;

import com.forgebrain.backend.config.JobStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local-disk {@link OutputStorage}: copies each file into {@code
 * <outputStorageRoot>/<jobId>/<fileName>}, mirroring the bucket/prefix layout {@link
 * CloudStorageOutputStorage} uses. A real copy, not a passthrough — this exercises the same
 * "take a local file, persist it somewhere durable, return a stable reference" contract the
 * Cloud Storage implementation does, so swapping between them is a drop-in change, not a
 * redesign. The default backend — see {@link OutputStorageFactory} for how the active backend is
 * chosen. Not a Spring {@code @Component}: constructed by {@link OutputStorageFactory}'s
 * {@code @Bean} method, so exactly one {@link OutputStorage} bean ever exists.
 */
public class LocalOutputStorage implements OutputStorage {

    private final Path storageRoot;

    public LocalOutputStorage(JobStorageConfig jobStorageConfig) {
        this.storageRoot = Path.of(jobStorageConfig.outputStorageRoot());
    }

    @Override
    public String store(String jobId, Path localFile) {
        Path jobStorageDirectory = storageRoot.resolve(jobId);
        try {
            Files.createDirectories(jobStorageDirectory);
            Path destination = jobStorageDirectory.resolve(localFile.getFileName());
            Files.copy(localFile, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to store output file '" + localFile + "' for job '" + jobId + "'.", e);
        }
    }
}
