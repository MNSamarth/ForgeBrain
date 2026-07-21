package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vertex AI configuration. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.vertex-ai.*} in application.yml. No credentials — authentication is
 * assumed to be handled by Application Default Credentials in the real Cloud Run environment,
 * never a key embedded here.
 *
 * @param projectId                        GCP project ID (empty placeholder in every committed profile)
 * @param location                         Vertex AI region, e.g. "us-central1"
 * @param researchModel                    model identifier used by {@link com.forgebrain.backend.services.ResearchService}
 * @param lessonModel                      model identifier used by {@link com.forgebrain.backend.services.LessonService}
 * @param scriptModel                      model identifier used by {@link com.forgebrain.backend.services.ScriptService}
 * @param contentDirectorModel             model identifier used by {@link com.forgebrain.backend.services.ContentDirectorService}
 * @param lessonTemperature                sampling temperature for lesson generation, or {@code null} to
 *                                          use the SDK default
 * @param lessonMaxOutputTokens            output token cap for lesson generation, or {@code null} to use
 *                                          the SDK default
 * @param lessonResponseMimeType           response MIME type requested for lesson generation, e.g.
 *                                          {@code "application/json"}
 * @param contentDirectorTemperature       sampling temperature for content director generation, or
 *                                          {@code null} to use the SDK default
 * @param contentDirectorMaxOutputTokens   output token cap for content director generation, or
 *                                          {@code null} to use the SDK default
 * @param contentDirectorResponseMimeType  response MIME type requested for content director
 *                                          generation, e.g. {@code "application/json"}
 * @param scriptTemperature                sampling temperature for script generation, or
 *                                          {@code null} to use the SDK default
 * @param scriptMaxOutputTokens            output token cap for script generation, or {@code null}
 *                                          to use the SDK default
 * @param scriptResponseMimeType           response MIME type requested for script generation,
 *                                          e.g. {@code "application/json"}
 * @param visualDirectorModel              model identifier used by {@link com.forgebrain.backend.services.VisualDirectorService}
 * @param visualDirectorTemperature        sampling temperature for visual direction generation,
 *                                          or {@code null} to use the SDK default
 * @param visualDirectorMaxOutputTokens    output token cap for visual direction generation, or
 *                                          {@code null} to use the SDK default
 * @param visualDirectorResponseMimeType   response MIME type requested for visual direction
 *                                          generation, e.g. {@code "application/json"}
 */
@ConfigurationProperties(prefix = "forgebrain.vertex-ai")
public record VertexAiConfig(
        String projectId,
        String location,
        String researchModel,
        String lessonModel,
        String scriptModel,
        String contentDirectorModel,
        Double lessonTemperature,
        Integer lessonMaxOutputTokens,
        String lessonResponseMimeType,
        Double contentDirectorTemperature,
        Integer contentDirectorMaxOutputTokens,
        String contentDirectorResponseMimeType,
        Double scriptTemperature,
        Integer scriptMaxOutputTokens,
        String scriptResponseMimeType,
        String visualDirectorModel,
        Double visualDirectorTemperature,
        Integer visualDirectorMaxOutputTokens,
        String visualDirectorResponseMimeType
) {
}
