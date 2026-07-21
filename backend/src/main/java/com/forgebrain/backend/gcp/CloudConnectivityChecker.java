package com.forgebrain.backend.gcp;

/**
 * Proves the live GCP configuration is actually usable — one guarded, minimal real call per
 * target, never run automatically (see {@code CloudConnectivitySmokeTestRunner}, disabled by
 * default like every other {@code CommandLineRunner} in this project). Both methods return a
 * {@link CloudConnectivityResult} rather than throwing, and both short-circuit to {@link
 * CloudConnectivityResult.Status#SKIPPED} without touching the network when that target isn't
 * enabled in {@code GcpConfig} — a disabled check is free and safe to call from anywhere,
 * including a test with no real credentials.
 */
public interface CloudConnectivityChecker {

    /** One minimal Vertex AI generation call via the existing, unmodified {@code VertexAiClient}. */
    CloudConnectivityResult checkVertexAi();

    /** One minimal write via the existing, unmodified {@code OutputStorage} seam. */
    CloudConnectivityResult checkGcs();
}
