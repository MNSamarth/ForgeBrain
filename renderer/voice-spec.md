# Voice Generation Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Voice Generation stage: the layer that turns a storyboard's `voiceover_text` into actual narrated audio. It is the first stage in `renderer/` — the production layer, as distinct from the decision/planning layer in [`brain/`](../brain/) — and sits between the [Storyboard Generator](../brain/storyboard-spec.md) and [Subtitle Generation](subtitle-spec.md).

## 1. Why Voice Generation Is Its Own Stage

Every word this stage speaks was already decided by the Script Generator and placed on a timeline by the Storyboard Generator. Voice Generation doesn't decide *what* is said — it decides *how it sounds*: which voice, what pacing, what emphasis. Keeping this separate from script/storyboard writing means the same script can be re-voiced (a different creator voice, a different language, a corrected pronunciation) without re-deciding any content, and content can be revised without re-recording audio that was already correct.

## 2. Position in the Pipeline

```
Storyboard → Voice Generation → Subtitle Generation → Asset Management → Renderer → Reviewer → Publishing Package
```

Voice Generation consumes one storyboard and produces exactly one `VoiceResult`. It is the first stage in the entire pipeline that produces an actual media artifact (audio) rather than structured text data — everything before it is planning; everything from here on is production.

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `storyboard` | Full storyboard object, matching `storyboard-output-schema.json` | Supplies every scene's `voiceover_text`, in order, plus each scene's `estimated_duration_seconds` (the baseline this stage's real measurements get compared against). |
| `voice_profile` | Caller-supplied (see Section 6) | Which synthetic voice, language, speaking rate, and pitch to render with. |
| `platform` | Carried from the storyboard | Minor format conventions (e.g. platform-preferred audio codec); does not change narration content. |

## 4. The Reconciliation Problem: Estimated vs. Actual Timing

Every duration in the pipeline up to this point — `script.estimated_duration_seconds`, every `storyboard.scenes[].duration` — was computed from a **word-count formula** (2.5 words/second, per `script-spec.md` Section 8), not from real speech. That formula is good enough to plan pacing and scene counts, but it is not what a real voice actually takes to say a given line: pauses, emphasis, and natural speech rhythm all shift the real number, sometimes by a second or more per scene.

Voice Generation is the stage where the estimate meets reality. For every scene, it produces:

- `estimated_duration_seconds` — carried forward from the storyboard, unchanged, for comparison.
- `actual_duration_seconds` — measured directly from the generated audio.
- `duration_drift_seconds` — `actual - estimated`, signed, so downstream stages know whether a scene ran long or short.

**From this point forward, `actual_duration_seconds` is the timing authority, not the storyboard's estimate.** The storyboard remains authoritative for scene *order*, *content*, and *visual instructions* — nothing here changes what a scene shows or says — but final cut timing for rendering comes from Voice Generation's measurements. This is a deliberate, explicit handoff of authority over one specific property (timing) from one stage to the next, not a contradiction between them.

## 5. Output: VoiceResult

Full field-level contract: [voice-schema.json](voice-schema.json). Per-scene `word_timings` (word-level start/end alignment within the audio) exist specifically to give Subtitle Generation exact sync data — see `subtitle-spec.md` Section 3 — rather than forcing it to re-derive timing from another word-count estimate.

`drift_exceeds_threshold` and `drift_threshold_seconds` exist so a large, unexpected timing gap (e.g. a TTS engine badly mispronouncing and re-reading a term, or an SSML tag inflating pause length) is a flagged, checkable condition rather than something only noticed after rendering.

## 6. Provider

Phase 1 targets **Google Cloud Text-to-Speech**, consistent with the project's Google Cloud-first direction (see `docs/CONFIGURATION.md`). The `voice_profile` structure (`voice_id`, `language_code`, `speaking_rate`, `pitch`) mirrors Cloud Text-to-Speech's own request shape closely enough to map onto it directly, but the schema does not hard-depend on it — `voice_id` is an opaque string, not a Google-specific type, so a future provider swap (a different TTS engine, or a custom-trained voice) would only need to populate the same fields differently. This follows the same "replaceable AI providers" principle already established in `docs/ARCHITECTURE.md` Section 3.

`voice_profile` is also, deliberately, the first place `brand_voice` (referenced as an unresolved input since `script-spec.md` Section 5) becomes concrete on the *audio* side — a real `voice_id` has to be chosen somewhere. The *narrative* side of `brand_voice` (tone descriptors, catchphrases, banned phrases) remains an open Phase 1 gap; see `TODO.md`.

## 7. Future Extensibility

- **Multiple creator voices** — `voice_profile` is already a named, swappable input, not a hardcoded default; a future version could hold a small library of profiles and select one per brand or per experiment.
- **Emotion / delivery control** — `voice_profile` reserves room for SSML-style parameters (rate, pitch) now; prosody/emphasis control (e.g. driven by the script's `highlighted_words`) is a natural extension of the same object without a schema change.
- **Multi-language** — `language_code` is already part of `voice_profile`; Phase 1 only ever populates it with `en-US`, but nothing in the schema assumes English.
- **A/B testing voices** — mirrors `variant_id` in `script-output-schema.json`; multiple `VoiceResult`s could be generated from one storyboard once that's worth testing.

## 8. Edge Cases

- **`drift_exceeds_threshold` is true.** The pipeline should not silently proceed to rendering with a storyboard-vs-audio mismatch this large — `confidence_notes.flagged_uncertainties` must say which scene(s) drifted and by how much, so a human or the Reviewer stage can decide whether to accept the drift, adjust `voice_profile.speaking_rate`, or send the storyboard back for re-planning.
- **A technical term is mispronounced.** TTS engines routinely misread Java-specific terms (e.g. "char" as a food-related word, or camelCase identifiers read as one run-on word). This is a correctness risk the same way a scripted factual error is — `confidence_notes.flagged_uncertainties` should name any term the voice profile is known to mishandle, and the Reviewer stage's final QC pass (see `reviewer/reviewer-spec.md`) is expected to catch what wasn't caught here.
- **No `word_timings` data is available from the provider for a given scene.** The scene's `actual_duration_seconds` is still required (measurable from the raw audio file directly), but `word_timings` may be an empty array — Subtitle Generation falls back to proportional estimation within that scene rather than failing outright (see `subtitle-spec.md` Section 5).
