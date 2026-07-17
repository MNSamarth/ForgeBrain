package com.forgebrain.backend.shared;

/**
 * Contract for generating identifiers for pipeline artifacts (job IDs, package IDs, review
 * IDs, and so on). Kept as an interface, not a static utility, so the generation strategy
 * (UUID, sequential, content-hash-based) can be swapped per environment without touching
 * any service that depends on it.
 *
 * No implementation is provided in this phase — see TODO.md.
 */
public interface IdGenerator {

    String generate(String prefix);
}
