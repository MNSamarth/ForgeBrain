package com.forgebrain.backend.analytics;

import java.time.Instant;
import com.forgebrain.backend.models.Script;

/**
 * One reel's real performance metrics at a point in time. Field names in {@link
 * AudienceResponse} deliberately match {@code MemoryState.AudienceResponse} exactly, since
 * this is what gets written back into it. See analytics/analytics-spec.md Section 5.
 *
 * @see <a href="../../../../../../../../analytics/analytics-schema.json">analytics/analytics-schema.json</a>
 */
public record PerformanceSnapshot(
        String snapshotId,
        String topicId,
        String basedOnPublishingPackageId,
        Script.Platform platform,
        AudienceResponse metrics,
        double computedPerformanceScore,
        double computedRetentionScore,
        Instant snapshotTakenAt,
        String snapshotVersion
) {

    public record AudienceResponse(
            int viewCount,
            double watchTimeSeconds,
            double averageRetentionPercent,
            int likes,
            int comments,
            int shares,
            int saves,
            int followerConversion
    ) {
    }
}
