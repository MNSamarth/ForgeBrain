package com.forgebrain.backend.models;

import java.time.Instant;

/**
 * A tracked unit of rendering work — separate from its result, {@link VideoPackage}, which
 * only exists once a job's {@code status} reaches {@link Status#COMPLETED} (see
 * renderer/render-spec.md Section 1). Mirrors the {@code renderJob} definition in
 * {@code renderer/render-schema.json}.
 *
 * @see <a href="../../../../../../../../renderer/render-schema.json">renderer/render-schema.json</a>
 */
public record RenderJob(
        String jobId,
        String topicId,
        Status status,
        int progressPercent,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        String failedAtSceneId,
        String basedOnStoryboardVersion,
        String basedOnVoiceVersion,
        String basedOnSubtitleVersion,
        String basedOnAssetManifestVersion
) {

    public enum Status {
        QUEUED, RENDERING, COMPLETED, FAILED
    }
}
