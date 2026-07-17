# Asset Management Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Asset Management stage: the layer that resolves the abstract style choices made upstream (`render_style`, `visual_style`, `code_style`, transitions) into concrete, reusable asset references — fonts, color themes, background music, code syntax themes, and brand watermark. It sits between [Subtitle Generation](subtitle-spec.md) and the [Renderer](render-spec.md).

## 1. Why Asset Resolution Is Its Own Stage

Every earlier stage in the pipeline picks a style by *name* — `content_director_output.visual_style: "split-screen"`, `storyboard.render_style: "dark-mode-ide"` — deliberately as an abstract label, not a concrete file path. Nothing upstream should need to know where a font file lives or which exact hex code a theme's background uses; that would tie content decisions to a specific asset library and make both harder to change independently. Asset Management is the one place that translation happens: style names in, concrete, renderer-ready references out.

This also enforces brand consistency structurally rather than by convention — every reel that resolves `render_style: "dark-mode-ide"` gets the *same* font/color/theme bundle, because they all resolve through the same catalog, not because each stage was independently careful to match the last reel.

## 2. Position in the Pipeline

```
Subtitle Generation → Asset Management → Renderer → Reviewer → Publishing Package
```

Asset Management consumes a storyboard (for its style fields and any per-scene visual cues that imply a specific asset) and produces exactly one `AssetManifest`.

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `storyboard` | Full storyboard object | Supplies `render_style`, `visual_style`, `code_style`, `transition_style`, and per-scene `visual_description`/`on_screen_text` that may imply a specific supporting asset (e.g. an icon named in a scene's description). |
| `asset_catalog` | The contents of `assets/` (see Section 4) | The actual pool of fonts, themes, tracks, and graphics this stage resolves against. Empty in Phase 1 — see `TODO.md`. |
| `brand_voice` | Same Phase 1 gap as `script-spec.md` Section 5 | Would inform watermark/logo choice once a brand identity is defined. |

## 4. Relationship to `assets/`

The repository's [`assets/`](../assets/) folder is the actual **asset library** — the real font files, background tracks, icon sets, and code themes this stage resolves against. Asset Management is the *logic* that looks things up in that library; `assets/` is the *library itself*. Phase 1 ships this stage's architecture with `assets/` still empty (per the original scaffold), which is an explicit, tracked gap — see `TODO.md` — not an oversight.

A resolved `AssetManifest` should be read as "given a populated `assets/` library organized as this stage expects, here is what a specific reel would use," not as proof any of these files currently exist.

## 5. Output: AssetManifest

Full field-level contract: [asset-management-schema.json](asset-management-schema.json). `resolved_theme` bundles everything a `render_style` implies (fonts, color palette, code syntax theme) as one resolved unit, so the Renderer never has to reason about style names itself — only about concrete asset references. `scene_assets` is deliberately small and optional per scene: most visual variety comes from motion and text (already fully specified in the storyboard), not from a large per-scene asset library; only scenes whose `visual_description` explicitly names a supporting graphic (an icon, a diagram element) get an entry.

## 6. Future Extensibility

- **Multiple visual themes per `render_style`** — nothing here assumes one-to-one resolution; a future version could resolve to one of several theme variants (e.g. seasonal accents) using the same schema.
- **Licensed asset tracking** — `background_music.license` is already a required field, reserved for exactly this once a real music library with licensing terms exists.
- **Per-brand asset catalogs** — once multiple `brand_voice` profiles exist (Section 3), `asset_catalog` resolution could be scoped per brand without changing this schema's shape.

## 7. Edge Cases

- **A `render_style` has no matching entry in the asset catalog.** This is a hard stop, not a silent fallback to a default theme — `confidence_notes` should say so explicitly, since silently substituting a theme would produce a reel that doesn't match what the Content Director and Storyboard stages actually specified.
- **A scene's `visual_description` implies an asset that doesn't exist in the catalog** (e.g. a specific icon). The manifest should still resolve everything else and flag the missing asset in `confidence_notes.flagged_uncertainties`, rather than failing the entire reel over one missing icon — the Renderer can substitute a generic placeholder or the Reviewer can catch the gap.
