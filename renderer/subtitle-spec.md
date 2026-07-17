# Subtitle Generation Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Subtitle Generation stage: the layer that reconciles the storyboard's *estimated* subtitle timing with Voice Generation's *actual* measured audio timing, and produces the final, render-ready subtitle file. It sits between [Voice Generation](voice-spec.md) and [Asset Management](asset-management-spec.md).

## 1. Why This Is Its Own Stage

The storyboard already contains `subtitle_segments` with text, emphasis words, and timing — but that timing was computed from the same word-count formula used everywhere upstream (`script-spec.md` Section 8), not from real speech. Voice Generation (Section 4 of `voice-spec.md`) establishes that real audio timing supersedes the estimate. Subtitle Generation exists specifically to carry that correction through to the one artifact where a timing mismatch is immediately, visibly obvious to a viewer: captions that drift out of sync with the voice.

This is deliberately not folded into Voice Generation itself, because reconciling timing (an audio-analysis problem) and formatting captions for a specific platform/style (a presentation problem) are different concerns — the same reconciled timing could be rendered as `bold-centered`, `karaoke-highlight`, `minimal-bottom-third`, or `boxed-caption` (per `storyboard.subtitle_style`) without re-solving the timing problem each time.

## 2. Position in the Pipeline

```
Voice Generation → Subtitle Generation → Asset Management → Renderer → Reviewer → Publishing Package
```

Subtitle Generation consumes one storyboard and its corresponding `VoiceResult`, and produces exactly one `SubtitleResult`.

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `storyboard` | Full storyboard object | Supplies the original `subtitle_segments` per scene (text and `emphasis_words`) and the reel's `subtitle_style`. |
| `voice_result` | Full `VoiceResult`, matching `voice-schema.json` | Supplies `actual_duration_seconds` and `word_timings` per scene — the ground truth this stage reconciles against. |
| `aspect_ratio` | Carried from the storyboard | Affects safe-region placement (Section 6). |
| `platform` | Carried from the storyboard | Some platforms (e.g. those that auto-generate their own captions) may call for a different `format` — see Section 5. |

## 4. Reconciliation: Two Methods, One Fallback

For each scene, subtitle timing is recomputed using whichever method the available data supports:

1. **Word-alignment** (preferred) — when `voice_result.scenes[].word_timings` is populated, each subtitle segment's real `start_time`/`end_time` is computed directly by summing the real per-word timestamps of the words it contains. This produces frame-accurate sync.
2. **Proportional-estimate** (fallback) — when `word_timings` is empty for a scene (see `voice-spec.md` Section 8), that scene's original estimated segment durations are scaled proportionally to fit the scene's real `actual_duration_seconds`, preserving relative timing between segments even though absolute per-word accuracy is lost.

Every scene records which method was used (`reconciliation_method`), so a downstream reviewer can tell which subtitles are frame-accurate and which are a best-effort estimate — this is never silently degraded.

Regardless of method, one invariant always holds: **every scene's final subtitle segments must span exactly that scene's `actual_duration_seconds` from `voice_result`, not the storyboard's original estimate.** A subtitle file timed against the estimate would drift from the real audio by exactly the amount `voice-spec.md` Section 4 describes.

## 5. Output: SubtitleResult

Full field-level contract: [subtitle-schema.json](subtitle-schema.json). `format` is chosen from `storyboard.subtitle_style`: a `karaoke-highlight` style requires per-word timing survive into the rendered file (ASS supports this; plain SRT/VTT do not), so `karaoke-highlight` maps to `format: "ass"`; the other three subtitle styles (`bold-centered`, `minimal-bottom-third`, `boxed-caption`) are expressible in `srt` or `vtt` plus renderer-side styling, since they don't need word-level timing baked into the caption file itself.

## 6. Safe Regions and Readability

Per `storyboard-spec.md` Section 9, a `9:16` frame commonly has platform UI (usernames, engagement buttons, native captions) overlapping the bottom ~15-20% and sometimes top ~10% of the frame. `safe_region` in the output records the vertical band this reel's subtitles were planned to stay within, so the Renderer doesn't have to re-derive it per platform. Line length is capped implicitly by each segment already being phrase-sized (inherited from the script's `subtitle_segments`, per `script-spec.md` Section 7) — this stage does not re-wrap or split text, only re-times it.

## 7. Future Extensibility

- **Multi-language subtitles** — nothing in the schema assumes English; a translated `text` per segment would slot into the same structure, keyed by `language_code` alongside `voice_profile.language_code` from Voice Generation.
- **Platform-native caption formats** — `format` is already an explicit enum rather than a fixed choice; a platform that ingests its own caption format could be added without restructuring segments.
- **Accessibility variants** — a future "verbose" caption mode (including non-speech sound cues) is a variant of the same segment structure, not a different pipeline stage.

## 8. Edge Cases

- **A scene's `word_timings` is empty and its estimated segment count doesn't obviously map to real speech pauses.** The proportional-estimate fallback (Section 4) still produces a complete, usable subtitle track — imperfect timing is preferred over missing subtitles. `confidence_notes.flagged_uncertainties` should name which scenes used the fallback.
- **`voice_result.drift_exceeds_threshold` is true.** Subtitle Generation still proceeds — its job is to be correct *given* the actual audio, not to second-guess whether the audio itself was acceptable (that's `voice-spec.md` Section 8's concern, checked before this stage runs, or the Reviewer stage's concern after rendering).
- **A subtitle segment's reconciled duration is too short to be legible** (a very short scene with dense text). This is a signal the storyboard's scene pacing was too aggressive for its word count — worth flagging in `confidence_notes`, not silently truncating text.
