package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * Asset Management's output: concrete, renderer-ready asset references resolved from a
 * storyboard's abstract style names. Mirrors {@code renderer/asset-management-schema.json}.
 *
 * @see <a href="../../../../../../../../renderer/asset-management-schema.json">renderer/asset-management-schema.json</a>
 */
public record AssetManifest(
        String topicId,
        String topicTitle,
        Storyboard.RenderStyle renderStyle,
        ResolvedTheme resolvedTheme,
        BackgroundMusic backgroundMusic,
        Watermark watermark,
        List<SceneAsset> sceneAssets,
        ConfidenceNotes confidenceNotes,
        String assetManifestVersion,
        Instant generatedAt,
        String basedOnStoryboardVersion
) {

    public record ResolvedTheme(
            String fontHeading,
            String fontBody,
            String fontCode,
            ColorPalette colorPalette,
            String codeSyntaxTheme
    ) {
    }

    public record ColorPalette(String background, String textPrimary, String textAccent, String error, String success) {
    }

    public record BackgroundMusic(String trackUri, String license, double volumeDb) {
    }

    public record Watermark(String assetUri, Position position) {
        public enum Position {
            TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
        }
    }

    public record SceneAsset(String sceneId, List<String> assetRefs) {
    }
}
