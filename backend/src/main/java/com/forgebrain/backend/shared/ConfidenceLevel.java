package com.forgebrain.backend.shared;

/**
 * Coarse self-assessment of how well-grounded a stage's output is. Mirrors the
 * {@code overall_confidence} enum used identically across every {@code confidence_notes}
 * object in brain/*-schema.json and renderer/*-schema.json.
 */
public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}
