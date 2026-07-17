package com.forgebrain.backend.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Performance metrics grouped across topics by a single strategy dimension and value (e.g.
 * every reel that used {@code hookType: MYTH}, regardless of topic) — the exact shape
 * {@code brain/content-director-spec.md} Section 8, {@code brain/script-spec.md} Section 10,
 * and {@code brain/storyboard-spec.md} Section 11 are all waiting on. A per-topic {@link
 * com.forgebrain.backend.models.MemoryState} record cannot answer this on its own — see
 * analytics/analytics-spec.md Section 5.
 *
 * @see <a href="../../../../../../../../analytics/analytics-schema.json">analytics/analytics-schema.json</a>
 */
public record StrategyPerformanceAggregate(
        String aggregateId,
        Dimension dimension,
        String value,
        int sampleSize,
        double averagePerformanceScore,
        double averageRetentionScore,
        List<String> contributingTopicIds,
        Instant lastUpdated
) {

    public enum Dimension {
        HOOK_TYPE, TEACHING_STYLE, VISUAL_STYLE, PACING, CODE_STYLE, CTA_STYLE,
        EMOTIONAL_GOAL, SCENE_TYPE, ANIMATION_STYLE
    }
}
