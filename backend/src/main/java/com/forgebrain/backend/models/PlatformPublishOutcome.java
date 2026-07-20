package com.forgebrain.backend.models;

import java.time.Instant;

/**
 * One platform adapter's result for one {@link PublishingPackage} — produced by {@link
 * com.forgebrain.backend.publishing.PlatformPublishAdapter#publish}. {@code dryRun} is {@code
 * true} for {@code YouTubeShortsPublishAdapter}/{@code InstagramReelsPublishAdapter} (writes a
 * payload file instead of calling a real platform API) and {@code false} for {@code
 * YouTubeRealPublishAdapter}/{@code InstagramRealPublishAdapter} (a real upload) — which adapter
 * ran for a given platform is decided by {@code PlatformPublishAdapterFactory} — see
 * backend/README.md's "Real Platform Publishing" section.
 *
 * @param payloadReference where the exact payload sent is recorded — a local file path for a
 *                          dry-run adapter, a real platform video/media id for a real one
 */
public record PlatformPublishOutcome(
        Script.Platform platform,
        boolean dryRun,
        boolean success,
        String payloadReference,
        String message,
        Instant completedAt
) {
}
