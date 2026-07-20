package com.forgebrain.backend.validation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The aggregated result of running every {@link PipelineInvariants}/{@link ArtifactValidator}
 * check against one real (or fixture) execution — this mission's "production-readiness report"
 * (Goal 3). {@code passed} is {@code true} iff every check in {@code violationsByCheck} came back
 * empty.
 *
 * @param violationsByCheck check name (e.g. {@code "stageRunsAtMostOnce"}) to the violations it
 *                          found — an empty list means that check passed
 */
public record ProductionReadinessReport(
        String validationId,
        Instant generatedAt,
        boolean passed,
        Map<String, List<String>> violationsByCheck
) {

    public static ProductionReadinessReport of(Map<String, List<String>> violationsByCheck) {
        boolean passed = violationsByCheck.values().stream().allMatch(List::isEmpty);
        return new ProductionReadinessReport(UUID.randomUUID().toString(), Instant.now(), passed,
                Map.copyOf(violationsByCheck));
    }
}
