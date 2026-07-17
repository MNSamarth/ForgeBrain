package com.forgebrain.backend.models;

import java.util.Map;

/**
 * A single, general-purpose named asset reference (a font, a music track, an icon, a theme
 * definition). Distinct from {@link AssetManifest}, which is the *resolved bundle* of assets
 * a specific storyboard needs — {@code Asset} is one entry such a bundle (or the future
 * {@code assets/} catalog) points to.
 */
public record Asset(
        String assetId,
        AssetType type,
        String uri,
        String license,
        Map<String, String> metadata
) {

    public enum AssetType {
        FONT, MUSIC, IMAGE, ICON, THEME, WATERMARK
    }
}
