package com.forgebrain.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * General application configuration: environment identity, pipeline versioning, and stage
 * feature flags. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.application.*} in application.yml.
 *
 * @param environment    "local", "staging", or "production" — governs which profile's
 *                       defaults are active, never branched on in pipeline code (see
 *                       docs/CONFIGURATION.md Section 4)
 * @param pipelineVersion the current schema/spec version set this deployment targets, for
 *                        correlating with the {@code *_version} fields on every pipeline
 *                        artifact (e.g. {@code Script.scriptVersion})
 * @param enabledStages  which {@link com.forgebrain.backend.shared.PipelineStage} names are
 *                       currently allowed to run — an empty list in every committed profile,
 *                       since no stage has a real implementation yet
 */
@ConfigurationProperties(prefix = "forgebrain.application")
public record ApplicationConfig(
        String environment,
        String pipelineVersion,
        List<String> enabledStages
) {
}
