package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * An approved reel bundled with everything a future publishing/upload stage would need. Nothing
 * in this type — or the service that produces it — actually posts anywhere; {@code
 * scheduling.status} can only ever reach {@link Scheduling.Status#READY} in this phase (see
 * publishing/publishing-spec.md Section 1). Mirrors {@code publishing/publishing-schema.json},
 * extended with {@code jobId} and {@code reviewVerdict} — fields the job orchestration layer
 * needs that predate this repository having a job system at all (the same extension this
 * mission's Reviewer stage made to {@code ReviewResult}).
 *
 * @param jobId        the {@link com.forgebrain.backend.job.ReelJob} this package was built for,
 *                      or {@code null} when built outside the job layer
 * @param reviewVerdict {@code ReviewResult.Verdict#name()} this package was built from — always
 *                       {@code "APPROVED"}, since {@link com.forgebrain.backend.services
 *                       .PublishingService} refuses to build a package from anything else (see
 *                       publishing-spec.md Section 4); carried here so the package is
 *                       self-contained proof of that precondition without a second lookup
 * @see <a href="../../../../../../../../publishing/publishing-schema.json">publishing/publishing-schema.json</a>
 */
public record PublishingPackage(
        String packageId,
        String jobId,
        String topicId,
        String topicTitle,
        String basedOnReviewId,
        String reviewVerdict,
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
