package com.forgebrain.backend.job;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Cloud Storage-backed {@link OutputStorage}: uploads each file to {@code
 * gs://<mediaBucket>/<outputPrefix>/<jobId>/<fileName>} and returns that {@code gs://} URI. The
 * {@link Storage} client is constructor-injected rather than constructed internally, so this
 * class never performs Application Default Credentials discovery itself and is directly
 * unit-testable with a mock client — no real GCP project or credentials required. Selected in
 * place of {@link LocalOutputStorage} by {@link OutputStorageFactory} when {@code
 * forgebrain.cloud-storage.enabled} is {@code true}.
 */
public class CloudStorageOutputStorage implements OutputStorage {

    private final Storage storage;
    private final String bucket;
    private final String outputPrefix;

    public CloudStorageOutputStorage(Storage storage, CloudStorageConfig cloudStorageConfig) {
        this.storage = storage;
        this.bucket = cloudStorageConfig.mediaBucket();
        this.outputPrefix = normalizePrefix(cloudStorageConfig.outputPrefix());
    }

    @Override
    public String store(String jobId, Path localFile) {
        String objectName = outputPrefix + jobId + "/" + localFile.getFileName();
        BlobId blobId = BlobId.of(bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        try {
            storage.createFrom(blobInfo, localFile);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to upload output file '" + localFile + "' for job '" + jobId + "' to gs://" + bucket
                            + "/" + objectName, e);
        } catch (StorageException e) {
            throw new ConfigurationException(
                    "Cloud Storage rejected upload of '" + localFile + "' for job '" + jobId + "' to gs://" + bucket
                            + "/" + objectName + ": " + e.getMessage(), e);
        }
        return "gs://" + bucket + "/" + objectName;
    }

    private static String normalizePrefix(String outputPrefix) {
        if (outputPrefix == null || outputPrefix.isBlank()) {
            return "";
        }
        String trimmed = outputPrefix.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + "/";
    }
}
