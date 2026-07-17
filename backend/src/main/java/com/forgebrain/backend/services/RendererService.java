package com.forgebrain.backend.services;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.RenderJob;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;

/**
 * Contract for assembling a storyboard, its narration audio, its reconciled subtitles, and
 * its resolved assets into a finished video. See renderer/render-spec.md. Modeled as an
 * asynchronous job (see Section 1 of that spec) — {@link #submitRenderJob} returns
 * immediately with a tracked {@link RenderJob}; {@link #getVideoPackage} only returns a
 * non-null result once that job's status reaches {@code COMPLETED}.
 *
 * No rendering/encoding logic is implemented by this interface or expected of it — see the
 * project rules in the repository root REPORT.md.
 */
public interface RendererService {

    RenderJob submitRenderJob(
            Storyboard storyboard,
            VoiceResult voiceResult,
            SubtitleResult subtitleResult,
            AssetManifest assetManifest
    );

    RenderJob getRenderJobStatus(String jobId);

    /**
     * @return the finished package, or {@code null} if the job has not yet completed
     *         successfully
     */
    VideoPackage getVideoPackage(String jobId);
}
