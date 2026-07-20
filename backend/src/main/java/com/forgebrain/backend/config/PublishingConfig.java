package com.forgebrain.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Publishing metadata generation defaults. Bound from {@code forgebrain.publishing.*} in
 * application.yml. See {@code com.forgebrain.backend.publishing.PublishingMetadataGenerator}.
 *
 * @param publishingVersion   stamped onto every {@code PublishingPackage}
 * @param defaultHashtags     hashtags included on every package before the topic-specific one
 *                            {@code PublishingMetadataGenerator} adds
 * @param defaultCategory     platform content category, e.g. {@code "Education"}
 * @param defaultLanguageCode e.g. {@code "en-US"}
 * @param titleMaxLength      titles longer than this are truncated
 * @param descriptionMaxLength descriptions longer than this are truncated
 */
@ConfigurationProperties(prefix = "forgebrain.publishing")
public record PublishingConfig(
        String publishingVersion,
        List<String> defaultHashtags,
        String defaultCategory,
        String defaultLanguageCode,
        int titleMaxLength,
        int descriptionMaxLength
) {
}
