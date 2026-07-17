package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Values the running application needs to know about its own Cloud Run hosting environment.
 * See docs/CONFIGURATION.md Section 3 — this is explicitly not a deployment manifest; no
 * Docker or Kubernetes configuration is included anywhere in this project. Bound from
 * {@code forgebrain.cloud-run.*} in application.yml.
 *
 * @param serviceName   the Cloud Run service name this instance identifies as
 * @param region        the Cloud Run region
 * @param maxConcurrency application-level hint for expected concurrent request handling,
 *                       informational only in this phase
 */
@ConfigurationProperties(prefix = "forgebrain.cloud-run")
public record CloudRunConfig(
        String serviceName,
        String region,
        int maxConcurrency
) {
}
