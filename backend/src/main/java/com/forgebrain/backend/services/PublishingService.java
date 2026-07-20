package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import java.nio.file.Path;

/**
 * Contract for turning an approved reel into a {@link com.forgebrain.backend.models
 * .PublishingPackage} and handing it to every registered platform adapter. See
 * publishing/publishing-spec.md. Implementations must enforce the precondition in Section 4 of
 * that spec: refuse to run against any {@link ReviewResult} whose verdict is not {@code
 * APPROVED}. This interface does not publish or post anywhere for real — see {@link
 * com.forgebrain.backend.publishing.PlatformPublishAdapter}.
 */
public interface PublishingService {

    /**
     * @param outputDirectory  where {@code publishing-package.json} and every platform adapter's
     *                         payload file are written, e.g. a job's render directory
     * @param subtitleFileUri  the actual on-disk subtitle file location — not {@code
     *                         SubtitleResult.subtitleFileUri()}, which is always {@code null}
     *                         (see {@link ReviewerService#review}'s equivalent parameter)
     * @throws com.forgebrain.backend.exceptions.PipelineStageException if {@code
     * reviewResult.verdict()} is not {@code APPROVED}
     */
    PublishingResult publish(String jobId, Path outputDirectory, ReviewResult reviewResult,
            VideoPackage videoPackage, String subtitleFileUri, Lesson lesson, Script script);
}
