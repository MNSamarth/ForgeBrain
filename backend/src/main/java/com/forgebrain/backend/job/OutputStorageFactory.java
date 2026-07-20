package com.forgebrain.backend.job;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.google.cloud.storage.Storage;
import java.util.function.Supplier;

/**
 * Chooses which {@link OutputStorage} backend is active: {@link LocalOutputStorage} by default,
 * or {@link CloudStorageOutputStorage} when {@code forgebrain.cloud-storage.enabled} is {@code
 * true}. Deterministic and Spring-free, so it's directly unit-testable with a plain {@link
 * CloudStorageConfig} and a mock {@link Storage} supplier — no Spring context or real GCP
 * credentials required.
 *
 * <p>The GCS client is supplied lazily: {@code gcsClientSupplier.get()} is only ever called when
 * cloud storage is both enabled and validated, so a misconfigured-but-disabled setup, or a
 * disabled setup on a machine with no GCP credentials at all, never touches the supplier and
 * never fails.
 */
public class OutputStorageFactory {

    private final CloudStorageConfig cloudStorageConfig;
    private final LocalOutputStorage localOutputStorage;
    private final Supplier<Storage> gcsClientSupplier;

    public OutputStorageFactory(CloudStorageConfig cloudStorageConfig, LocalOutputStorage localOutputStorage,
            Supplier<Storage> gcsClientSupplier) {
        this.cloudStorageConfig = cloudStorageConfig;
        this.localOutputStorage = localOutputStorage;
        this.gcsClientSupplier = gcsClientSupplier;
    }

    /**
     * @throws ConfigurationException if cloud storage is enabled but {@code
     * forgebrain.cloud-storage.media-bucket} is blank — a misconfiguration that should fail
     * loudly at startup rather than silently falling back, since the user explicitly opted in
     */
    public OutputStorage resolve() {
        if (!cloudStorageConfig.enabled()) {
            return localOutputStorage;
        }
        if (cloudStorageConfig.mediaBucket() == null || cloudStorageConfig.mediaBucket().isBlank()) {
            throw new ConfigurationException(
                    "forgebrain.cloud-storage.enabled is true but forgebrain.cloud-storage.media-bucket is "
                            + "blank; set a bucket name, or set enabled: false to use local storage.");
        }
        return new CloudStorageOutputStorage(gcsClientSupplier.get(), cloudStorageConfig);
    }
}
