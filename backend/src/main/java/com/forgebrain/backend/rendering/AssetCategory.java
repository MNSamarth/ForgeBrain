package com.forgebrain.backend.rendering;

/**
 * Categories of concrete file-backed assets a {@link RenderPlan} can reference. Distinct from
 * {@link com.forgebrain.backend.models.Asset.AssetType}: that enum covers the general-purpose
 * catalog vocabulary ({@code THEME} included); this one is scoped to exactly what {@link
 * AssetCollector} needs to categorize while walking a render plan, including render-specific
 * categories ({@code SOUND_EFFECT}, {@code GENERATED_IMAGE}, {@code GENERATED_CODE_SCREENSHOT})
 * that don't fit a general asset catalog entry because they're generated per-topic, not
 * pre-existing catalog items.
 */
public enum AssetCategory {
    FONT,
    ICON,
    LOGO,
    WATERMARK,
    MUSIC,
    SOUND_EFFECT,
    GENERATED_IMAGE,
    GENERATED_CODE_SCREENSHOT
}
