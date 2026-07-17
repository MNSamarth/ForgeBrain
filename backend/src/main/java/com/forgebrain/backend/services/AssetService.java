package com.forgebrain.backend.services;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.Storyboard;

/**
 * Contract for resolving a storyboard's abstract style names (render_style, visual_style,
 * code_style) into concrete, renderer-ready asset references. See
 * renderer/asset-management-spec.md. Resolves against the asset catalog described in
 * renderer/asset-management-spec.md Section 4 — a real catalog implementation does not exist
 * yet (see TODO.md).
 */
public interface AssetService {

    AssetManifest resolveAssets(Storyboard storyboard);
}
