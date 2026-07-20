package com.forgebrain.backend.models;

import java.time.Instant;

/**
 * One platform adapter's result for one {@link PublishingPackage} — produced by {@link
 * com.forgebrain.backend.publishing.PlatformPublishAdapter#publish}. {@code dryRun} is always
 * {@code true} today, since every registered adapter is currently a dry-run adapter that writes
 * a payload file instead of calling a real platform API (see backend/README.md's "Publishing"
 * section) — kept as an explicit field, not assumed, so a future real adapter's result looks
 * identical in shape and callers don't need to change.
 *
 * @param payloadReference where the exact payload sent (or, today, would have been sent) is
 *                          written — a local file path for a dry-run adapter, a platform video/
 *                          post ID for a future real one
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
