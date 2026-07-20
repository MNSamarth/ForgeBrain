package com.forgebrain.backend.job;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.JobStorageConfig;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single active {@link OutputStorage} bean through {@link OutputStorageFactory}.
 * {@link LocalOutputStorage} and {@link CloudStorageOutputStorage} are plain classes, not
 * {@code @Component}s, precisely so this is the only place that decides between them — with two
 * component-scanned implementations of the same interface, Spring would have no unambiguous
 * candidate to autowire into {@link OutputPackagingServiceImpl}.
 */
@Configuration
public class OutputStorageBeanConfig {

    @Bean
    public OutputStorage outputStorage(CloudStorageConfig cloudStorageConfig, JobStorageConfig jobStorageConfig) {
        LocalOutputStorage localOutputStorage = new LocalOutputStorage(jobStorageConfig);
        OutputStorageFactory factory = new OutputStorageFactory(cloudStorageConfig, localOutputStorage,
                () -> buildGcsClient(cloudStorageConfig));
        return factory.resolve();
    }

    private static Storage buildGcsClient(CloudStorageConfig cloudStorageConfig) {
        StorageOptions.Builder options = StorageOptions.newBuilder();
        if (cloudStorageConfig.projectId() != null && !cloudStorageConfig.projectId().isBlank()) {
            options.setProjectId(cloudStorageConfig.projectId());
        }
        return options.build().getService();
    }
}
