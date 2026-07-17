/**
 * Domain models: one Java type per pipeline artifact defined in {@code brain/}, {@code
 * renderer/}, {@code reviewer/}, and {@code publishing/}'s JSON schemas.
 *
 * <p>These are transient, in-memory pipeline objects — the data passed between {@link
 * com.forgebrain.backend.services} implementations — not persistence-mapped types (see {@link
 * com.forgebrain.backend.entities} for the small subset of state that's actually stored).
 *
 * <p><b>Fidelity note:</b> each record here mirrors its corresponding schema's required
 * top-level fields and primary nested structures. Exhaustive field-level documentation
 * (descriptions, enums, constraints) lives in the authoritative {@code *-schema.json} file
 * referenced in each type's Javadoc — these records are a structural mirror for the backend
 * to compile against, not a second source of truth.
 */
package com.forgebrain.backend.models;
