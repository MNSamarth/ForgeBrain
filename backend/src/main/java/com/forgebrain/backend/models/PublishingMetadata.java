package com.forgebrain.backend.models;

import java.util.List;

/**
 * Title, description, tags, and category for one publishing destination. Declared as a
 * standalone top-level type because {@link PublishingPackage} uses it twice: once as the
 * default {@code metadata}, and once per entry in {@code platformVariants} for platform-specific
 * phrasing (see publishing/publishing-spec.md Section 5).
 */
public record PublishingMetadata(
        String title,
        String description,
        List<String> tags,
        List<String> hashtags,
        String category,
        String languageCode
) {
}
