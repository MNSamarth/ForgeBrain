package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single "which live Google Cloud project are we pointed at, and is it turned on" config
 * object. Bound from {@code forgebrain.gcp.*}. See docs/CONFIGURATION.md Section 5 — {@code
 * projectId}/{@code storageBucket} are empty placeholders in every committed profile, exactly
 * like every other identifying field in this package; real values are injected via environment
 * variables (e.g. {@code FORGEBRAIN_GCP_PROJECT-ID}), never committed.
 *
 * <p>This does <b>not</b> replace {@link VertexAiConfig#projectId()}/{@link
 * CloudStorageConfig#projectId()}/{@link CloudStorageConfig#mediaBucket()} — those remain exactly
 * what {@code VertexAiClientImpl}/{@code CloudStorageOutputStorage} read to do real work, unchanged
 * by this mission. {@code GcpConfig} exists alongside them as the one place that answers "is cloud
 * mode on at all" explicitly (today that's only ever inferred reactively — Vertex AI has no
 * enablement flag of its own, just a blank-project-id check inside {@code VertexAiClientImpl} at
 * call time) — {@link com.forgebrain.backend.gcp.CloudConnectivityChecker} reads this before
 * attempting any real call, so a disabled check never touches the network.
 *
 * @param projectId       GCP project ID, e.g. {@code "forgebrain-prod"} in the real deployment —
 *                        blank in every committed profile
 * @param region          Vertex AI / Cloud Storage region, e.g. {@code "us-central1"} — safe to
 *                        commit a real default, unlike a project id or bucket name
 * @param storageBucket   GCS bucket for durable output artifacts, e.g. {@code
 *                        "forgebrain-artifacts"} in the real deployment — blank in every
 *                        committed profile
 * @param vertexAiEnabled whether the live Vertex AI path (and its smoke test) should be
 *                        considered turned on
 * @param gcsEnabled      whether the live Cloud Storage path (and its smoke test) should be
 *                        considered turned on — kept independent from {@code vertexAiEnabled}
 *                        since either can be enabled without the other
 */
@ConfigurationProperties(prefix = "forgebrain.gcp")
public record GcpConfig(
        String projectId,
        String region,
        String storageBucket,
        boolean vertexAiEnabled,
        boolean gcsEnabled
) {
}
