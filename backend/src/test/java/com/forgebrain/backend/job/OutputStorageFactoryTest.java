package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.JobStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;

/**
 * Spring-free by design: {@link OutputStorageFactory} is meant to be deterministic and testable
 * without a Spring context or real GCP credentials, per this mission's Part 4.
 */
class OutputStorageFactoryTest {

    private static LocalOutputStorage localOutputStorage() {
        return new LocalOutputStorage(new JobStorageConfig("jobs", "output"));
    }

    @Test
    void returnsLocalStorageByDefaultAndNeverTouchesTheGcsClientSupplier() {
        CloudStorageConfig disabled = new CloudStorageConfig(false, "", "", "", "", "");
        LocalOutputStorage local = localOutputStorage();
        OutputStorageFactory factory = new OutputStorageFactory(disabled, local,
                () -> {
                    throw new AssertionError("the GCS client supplier must not be invoked when disabled");
                });

        assertThat(factory.resolve()).isSameAs(local);
    }

    @Test
    void returnsCloudStorageWhenExplicitlyEnabledWithAValidBucket() {
        CloudStorageConfig enabled = new CloudStorageConfig(true, "my-bucket", "", "", "reels", "");
        Storage mockStorage = mock(Storage.class);
        OutputStorageFactory factory = new OutputStorageFactory(enabled, localOutputStorage(), () -> mockStorage);

        OutputStorage resolved = factory.resolve();

        assertThat(resolved).isInstanceOf(CloudStorageOutputStorage.class);
    }

    @Test
    void failsClearlyWhenEnabledButNoBucketIsConfiguredAndNeverTouchesTheSupplier() {
        CloudStorageConfig misconfigured = new CloudStorageConfig(true, "", "", "", "reels", "");
        OutputStorageFactory factory = new OutputStorageFactory(misconfigured, localOutputStorage(),
                () -> {
                    throw new AssertionError("the GCS client supplier must not be invoked before validation passes");
                });

        assertThatThrownBy(factory::resolve)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("media-bucket");
    }

    @Test
    void treatsABlankBucketTheSameAsAMissingOne() {
        CloudStorageConfig blankBucket = new CloudStorageConfig(true, "   ", "", "", "reels", "");
        OutputStorageFactory factory = new OutputStorageFactory(blankBucket, localOutputStorage(),
                () -> {
                    throw new AssertionError("the GCS client supplier must not be invoked before validation passes");
                });

        assertThatThrownBy(factory::resolve).isInstanceOf(ConfigurationException.class);
    }
}
