package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.VideoPackage;
import java.util.List;

/**
 * Contract for whatever actual video composition technology executes a {@link
 * com.forgebrain.backend.models.RenderJob}'s resolved scene instructions. Deliberately named
 * and scoped separately from {@link com.forgebrain.backend.services.RendererService}: that
 * service manages the job lifecycle (submit, track status); this interface is the seam where
 * an actual rendering technology would plug in, matching renderer/render-spec.md Section 7's
 * "render engine swapping" extensibility note.
 *
 * <p><b>No implementation is provided.</b> Per this project's rules, no rendering/encoding
 * logic is written in this phase — this interface exists only to name the seam for later.
 */
public interface RenderEngine {

    VideoPackage render(List<SceneRenderInstruction> instructions);
}
