package com.forgebrain.backend.publishing;

import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.Script;
import java.util.Map;
import java.util.Set;

/**
 * Chooses which {@link PlatformPublishAdapter} handles a given platform: the real adapter when
 * real upload is enabled for that platform (and not overridden off globally), the dry-run adapter
 * otherwise. Deterministic and Spring-free, mirroring {@link
 * com.forgebrain.backend.job.OutputStorageFactory}'s exact "one factory picks between two plain,
 * non-{@code @Component} candidates" pattern for the same kind of decision (local vs. cloud
 * storage there, dry-run vs. real upload here) — directly unit-testable with a plain {@link
 * PlatformUploadConfig} and fake adapters, no Spring context required.
 *
 * <p>Every platform always has a dry-run adapter registered; a real adapter is optional per
 * platform. If real upload is enabled for a platform that has no real adapter registered, this
 * falls back to the dry-run adapter rather than failing — "no real adapter exists yet for this
 * platform" is not a misconfiguration.
 */
public class PlatformPublishAdapterFactory {

    private final PlatformUploadConfig config;
    private final Map<Script.Platform, PlatformPublishAdapter> dryRunAdapters;
    private final Map<Script.Platform, PlatformPublishAdapter> realAdapters;

    public PlatformPublishAdapterFactory(PlatformUploadConfig config,
            Map<Script.Platform, PlatformPublishAdapter> dryRunAdapters,
            Map<Script.Platform, PlatformPublishAdapter> realAdapters) {
        this.config = config;
        this.dryRunAdapters = Map.copyOf(dryRunAdapters);
        this.realAdapters = Map.copyOf(realAdapters);
    }

    /**
     * @throws ConfigurationException if no adapter — real or dry-run — is registered for {@code
     * platform} at all
     */
    public PlatformPublishAdapter resolve(Script.Platform platform) {
        if (useReal(platform)) {
            PlatformPublishAdapter real = realAdapters.get(platform);
            if (real != null) {
                return real;
            }
        }
        PlatformPublishAdapter dryRun = dryRunAdapters.get(platform);
        if (dryRun == null) {
            throw new ConfigurationException(
                    "No publish adapter (real or dry-run) is registered for platform " + platform + ".");
        }
        return dryRun;
    }

    /** Every platform this factory can resolve an adapter for — driven by the dry-run adapters. */
    public Set<Script.Platform> supportedPlatforms() {
        return dryRunAdapters.keySet();
    }

    private boolean useReal(Script.Platform platform) {
        if (config.dryRunOnly()) {
            return false;
        }
        return switch (platform) {
            case YOUTUBE_SHORTS -> config.youtube() != null && config.youtube().enabled();
            case INSTAGRAM_REELS -> config.instagram() != null && config.instagram().enabled();
            default -> false;
        };
    }
}
