package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.VideoPackage;
import java.util.List;

/**
 * Contract for whatever actual video composition technology executes a {@link
 * com.forgebrain.backend.models.RenderJob}. Deliberately named and scoped separately from
 * {@link com.forgebrain.backend.services.RendererService}: that service manages the job
 * lifecycle (submit, track status); this interface is the seam where an actual rendering
 * technology plugs in, matching renderer/render-spec.md Section 7's "render engine swapping"
 * extensibility note.
 *
 * <p>{@link #render(RenderPlan)} is the real entry point, implemented by {@link
 * com.forgebrain.backend.rendering.ffmpeg.FfmpegRenderEngine} — a validated {@link RenderPlan}
 * is exactly what the rendering foundation (see {@code RenderPlanBuilder}, {@code
 * AssetCollector}, {@code RenderValidator}) already produces, so it's the natural input here
 * rather than a separate resolved-instruction shape.
 */
public interface RenderEngine {

    VideoPackage render(RenderPlan renderPlan);

    /**
     * @deprecated this was the original named seam, predating the {@link RenderPlan}-based
     * rendering foundation. No implementation renders through {@link SceneRenderInstruction} —
     * use {@link #render(RenderPlan)}. Kept as a default so existing implementers aren't broken;
     * throws by default.
     */
    @Deprecated
    default VideoPackage render(List<SceneRenderInstruction> instructions) {
        throw new UnsupportedOperationException(
                "SceneRenderInstruction-based rendering is not implemented; use render(RenderPlan) instead.");
    }
}
