package com.forgebrain.backend.services;

import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;

/**
 * Contract for bundling an approved reel into a {@link PublishingPackage}. See
 * publishing/publishing-spec.md. Implementations must enforce the precondition in Section 4
 * of that spec: refuse to run against any {@link ReviewResult} whose verdict is not {@code
 * APPROVED}. This interface does not publish or post anywhere — see publishing-spec.md
 * Section 1.
 */
public interface PublishingService {

    PublishingPackage preparePublishingPackage(
            ReviewResult reviewResult,
            VideoPackage videoPackage,
            SubtitleResult subtitleResult
    );
}
