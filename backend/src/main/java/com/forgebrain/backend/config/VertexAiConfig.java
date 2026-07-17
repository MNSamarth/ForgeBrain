package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vertex AI configuration. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.vertex-ai.*} in application.yml. No credentials — authentication is
 * assumed to be handled by Application Default Credentials in the real Cloud Run environment,
 * never a key embedded here.
 *
 * @param projectId     GCP project ID (empty placeholder in every committed profile)
 * @param location      Vertex AI region, e.g. "us-central1"
 * @param researchModel model identifier used by {@link com.forgebrain.backend.services.ResearchService}
 * @param lessonModel   model identifier used by {@link com.forgebrain.backend.services.LessonService}
 * @param scriptModel   model identifier used by {@link com.forgebrain.backend.services.ScriptService}
 */
@ConfigurationProperties(prefix = "forgebrain.vertex-ai")
public record VertexAiConfig(
        String projectId,
        String location,
        String researchModel,
        String lessonModel,
        String scriptModel
) {
}
