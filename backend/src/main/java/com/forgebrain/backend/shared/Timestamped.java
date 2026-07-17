package com.forgebrain.backend.shared;

import java.time.Instant;

/**
 * Marker contract for any type that records when it was produced. Every pipeline stage's
 * output carries a {@code generated_at} field in its schema; implementing this interface
 * (where a model chooses to) makes that convention checkable at compile time rather than
 * by naming convention alone.
 */
public interface Timestamped {

    Instant generatedAt();
}
