package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Structure-only placeholder for a future Secret Manager-backed secret lookup — mirrors {@link
 * FirestoreConfig}'s "reserved ahead of need" pattern exactly. No secret is read through this
 * config today: ADC remains the only real authentication path this project uses (see
 * docs/CONFIGURATION.md Section 5), and nothing in the pipeline currently needs a runtime-fetched
 * secret value — the real YouTube/Instagram credentials in {@link PlatformUploadConfig}, for
 * example, are read as plain (blank-by-default, environment-injected) config strings, not
 * fetched from Secret Manager.
 *
 * <p>When a real secret consumer exists, it depends on this config (for {@code projectId}) plus a
 * thin {@code com.google.cloud.secretmanager.v1.SecretManagerServiceClient}-backed lookup — no
 * such client is wired here yet, since building one with no caller would itself be exactly the
 * kind of unnecessary abstraction this mission's constraints rule out.
 *
 * @param enabled   whether secret lookups should be attempted at all
 * @param projectId GCP project ID secrets are looked up in — blank in every committed profile
 */
@ConfigurationProperties(prefix = "forgebrain.secret-manager")
public record SecretManagerConfig(
        boolean enabled,
        String projectId
) {
}
