/**
 * Common types shared across every pipeline layer: confidence/uncertainty reporting,
 * timestamp conventions, and identifier generation contracts.
 *
 * Types here exist specifically to avoid re-declaring the same shape (e.g. confidence_notes)
 * in every model class — see {@link com.forgebrain.backend.shared.ConfidenceNotes}, which is
 * reused by nearly every model in {@link com.forgebrain.backend.models}.
 */
package com.forgebrain.backend.shared;
