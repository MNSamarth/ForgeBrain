package com.forgebrain.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgebrain.backend.BackendApplication;
import com.forgebrain.backend.config.AiGatewayConfig;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.config.RuntimeConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.job.LocalOutputStorage;
import com.forgebrain.backend.job.OutputStorage;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.publishing.PlatformPublishAdapterFactory;
import com.forgebrain.backend.publishing.YouTubeRealPublishAdapter;
import com.forgebrain.backend.publishing.YouTubeShortsPublishAdapter;
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
 * Per this mission's Part 5 ("Configuration Validation") — boots the real application (via {@link
 * SpringApplicationBuilder}, {@code spring.main.web-application-type=none} so no embedded server
 * is needed) under various configuration combinations, proving each either starts cleanly with
 * the expected beans, or fails fast and clearly. No real cloud credentials are used or required —
 * every scenario here is reachable with the committed, credential-free defaults plus property
 * overrides.
 */
class ConfigurationValidationTest {

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

    @Test
    void startsSuccessfullyWithTheDefaultDryRunConfiguration() {
        try (ConfigurableApplicationContext context = boot(Map.of())) {
            PlatformUploadConfig config = context.getBean(PlatformUploadConfig.class);
            assertThat(config.dryRunOnly()).isTrue();
            PlatformPublishAdapterFactory factory = context.getBean(PlatformPublishAdapterFactory.class);
            assertThat(factory.resolve(Script.Platform.YOUTUBE_SHORTS)).isInstanceOf(YouTubeShortsPublishAdapter.class);
        }
    }

    @Test
    void startsSuccessfullyWithPublishingDisabledPerPlatform() {
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.publishing.upload.youtube.enabled", "false",
                "forgebrain.publishing.upload.instagram.enabled", "false"))) {
            PlatformUploadConfig config = context.getBean(PlatformUploadConfig.class);
            assertThat(config.youtube().enabled()).isFalse();
            assertThat(config.instagram().enabled()).isFalse();
        }
    }

    @Test
    void startsSuccessfullyWhenRealUploadIsEnabledButCredentialsAreMissing() {
        // Missing credentials must fail clearly at publish time (see
        // YouTubeRealPublishAdapterTest), never block application startup.
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.publishing.upload.dry-run-only", "false",
                "forgebrain.publishing.upload.youtube.enabled", "true"))) {
            PlatformPublishAdapterFactory factory = context.getBean(PlatformPublishAdapterFactory.class);
            assertThat(factory.resolve(Script.Platform.YOUTUBE_SHORTS)).isInstanceOf(YouTubeRealPublishAdapter.class);
        }
    }

    @Test
    void startsSuccessfullyWithLocalStorageByDefault() {
        try (ConfigurableApplicationContext context = boot(Map.of())) {
            OutputStorage storage = context.getBean(OutputStorage.class);
            assertThat(storage).isInstanceOf(LocalOutputStorage.class);
        }
    }

    @Test
    void failsFastWhenCloudStorageIsEnabledWithoutABucket() {
        assertThatThrownBy(() -> boot(Map.of(
                "forgebrain.cloud-storage.enabled", "true",
                "forgebrain.cloud-storage.media-bucket", "")))
                .hasRootCauseInstanceOf(ConfigurationException.class);
    }

    @Test
    void bindsCustomRuntimeReelCountsAndRetryConfiguration() {
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.runtime.daily-reel-count", "7",
                "forgebrain.runtime.max-retries-per-reel", "4",
                "forgebrain.runtime.parallelism", "2"))) {
            RuntimeConfig config = context.getBean(RuntimeConfig.class);
            assertThat(config.dailyReelCount()).isEqualTo(7);
            assertThat(config.maxRetriesPerReel()).isEqualTo(4);
            assertThat(config.parallelism()).isEqualTo(2);
        }
    }

    @Test
    void bindsAiGatewayRetryAndTimeoutConfiguration() {
        try (ConfigurableApplicationContext context = boot(Map.of(
                "forgebrain.ai-gateway.max-retries", "5",
                "forgebrain.ai-gateway.timeout-millis", "9999"))) {
            AiGatewayConfig config = context.getBean(AiGatewayConfig.class);
            assertThat(config.maxRetries()).isEqualTo(5);
            assertThat(config.timeoutMillis()).isEqualTo(9999);
        }
    }
}
