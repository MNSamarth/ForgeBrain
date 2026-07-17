package com.forgebrain.backend.shared;

import java.util.List;

/**
 * The {@code confidence_notes} shape used identically across nearly every schema in
 * {@code brain/}, {@code renderer/}, {@code reviewer/}, and {@code publishing/}: a coarse
 * confidence level, specific flagged uncertainties for reviewer attention, and a reserved,
 * currently-always-empty list of unresolved conflicts (see brain/research-spec.md Section 8
 * for why that field is reserved rather than populated in Phase 1).
 *
 * @param overallConfidence   coarse self-assessment of this output's grounding
 * @param flaggedUncertainties specific judgment calls or gaps worth reviewer attention
 * @param unresolvedConflicts  reserved for future multi-source/multi-variant contradiction
 *                              handling; always empty in Phase 1
 */
public record ConfidenceNotes(
        ConfidenceLevel overallConfidence,
        List<String> flaggedUncertainties,
        List<String> unresolvedConflicts
) {
}
