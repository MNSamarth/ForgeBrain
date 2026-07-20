package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.JobStorageConfig;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@code @Bean} factory method directly (no Spring context needed) to prove the
 * default, committed configuration ({@code forgebrain.cloud-storage.enabled: false}) resolves to
 * local storage without ever attempting to construct a real Cloud Storage client — so this test
 * needs no GCP project or credentials, matching every other test in this package.
 */
class OutputStorageBeanConfigTest {

    @Test
    void producesLocalOutputStorageWhenCloudStorageIsDisabled() {
        OutputStorageBeanConfig beanConfig = new OutputStorageBeanConfig();
        CloudStorageConfig disabled = new CloudStorageConfig(false, "", "", "", "reels", "");
        JobStorageConfig jobStorageConfig = new JobStorageConfig("jobs", "output");

        OutputStorage outputStorage = beanConfig.outputStorage(disabled, jobStorageConfig);

        assertThat(outputStorage).isInstanceOf(LocalOutputStorage.class);
    }
}
