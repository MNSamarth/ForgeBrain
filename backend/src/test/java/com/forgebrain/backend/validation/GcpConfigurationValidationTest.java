package com.forgebrain.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.forgebrain.backend.BackendApplication;
import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.config.GcpConfig;
import com.forgebrain.backend.config.JobStorageConfig;
import com.forgebrain.backend.gcp.CloudConnectivitySmokeTestRunner;
import com.forgebrain.backend.job.CloudStorageOutputStorage;
import com.forgebrain.backend.job.LocalOutputStorage;
import com.forgebrain.backend.job.OutputStorage;
import com.forgebrain.backend.job.OutputStorageFactory;
import com.google.cloud.storage.Storage;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Per this mission's Part 5 ("reading the GCP config values", "cloud enabled/disabled
 * selection", "smoke-test gating", "local fallback still working", "bucket routing using
 * forgebrain-artifacts"). No real cloud credentials are used or required. Mirrors {@code
 * ConfigurationValidationTest}'s {@link SpringApplicationBuilder}-based approach.
 */
class GcpConfigurationValidationTest {

    @TempDir
    Path tempDir;

    private Map<String, String> baseOverrides() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("forgebrain.local-storage.memory-state-path",
                new File(tempDir.toFile(), "memory-state.json").getAbsolutePath());
        props.put("forgebrain.local-storage.pipeline-output-directory",
                new File(tempDir.toFile(), "output").getAbsolutePath());
        props.put("forgebrain.local-storage.execution-report-directory",
                new File(tempDir.toFile(), "reports").getAbsolutePath());
        props.put("forgebrain.rendering.output-directory", new File(tempDir.toFile(), "renders").getAbsolutePath());
        props.put("forgebrain.rendering.voiceover-assets-directory",
                new File(tempDir.toFile(), "voiceover").getAbsolutePath());
        props.put("forgebrain.rendering.assets-directory",
                new File(tempDir.toFile(), "empty-assets").getAbsolutePath());
        props.put("forgebrain.jobs.jobs-directory", new File(tempDir.toFile(), "jobs").getAbsolutePath());
        props.put("forgebrain.jobs.output-storage-root", new File(tempDir.toFile(), "job-output").getAbsolutePath());
        props.put("forgebrain.analytics.snapshots-directory",
                new File(tempDir.toFile(), "analytics/snapshots").getAbsolutePath());
        props.put("forgebrain.analytics.reports-directory",
                new File(tempDir.toFile(), "analytics/reports").getAbsolutePath());
        return props;
    }

    private ConfigurableApplicationContext boot(Map<String, String> overrides) {
        Map<String, String> props = new LinkedHashMap<>(baseOverrides());
        props.putAll(overrides);
        String[] args = props.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    // ------------------------------------------------------------------------- reading config

    @Test
    void bindsTheLiveGcpConfigValuesFromProperties() {
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.gcp.project-id", "forgebrain-prod",
                "forgebrain.gcp.region", "us-central1",
                "forgebrain.gcp.storage-bucket", "forgebrain-artifacts",
                "forgebrain.gcp.vertex-ai-enabled", "true",
                "forgebrain.gcp.gcs-enabled", "true"))) {
            GcpConfig config = context.getBean(GcpConfig.class);

            assertThat(config.projectId()).isEqualTo("forgebrain-prod");
            assertThat(config.region()).isEqualTo("us-central1");
            assertThat(config.storageBucket()).isEqualTo("forgebrain-artifacts");
            assertThat(config.vertexAiEnabled()).isTrue();
            assertThat(config.gcsEnabled()).isTrue();
        }
    }

    // -------------------------------------------------------------- cloud enabled/disabled + fallback

    @Test
    void cloudIsDisabledByDefaultAndLocalStorageRemainsTheFallback() {
        try (ConfigurableApplicationContext context = boot(Map.of())) {
            GcpConfig config = context.getBean(GcpConfig.class);
            assertThat(config.vertexAiEnabled()).isFalse();
            assertThat(config.gcsEnabled()).isFalse();

            OutputStorage storage = context.getBean(OutputStorage.class);
            assertThat(storage).isInstanceOf(LocalOutputStorage.class);
        }
    }

    // --------------------------------------------------------------------------- smoke-test gating

    @Test
    void theSmokeTestRunnerIsAbsentByDefault() {
        try (ConfigurableApplicationContext context = boot(Map.of())) {
            assertThat(context.getBeanNamesForType(CloudConnectivitySmokeTestRunner.class)).isEmpty();
        }
    }

    @Test
    void theSmokeTestRunnerIsPresentAndSafeWhenExplicitlyEnabledWithCloudStillOff() {
        // smoke-test-on-startup=true makes the runner run automatically as part of context
        // startup — safe here because vertex-ai-enabled/gcs-enabled stay false (the default), so
        // both checks it performs report SKIPPED without any real network call.
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.gcp.smoke-test-on-startup", "true"))) {
            assertThat(context.getBeanNamesForType(CloudConnectivitySmokeTestRunner.class)).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------- bucket routing

    @Test
    void bucketRoutingSelectsCloudStorageOutputStorageForForgebrainArtifactsWhenEnabled() {
        CloudStorageConfig cloudStorageConfig = new CloudStorageConfig(true, "forgebrain-artifacts", "", "", "reels",
                "forgebrain-prod");
        OutputStorageFactory factory = new OutputStorageFactory(cloudStorageConfig, localOutputStorage(),
                () -> mock(Storage.class));

        OutputStorage resolved = factory.resolve();

        assertThat(resolved).isInstanceOf(CloudStorageOutputStorage.class);
    }

    @Test
    void bucketRoutingStaysLocalWhenCloudStorageIsDisabled() {
        CloudStorageConfig cloudStorageConfig = new CloudStorageConfig(false, "", "", "", "reels", "");
        LocalOutputStorage localOutputStorage = localOutputStorage();
        OutputStorageFactory factory = new OutputStorageFactory(cloudStorageConfig, localOutputStorage,
                () -> mock(Storage.class));

        assertThat(factory.resolve()).isSameAs(localOutputStorage);
    }

    private LocalOutputStorage localOutputStorage() {
        return new LocalOutputStorage(new JobStorageConfig(
                new File(tempDir.toFile(), "jobs-factory-test").getAbsolutePath(),
                new File(tempDir.toFile(), "job-output-factory-test").getAbsolutePath()));
    }
}
