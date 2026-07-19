package com.forgebrain.backend.rendering;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Walks a {@link RenderPlan} end to end and gathers every asset it references into one
 * deduplicated {@link RenderAssetManifest}. The same asset can be named in several places — a
 * font in {@link RenderPlan#fonts()} and again in a scene's own {@code assetRefs}, a music track
 * only in {@link RenderPlan.AudioPlan} — this collector is what turns those scattered references
 * into a single authoritative list, merging {@code usedBySceneIds} rather than duplicating
 * entries.
 *
 * <p>{@code heading}/{@code body} fonts and background music are treated as reel-global (empty
 * {@code usedBySceneIds}): every scene's subtitle cues render in the body font and the
 * background track plays under the whole reel, so attributing them to specific scenes would be
 * arbitrary. The code font and generated code screenshots, by contrast, are genuinely
 * scene-scoped — only scenes with a {@link SceneRenderPlan#codeLayer()} need them — so their
 * attribution comes directly from each scene's own {@code assetRefs}.
 */
@Component
public class AssetCollector {

    public RenderAssetManifest collect(RenderPlan renderPlan) {
        Map<String, Accumulator> byAssetId = new LinkedHashMap<>();

        add(byAssetId, AssetCategory.FONT, renderPlan.fonts().heading(), null);
        add(byAssetId, AssetCategory.FONT, renderPlan.fonts().body(), null);

        if (renderPlan.audio().backgroundMusicRef() != null && !renderPlan.audio().backgroundMusicRef().isBlank()) {
            add(byAssetId, AssetCategory.MUSIC, renderPlan.audio().backgroundMusicRef(), null);
        }

        for (RenderPlan.GlobalAssetRef globalRef : renderPlan.globalAssetRefs()) {
            add(byAssetId, globalRef.category(), globalRef.ref(), null);
        }

        for (SceneRenderPlan scene : renderPlan.scenes()) {
            for (RenderPlan.GlobalAssetRef sceneRef : scene.assetRefs()) {
                add(byAssetId, sceneRef.category(), sceneRef.ref(), scene.sceneId());
            }
        }

        List<RenderAssetManifest.AssetReference> assets = byAssetId.values().stream()
                .map(Accumulator::toAssetReference)
                .toList();

        return new RenderAssetManifest(
                renderPlan.topicId(),
                assets,
                assets.size(),
                Instant.now(),
                renderPlan.renderPlanVersion()
        );
    }

    private void add(Map<String, Accumulator> byAssetId, AssetCategory category, String ref, String sceneId) {
        String assetId = category.name() + ":" + ref;
        Accumulator accumulator = byAssetId.computeIfAbsent(assetId, id -> new Accumulator(assetId, category, ref));
        if (sceneId != null) {
            accumulator.usedBySceneIds.add(sceneId);
        }
    }

    private static final class Accumulator {
        private final String assetId;
        private final AssetCategory category;
        private final String ref;
        private final Set<String> usedBySceneIds = new LinkedHashSet<>();

        private Accumulator(String assetId, AssetCategory category, String ref) {
            this.assetId = assetId;
            this.category = category;
            this.ref = ref;
        }

        private RenderAssetManifest.AssetReference toAssetReference() {
            return new RenderAssetManifest.AssetReference(assetId, category, ref, List.copyOf(usedBySceneIds));
        }
    }
}
