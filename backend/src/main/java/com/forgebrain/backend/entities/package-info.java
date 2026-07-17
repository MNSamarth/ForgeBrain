/**
 * Firestore-persisted entities — the small subset of pipeline state that actually needs to
 * survive between application runs, as opposed to the transient pipeline artifacts in {@link
 * com.forgebrain.backend.models}.
 *
 * <p>Deliberately plain, mutable POJOs (private fields, no-arg constructor, getters/setters)
 * rather than records: this is the conventional shape for Spring Data document mapping, and
 * it also signals structurally that these types are different in kind from the immutable
 * {@code models} — persisted and mutable, not transient and value-like. See
 * backend/README.md Section 4 for this project's full models-vs-entities-vs-dto rationale.
 *
 * <p>No Spring Data GCP/Firestore dependency is declared in pom.xml yet, and no real mapping
 * annotations are used here — these classes describe the intended shape only. See TODO.md.
 */
package com.forgebrain.backend.entities;
