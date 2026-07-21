package com.forgebrain.backend.services;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VisualPlan;

/**
 * Contract for resolving a storyboard's abstract style names (render_style, visual_style,
 * code_style) into concrete, renderer-ready asset references. See
 * renderer/asset-management-spec.md. Resolves against the asset catalog described in
 * renderer/asset-management-spec.md Section 4 — a real catalog implementation does not exist
 * yet (see TODO.md).
 */
public interface AssetService {

    AssetManifest resolveAssets(Storyboard storyboard);

    /**
     * Like {@link #resolveAssets(Storyboard)}, additionally carrying each scene's Visual
     * Director-authored illustration/diagram prompt brief (see {@link
     * AssetManifest.SceneAsset#visualPromptBrief()}) into the resolved manifest. Default
     * implementation ignores {@code visualPlan} and delegates to {@link #resolveAssets(Storyboard)}
     * — implementations that don't have a real visual prompt story yet aren't forced to have one.
     */
    default AssetManifest resolveAssets(Storyboard storyboard, VisualPlan visualPlan) {
        return resolveAssets(storyboard);
    }
}
