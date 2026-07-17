# Renderer — The Production Layer

This folder is ForgeBrain's production layer: the sequence of stages that turn a finished [storyboard](../brain/storyboard-spec.md) into an actual finished video file. Where [`brain/`](../brain/) decides *what* a reel says and *what it should look like*, `renderer/` is where those decisions become real media — narration audio, synced captions, resolved brand assets, and a final MP4.

Nothing in this folder decides content. Every stage here consumes a structured artifact from the previous stage and produces a more concrete one; none of them can change what the storyboard already committed to.

## The pipeline, in order

```
Storyboard → Voice Generation → Subtitle Generation → Asset Management → Renderer
```

| Stage | Spec | Schema | What it produces |
| --- | --- | --- | --- |
| Voice Generation | [voice-spec.md](voice-spec.md) | [voice-schema.json](voice-schema.json) | Narrated audio per scene, plus the *real* measured timing that supersedes every estimate made upstream. |
| Subtitle Generation | [subtitle-spec.md](subtitle-spec.md) | [subtitle-schema.json](subtitle-schema.json) | Final captions, reconciled against real audio timing, not the original word-count estimate. |
| Asset Management | [asset-management-spec.md](asset-management-spec.md) | [asset-management-schema.json](asset-management-schema.json) | Concrete fonts, color themes, music, and brand assets resolved from the storyboard's style names. |
| Renderer | [render-spec.md](render-spec.md) | [render-schema.json](render-schema.json) | The tracked render job, and — on success — the finished `VideoPackage` (MP4 + thumbnail + technical metadata). |

Worked examples for all four stages, threaded through one topic end to end, live in [examples.md](examples.md).

## The central theme: estimate vs. reality

Every duration anywhere upstream of this folder — in the script, in the storyboard — was computed from a word-count formula, not real speech (`brain/script-spec.md` Section 8). Voice Generation is where that estimate finally meets a real, measured voice, and `voice-spec.md` Section 4 makes an explicit architectural call: from that point forward, **real measured audio timing is the authority**, not the original estimate. Subtitle Generation exists specifically to carry that correction into captions; the Renderer inherits the same authoritative timing for the final cut. This is the throughline connecting all four stages in this folder.

## Relationship to `assets/`

This folder's `asset-management-spec.md` is the **logic** that resolves a storyboard's abstract style names (`render_style: "dark-mode-ide"`) into concrete files. The repository's top-level [`assets/`](../assets/) folder is the **library** those resolutions point into — the actual font files, music tracks, and graphics. `assets/` remains an empty placeholder in Phase 1; see `TODO.md`.

## Relationship to `reviewer/`

The Renderer's output (`VideoPackage`) is the primary input to [`reviewer/`](../reviewer/), which performs the pipeline's final quality gate before a `PublishingPackage` can be prepared. Rendering does not self-certify its own output — that's a deliberately separate stage.
