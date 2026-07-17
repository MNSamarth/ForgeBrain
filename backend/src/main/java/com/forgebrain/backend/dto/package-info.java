/**
 * API-boundary request/response wrappers, kept separate from {@link
 * com.forgebrain.backend.models} so the pipeline's internal data shapes can evolve
 * independently of whatever a future HTTP API commits to as a stable contract.
 *
 * <p>Illustrative in this phase: no {@link com.forgebrain.backend.controllers} class currently
 * exposes a real endpoint using these, since no service implementation exists yet to back one
 * (see TODO.md). They establish the intended request/response pattern.
 */
package com.forgebrain.backend.dto;
