# Renderer Specification

Status: Design specification — no implementation yet. Per the project rules for this pass, this document defines the Renderer's **job contract and output artifact**, not rendering code, video-processing logic, or any actual encoding pipeline.

This document describes ForgeBrain's Renderer: the layer that assembles a storyboard, its narration audio, its reconciled subtitles, and its resolved asset manifest into one finished vertical video file. It sits between [Asset Management](asset-management-spec.md) and the [Reviewer](../reviewer/reviewer-spec.md).

## 1. Why Rendering Is Modeled as a Job, Not a Function Call

Every prior stage in this pipeline is fast and synchronous relative to rendering: producing structured JSON is seconds of work. Actually compositing video, audio, subtitles, and motion into an encoded MP4 is not — it's the slowest step in the pipeline by a wide margin, and the one most likely to run on separate compute (see `docs/CONFIGURATION.md` for the Cloud Run assumption). Modeling it as an asynchronous **`RenderJob`** rather than a request/response call is a direct consequence of that: something has to be submitted, tracked, and polled or notified, rather than awaited inline.

This also cleanly separates two concerns the task list explicitly names as distinct: `RenderJob` (the tracked unit of *work* — is it queued, running, done, failed?) and `VideoPackage` (the *result* once that work succeeds). A job can fail without a package ever existing; a package's existence implies its job succeeded. Conflating the two would make "is this reel done yet?" and "here is the finished reel" the same question, when they aren't.

## 2. Position in the Pipeline

```
Asset Management → Renderer → Reviewer → Publishing Package
```

The Renderer consumes a storyboard, a `VoiceResult`, a `SubtitleResult`, and an `AssetManifest` — everything decided or produced by every earlier stage — and produces one `RenderJob`, which upon success yields one `VideoPackage`.

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `storyboard` | Full storyboard object | Scene timing, visual instructions, motion notes, code blocks — the shot list. |
| `voice_result` | Full `VoiceResult` | The real narration audio and its authoritative timing (`voice-spec.md` Section 4). |
| `subtitle_result` | Full `SubtitleResult` | The reconciled caption track and file. |
| `asset_manifest` | Full `AssetManifest` | Every concrete font, theme, music track, and graphic the render needs. |

No stage input here is optional — the Renderer is the first point in the pipeline where all four production-layer artifacts must exist simultaneously, which is itself a useful gate: a `RenderJob` cannot legitimately be submitted with a missing upstream artifact, and that's checkable before any compute is spent.

## 4. Output: RenderJob and VideoPackage

Full field-level contracts: [render-schema.json](render-schema.json) defines both `RenderJob` and `VideoPackage`.

**`RenderJob`** tracks the work itself: `job_id`, `status` (`queued`, `rendering`, `completed`, `failed`), `progress_percent`, timestamps for each status transition, and — on failure — an `error_message` and `failed_at_scene_id` where available, so a failure is traceable to a specific scene's instructions rather than "rendering broke."

**`VideoPackage`** is produced only when a `RenderJob` reaches `completed`: the actual `video_file_uri`, technical metadata (`duration_seconds`, `resolution`, `codec`, `file_size_bytes`), a `thumbnail_frame_uri` (a single extracted frame, chosen per Section 5), a `checksum` for integrity verification, and `based_on_render_job_id` linking back to the job that produced it.

## 5. Thumbnail Selection

`VideoPackage.thumbnail_frame_uri` is chosen from `storyboard.emphasis_points` (per `storyboard-schema.json`) rather than an arbitrary frame (e.g. the first frame, which is frequently just the hook's opening state and not visually representative) — specifically, the frame at the timestamp of the first `emphasis_point`, since that field already names the reel's most important visual moment. This reuses a decision an earlier stage already made deliberately, instead of inventing a new one here.

## 6. Technical Output Standards

Phase 1 assumes, but does not hard-code as unchangeable: `9:16` resolution at a platform-appropriate size (e.g. 1080×1920), H.264 video codec, AAC audio codec, and an MP4 container — the broadest-compatibility choice across `youtube-shorts`, `instagram-reels`, and `tiktok`. `resolution` and `codec` are still explicit fields in `VideoPackage`, not assumed constants, so a platform-specific render profile can override them later without a schema change.

## 7. Future Extensibility

- **Platform-specific render profiles** — `platform` is already carried through the whole pipeline; a future version could produce multiple `VideoPackage`s (different resolutions/codecs) from one `RenderJob` per platform's preferred spec.
- **Render engine swapping** — `RenderJob` doesn't name a specific rendering technology; whatever engine executes it (a video composition library, a cloud rendering service) is an implementation detail behind the job contract, not part of this schema.
- **Partial re-render** — `failed_at_scene_id` exists specifically so a future implementation could re-render only the failed scene and re-composite, rather than restarting the entire job, once that optimization is worth building.
- **Automatic scene compression on render** — mirrors `storyboard-spec.md` Section 11; if a `RenderJob` discovers the assembled duration exceeds a hard platform cap, that's a condition `error_message` can name today, and a future auto-trim pass could resolve without failing the job.

## 8. Edge Cases

- **A `RenderJob` fails partway through.** `status: "failed"` with `error_message` and, where determinable, `failed_at_scene_id` — never a `VideoPackage` with a partial or corrupted video. Failure is binary at the package level, even if the job itself made partial progress.
- **`voice_result.drift_exceeds_threshold` was true and nothing downstream corrected it.** The Renderer should not silently absorb a large, unresolved timing mismatch — this is exactly the kind of condition that should have been caught before a `RenderJob` was submitted (see `voice-spec.md` Section 8), but if it wasn't, `RenderJob` should still surface it in a warning rather than produce a `VideoPackage` with unreviewed drift.
- **The chosen thumbnail frame (Section 5) is visually poor** (e.g. mid-transition, motion blur). This is a real risk of automated thumbnail selection — flagged here as a known limitation rather than solved; a future version might sample a small window around the emphasis point and pick the sharpest frame.
