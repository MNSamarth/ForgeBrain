/**
 * Service contracts for every stage in docs/PIPELINE.md, plus {@link
 * com.forgebrain.backend.services.MemoryService} and {@link
 * com.forgebrain.backend.services.AnalyticsService}, which support the pipeline without being
 * linear stages in it themselves.
 *
 * <p>Every type here is an interface only — no implementing class exists in this phase (see
 * TODO.md). Method parameters are explicit and typed against {@link
 * com.forgebrain.backend.models}, deliberately not a single shared "context" blob, so each
 * contract is self-documenting: reading a method signature should tell you exactly what that
 * stage needs, matching the Inputs table in its corresponding {@code *-spec.md}.
 */
package com.forgebrain.backend.services;
