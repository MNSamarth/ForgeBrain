package com.forgebrain.backend.rendering;

import java.time.Instant;
import java.util.List;

/**
 * Every concrete asset a {@link RenderPlan} needs, deduplicated and categorized by {@link
 * AssetCollector}. One entry per distinct {@code (category, ref)} pair, regardless of how many
 * scenes reference it — {@code usedBySceneIds} carries the traceability instead, so the same
 * font or background track isn't counted (or eventually fetched) more than once.
 *
 * <p>Deliberately a different type from {@link com.forgebrain.backend.models.AssetManifest}:
 * that record is Asset Management's pipeline-stage <em>output</em>, resolving a storyboard's
 * abstract style names against a real catalog (renderer/asset-management-schema.json — not
 * populated yet, see TODO.md 5.2). This one is the renderer's own bookkeeping of every asset
 * reference actually embedded in a {@link RenderPlan}, whether or not a real catalog exists to
 * resolve them against yet. A future {@code AssetService} implementation is a plausible way to
 * turn each {@link AssetReference#ref()} here into a real URI, but that resolution is explicitly
 * out of scope for this rendering foundation.
 */
public record RenderAssetManifest(
        String topicId,
        List<AssetReference> assets,
        int totalAssetCount,
        Instant collectedAt,
        String basedOnRenderPlanVersion
) {

    /**
     * @param assetId          deterministic dedup key: {@code category + ":" + ref}
     * @param category         what kind of asset this is
     * @param ref               the asset's abstract reference/name — not yet a resolved file URI
     * @param usedBySceneIds   every scene that references this asset; empty for reel-global
     *                         assets (e.g. background music, watermark) that aren't scene-scoped
     */
    public record AssetReference(String assetId, AssetCategory category, String ref, List<String> usedBySceneIds) {
    }
}
