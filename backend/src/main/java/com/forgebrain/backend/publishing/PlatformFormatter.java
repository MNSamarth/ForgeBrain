package com.forgebrain.backend.publishing;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.Script;

/**
 * Contract for adapting a default {@link PublishingMetadata} into platform-specific phrasing
 * conventions (e.g. YouTube Shorts titles tending more descriptive, TikTok captions leaning
 * more casual) — see publishing/publishing-spec.md Section 6. One implementation is expected
 * per {@link Script.Platform} value once this is built (see TODO.md).
 */
public interface PlatformFormatter {

    Script.Platform supportedPlatform();

    PublishingMetadata format(Lesson lesson, Script script, PublishingMetadata defaultMetadata);
}
