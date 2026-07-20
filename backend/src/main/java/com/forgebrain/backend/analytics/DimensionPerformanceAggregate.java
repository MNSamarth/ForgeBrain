package com.forgebrain.backend.analytics;

import java.time.Instant;
import java.util.List;

/**
 * {@link ReelOutcomeSnapshot}s grouped across topics by hook type, teaching style, or platform
 * target — the pipeline-outcome sibling of {@link StrategyPerformanceAggregate}, which groups the
 * same kind of strategy fields but by real <em>audience</em> performance (still not active, no
 * publishing integration posts anywhere real yet). This one groups by review-and-publish outcome
 * instead, using signals the pipeline already has today. Kept as a distinct type rather than
 * reusing {@link StrategyPerformanceAggregate} because the two score fields would otherwise mean
 * different things (internal review score vs. real engagement) under the same name.
 *
 * @param dimension            which categorical field this aggregate groups by
 * @param value                the specific value within that dimension, e.g. {@code "MYTH"} for
 *                             {@link Dimension#HOOK_TYPE}
 * @param sampleSize           how many snapshots contributed
 * @param averageReviewScore   mean {@link ReelOutcomeSnapshot#reviewScore()} across snapshots
 *                             that reached review; {@code 0.0} if none did
 * @param approvalRate         share of reviewed snapshots with verdict {@code APPROVED}
 * @param contributingTopicIds distinct topic ids behind this aggregate, for traceability back to
 *                             individual snapshots — mirrors {@link
 *                             StrategyPerformanceAggregate#contributingTopicIds()}
 */
public record DimensionPerformanceAggregate(
        String aggregateId,
        Dimension dimension,
        String value,
        int sampleSize,
        double averageReviewScore,
        double approvalRate,
        List<String> contributingTopicIds,
        Instant lastUpdated
) {

    public enum Dimension {
        HOOK_TYPE, TEACHING_STYLE, PLATFORM
    }
}
