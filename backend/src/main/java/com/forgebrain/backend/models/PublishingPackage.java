package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * The final Phase 1 pipeline artifact: an approved reel bundled with everything a future
 * publishing/upload stage would need. Nothing in this type — or the pipeline that produces
 * it — actually posts anywhere; {@code scheduling.status} can only ever reach {@link
 * Scheduling.Status#READY} in this phase (see publishing/publishing-spec.md Section 1).
 * Mirrors {@code publishing/publishing-schema.json}.
 *
 * @see <a href="../../../../../../../../publishing/publishing-schema.json">publishing/publishing-schema.json</a>
 */
public record PublishingPackage(
        String packageId,
        String topicId,
        String topicTitle,
        String basedOnReviewId,
        String basedOnVideoPackageId,
        String videoFileUri,
        String thumbnailUri,
        String captionsFileUri,
        PublishingMetadata metadata,
        List<PlatformVariant> platformVariants,
        Scheduling scheduling,
        ConfidenceNotes confidenceNotes,
        String publishingVersion,
        Instant generatedAt
) {

    public record PlatformVariant(Script.Platform platform, PublishingMetadata metadata) {
    }

    public record Scheduling(Status status, Instant scheduledFor) {
        public enum Status {
            DRAFT, READY, SCHEDULED
        }
    }
}
