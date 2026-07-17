package com.forgebrain.backend.services;

import com.forgebrain.backend.analytics.PerformanceSnapshot;
import com.forgebrain.backend.analytics.StrategyPerformanceAggregate;
import com.forgebrain.backend.models.PublishingPackage;
import java.util.List;

/**
 * Contract for capturing real audience performance and aggregating it by strategy dimension.
 * See analytics/analytics-spec.md. <b>Not active in Phase 1</b> — there is no publishing
 * integration yet to source real metrics from (see analytics-spec.md Section 7).
 */
public interface AnalyticsService {

    /**
     * Records a performance snapshot for one published reel and writes the derived scores
     * back into memory (see analytics/analytics-spec.md Section 6).
     */
    PerformanceSnapshot captureSnapshot(PublishingPackage publishingPackage, PerformanceSnapshot.AudienceResponse metrics);

    /**
     * Aggregates performance across topics by a single strategy dimension (e.g. every reel
     * that used {@code hookType: MYTH}) — the shape the Content Director, Script, and
     * Storyboard stages' future extensibility sections are waiting on. See
     * analytics/analytics-spec.md Section 5.
     */
    List<StrategyPerformanceAggregate> aggregateByDimension(StrategyPerformanceAggregate.Dimension dimension);
}
