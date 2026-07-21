/**
 * Live Google Cloud connectivity: {@link com.forgebrain.backend.gcp.CloudConnectivityChecker}
 * proves the configured Vertex AI/GCS settings are actually reachable, one guarded, minimal call
 * per target. Does not replace or wrap {@code VertexAiClient}/{@code OutputStorage} — it calls
 * them directly, exactly as any other caller would. See backend/README.md's "Live GCP
 * Configuration" section.
 */
package com.forgebrain.backend.gcp;
