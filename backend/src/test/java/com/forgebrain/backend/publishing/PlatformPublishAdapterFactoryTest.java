package com.forgebrain.backend.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlatformPublishAdapterFactory} — per this mission's Part 6 ("adapter
 * selection", "dry-run vs real mode selection"). Deterministic and Spring-free, mirroring {@code
 * OutputStorageFactoryTest}'s style for the same kind of decision.
 */
class PlatformPublishAdapterFactoryTest {

    private static final FakeAdapter DRY_RUN_YOUTUBE = new FakeAdapter(Script.Platform.YOUTUBE_SHORTS, true);
    private static final FakeAdapter DRY_RUN_INSTAGRAM = new FakeAdapter(Script.Platform.INSTAGRAM_REELS, true);
    private static final FakeAdapter REAL_YOUTUBE = new FakeAdapter(Script.Platform.YOUTUBE_SHORTS, false);
    private static final FakeAdapter REAL_INSTAGRAM = new FakeAdapter(Script.Platform.INSTAGRAM_REELS, false);

    private static final Map<Script.Platform, PlatformPublishAdapter> DRY_RUN_ADAPTERS = Map.of(
            Script.Platform.YOUTUBE_SHORTS, DRY_RUN_YOUTUBE,
            Script.Platform.INSTAGRAM_REELS, DRY_RUN_INSTAGRAM);
    private static final Map<Script.Platform, PlatformPublishAdapter> REAL_ADAPTERS = Map.of(
            Script.Platform.YOUTUBE_SHORTS, REAL_YOUTUBE,
            Script.Platform.INSTAGRAM_REELS, REAL_INSTAGRAM);

    private static PlatformUploadConfig config(boolean dryRunOnly, boolean youtubeEnabled,
            boolean instagramEnabled) {
        return new PlatformUploadConfig(dryRunOnly,
                new PlatformUploadConfig.YouTube(youtubeEnabled, "id", "secret", "token", "channel", "private", "27"),
                new PlatformUploadConfig.Instagram(instagramEnabled, "token", "ig-user", 0, 3));
    }

    @Test
    void dryRunOnlyForcesTheDryRunAdapterEvenWhenBothPlatformsAreEnabled() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(true, true, true),
                DRY_RUN_ADAPTERS, REAL_ADAPTERS);

        assertThat(factory.resolve(Script.Platform.YOUTUBE_SHORTS)).isSameAs(DRY_RUN_YOUTUBE);
        assertThat(factory.resolve(Script.Platform.INSTAGRAM_REELS)).isSameAs(DRY_RUN_INSTAGRAM);
    }

    @Test
    void resolvesTheRealAdapterWhenDryRunOnlyIsFalseAndThatPlatformIsEnabled() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(false, true, false),
                DRY_RUN_ADAPTERS, REAL_ADAPTERS);

        assertThat(factory.resolve(Script.Platform.YOUTUBE_SHORTS)).isSameAs(REAL_YOUTUBE);
    }

    @Test
    void resolvesTheDryRunAdapterForAPlatformThatIsNotIndividuallyEnabled() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(false, true, false),
                DRY_RUN_ADAPTERS, REAL_ADAPTERS);

        assertThat(factory.resolve(Script.Platform.INSTAGRAM_REELS)).isSameAs(DRY_RUN_INSTAGRAM);
    }

    @Test
    void fallsBackToDryRunWhenEnabledButNoRealAdapterIsRegisteredForThatPlatform() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(false, true, true),
                DRY_RUN_ADAPTERS, Map.of(Script.Platform.YOUTUBE_SHORTS, REAL_YOUTUBE));

        assertThat(factory.resolve(Script.Platform.INSTAGRAM_REELS)).isSameAs(DRY_RUN_INSTAGRAM);
    }

    @Test
    void throwsWhenNoAdapterAtAllIsRegisteredForAPlatform() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(true, false, false),
                Map.of(Script.Platform.YOUTUBE_SHORTS, DRY_RUN_YOUTUBE), Map.of());

        assertThatThrownBy(() -> factory.resolve(Script.Platform.INSTAGRAM_REELS))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("INSTAGRAM_REELS");
    }

    @Test
    void supportedPlatformsComesFromTheDryRunAdapterMap() {
        PlatformPublishAdapterFactory factory = new PlatformPublishAdapterFactory(config(true, false, false),
                DRY_RUN_ADAPTERS, Map.of());

        assertThat(factory.supportedPlatforms())
                .containsExactlyInAnyOrder(Script.Platform.YOUTUBE_SHORTS, Script.Platform.INSTAGRAM_REELS);
    }

    private record FakeAdapter(Script.Platform platform, boolean dryRun) implements PlatformPublishAdapter {
        @Override
        public Script.Platform supportedPlatform() {
            return platform;
        }

        @Override
        public PlatformPublishOutcome publish(PublishingPackage publishingPackage,
                PublishingMetadata platformMetadata, Path payloadDirectory) {
            return new PlatformPublishOutcome(platform, dryRun, true, "ref", "ok", Instant.now());
        }
    }
}
