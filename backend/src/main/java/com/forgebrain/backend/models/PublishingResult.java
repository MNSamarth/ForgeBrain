package com.forgebrain.backend.models;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of one {@link com.forgebrain.backend.services.PublishingService#publish} call:
 * the {@link PublishingPackage} that was built, where it was written, and what each platform
 * adapter did with it. Only ever produced for an {@code APPROVED} review — see {@link
 * PublishingPackage#reviewVerdict()}.
 *
 * @param packageFileReference where {@code publishing-package.json} was written — a local path
 *                             before {@link com.forgebrain.backend.job.OutputStorage} durably
 *                             stores it, matching every other job output file's convention
 * @param status               {@link Status#READY} (every platform outcome succeeded), {@link
 *                             Status#PARTIAL_FAILURE} (some succeeded), or {@link Status#FAILED}
 *                             (none did)
 */
public record PublishingResult(
        String publishingId,
        String jobId,
        String topicId,
        PublishingPackage publishingPackage,
        String packageFileReference,
        List<PlatformPublishOutcome> platformOutcomes,
        Status status,
        List<String> errors,
        Instant createdAt
) {

    public enum Status {
        READY, PARTIAL_FAILURE, FAILED
    }
}
