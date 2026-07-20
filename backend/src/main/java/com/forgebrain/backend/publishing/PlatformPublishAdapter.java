package com.forgebrain.backend.publishing;

import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.nio.file.Path;

/**
 * Contract for actually getting a {@link PublishingPackage} onto one platform. {@link
 * PublishingServiceImpl} looks up the adapter registered for each of the package's {@code
 * platformVariants} and calls it — one adapter bean per {@link Script.Platform}, so adding a new
 * platform is "register one more bean," not a change to this interface or to {@code
 * PublishingServiceImpl}.
 *
 * <p>Every adapter registered today ({@code YouTubeShortsPublishAdapter}, {@code
 * InstagramReelsPublishAdapter}) extends {@link AbstractDryRunPlatformPublishAdapter}: no real
 * platform credentials exist in this project yet, so "publishing" means writing the exact payload
 * a real upload call would send to a local file instead of sending it — see {@code
 * PlatformPublishOutcome#dryRun()}. A future real adapter (e.g. calling the YouTube Data API)
 * implements this same interface directly; nothing above this seam changes.
 */
public interface PlatformPublishAdapter {

    Script.Platform supportedPlatform();

    /**
     * @param platformMetadata the platform-specific {@link PublishingMetadata} variant to publish
     *                         (already formatted by a {@link PlatformFormatter} if one is
     *                         registered for this platform, otherwise the package's default)
     * @param payloadDirectory where a dry-run adapter should write its payload file; ignored by a
     *                         real adapter that actually calls a platform API
     */
    PlatformPublishOutcome publish(PublishingPackage publishingPackage, PublishingMetadata platformMetadata,
            Path payloadDirectory);
}
