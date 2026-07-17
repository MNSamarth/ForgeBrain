# Storyboard Generator Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Storyboard Generator: the layer that converts a finalized script into a scene-by-scene visual plan a renderer can execute without guessing. It sits between the [Script Generator](script-spec.md) and the rendering stage described in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## 1. Why Storyboard Generation Is Needed After the Script

A finished script answers what is said and roughly how long it takes to say it. It does not answer what the viewer is looking at while it's being said — and for a format with no on-camera host, that question has no default answer. Someone has to decide it deliberately, the same way someone had to deliberately decide the hook type (Content Director) and the actual words (Script Generator).

Leaving that decision implicit — assuming a renderer will "figure out something reasonable" from a wall of narration — reintroduces exactly the failure mode every earlier `brain/` stage was built to avoid: an undocumented, unreviewable judgment call buried inside whichever component runs last. The storyboard makes visual planning its own explicit, auditable stage, with its own output a human or a renderer can inspect before a single frame is rendered.

## 2. How It Helps the Renderer Produce a Polished Reel

The Renderer (per `docs/ARCHITECTURE.md`) should not be making creative decisions — it should be executing them. A storyboard exists to remove every ambiguity a renderer would otherwise have to resolve on its own:

- **Exact timing** — every scene has a `start_time`, `end_time`, and `duration` computed from the script's own word counts, not a rendering-time guess.
- **Exact content per scene** — `voiceover_text`, `on_screen_text`, and `code_block` are already decided; the renderer places them, it doesn't choose them.
- **Exact motion and transitions** — `motion_notes`, `transition_in`, and `transition_out` specify behavior a renderer would otherwise have to invent per scene.

A renderer consuming a storyboard should never need to ask "what happens here?" — only "how do I execute this?"

## 3. How It Improves Pacing, Readability, and Retention

- **Pacing** — `pacing_profile` makes a reel's scene rhythm explicit and checkable (`shortest_scene_duration_seconds`/`longest_scene_duration_seconds` make a runaway long scene visible before rendering, not after).
- **Readability** — `scene_text` and code presentation fields (`code_block`, `code_style`) are decided with mobile-first, subtitle-safe constraints already applied (Section 8), rather than left to a renderer with no knowledge of those constraints.
- **Retention** — `emphasis_points` names the specific moments a reel's retention actually depends on (see the Arrays revision example in `storyboard-examples.md`, where the emphasis point directly names the payoff the whole cold-open strategy is betting on). This makes retention design reviewable before rendering, not something inferred after a reel underperforms.

## 4. Why Scene Planning Must Be Explicit Before Rendering

Short-form video lives or dies on the first few seconds and on constant visual variety (Section 6 of the task, and Section 7 below). Both of those are *structural* properties of the whole reel, not properties any single scene has in isolation — a renderer working scene-by-scene without a storyboard has no way to know if it just produced eight scenes that all look the same, or a 12-second static hold that will lose the viewer. `scene_count`, `pacing_profile`, and the full `scenes` array exist specifically so those whole-reel properties are decided and checkable *before* rendering commits to them, when it's still cheap to fix.

## 5. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `script` | Full script object, matching `script-output-schema.json` | The finalized words and timing this storyboard visualizes. Every `voiceover_text` in the output must trace back to this script's `full_spoken_script` — see Section 9. |
| `lesson` | Full lesson blueprint, matching `lesson-output-schema.json` | Backup source for `core_example.focus_note` and `visual_notes` if the script's own code/visual fields need clarification. |
| `content_director_output` | Full content strategy, matching `content-director-schema.json` | Supplies `visual_style`, `pacing`, `scene_pacing`, and `retention_goal` as starting signals — see Section 6 for how these map (and don't map 1:1) onto the storyboard's own fields. |
| `topic_context` | The topic's entry in `curriculum/java-roadmap.json` | Used for continuity cues (e.g. a CTA scene referencing `next_topics`). |
| `brand_voice` | Caller-supplied; same Phase 1 gap noted in `script-spec.md` Section 5 | Would inform `render_style` and `animation_style` defaults once a persisted brand voice profile exists. |
| `platform` | Caller-supplied, one of `youtube-shorts`, `instagram-reels`, `tiktok`, `generic-vertical-short` | Minor format conventions — see Section 8 for platform-aware subtitle/safe-region considerations. |
| `target_duration` | Carried from `script.target_duration_seconds` unless overridden | Bounds `total_duration_seconds` — see Section 9. |
| `aspect_ratio` | Caller-supplied, one of `9:16`, `1:1`, `4:5` | Defaults to `9:16` for vertical short-form. Affects safe-region planning (Section 8). |
| `render_style` | Caller-supplied, one of `dark-mode-ide`, `minimal-light`, `neon-tech`, `terminal-retro` | The overall brand aesthetic applied across every scene's `visual_description`. |
| `memory_state` | The relevant slice of a memory state matching `memory/memory-schema.json` | Tells the storyboard stage whether this is a revision and what specifically needs to look different this time — see the Arrays example in `storyboard-examples.md`. |

## 6. Relationship to the Content Director's Fields

Two field names are deliberately reused from `content-director-schema.json` with a *different* meaning at this stage. This mirrors the same kind of relationship `script-spec.md` Section 3 already documents between the lesson's and Content Director's `teaching_style` — a later stage inheriting a name from an earlier one while refining what it actually governs:

- **`visual_style`** — the Content Director's `visual_style` is a single strategic recommendation for the reel's dominant technique (e.g. `split-screen`). The storyboard's own `visual_style` field normally just carries that value forward at the top level, but the *scene-by-scene execution* can still vary — a `split-screen`-strategy reel can still have a `code-reveal` scene that isn't split-screen, because not every beat needs the dominant technique. The top-level field states the dominant strategy; the `scenes` array shows how it was actually realized.
- **`code_style`** — the Content Director's `code_style` (`minimal-example`, `comparison-example`, `bug-example`, `optimization-example`, `interview-example`) is about *narrative framing*: why this example, told which way. The storyboard's `code_style` (`typing-animation`, `line-by-line-reveal`, `static-panel-with-highlight`, `before-after-comparison`, `cursor-walkthrough`) is about *rendering technique*: how the code panel actually animates on screen. These are orthogonal — a `bug-example` framing can be rendered via `static-panel-with-highlight` or `typing-animation` depending on what suits the specific code. Neither value can be inferred from the other.

`animation_style` has no equivalent anywhere upstream — it's new at this stage, and orthogonal to `content_director_output.pacing`/`pacing_profile.tier` as well: pacing is about *how many scenes and how long each runs*; `animation_style` is about *how motion behaves within and between them* (e.g. `snappy-cuts` vs. `smooth-transitions`). A `fast`-paced reel can use either.

## 7. Scene Type Assignment

The nine `scene_type` values (`hook`, `setup`, `explanation`, `code-reveal`, `step-breakdown`, `mistake-highlight`, `comparison`, `recap`, `cta`) are chosen from what a beat actually does, not mechanically assigned from which script field it came from. One structural rule keeps this deterministic rather than a matter of taste:

> **The first scene in every storyboard is `scene_type: hook`**, describing its *position* in the reel. Every later scene is typed by its *content function* — what specifically happens in it — regardless of which script field(s) it draws from.

This is why, across `storyboard-examples.md`, a script's `code_narration` sometimes becomes a `code-reveal` scene and sometimes a `mistake-highlight` scene: the type follows what the code demonstrates (a working example vs. a failure), not which script field supplied the words. It's also why a storyboard scene doesn't have to map 1:1 to a script field — the Arrays example merges a `main_script` beat and `code_narration` into one `mistake-highlight` scene specifically because the underlying sentence is delivered as one continuous utterance (see `storyboard-examples.md` Section 4), and the StringBuilder example merges two `main_script` beats into one `comparison` scene specifically to honor a `split-screen` visual_style that needs both sides on screen simultaneously.

## 8. Code Visualization

Since every reel teaches Java, code presentation gets first-class, structured support rather than being folded into generic `visual_description` prose:

- **`code_block`** is a dedicated, nullable, structured field per scene (`code_snippet`, `focus_line`, `language`) — not a free-text description of code. A scene either shows code (with a specific line to highlight) or it doesn't (`null`); there's no ambiguous middle state.
- **`code_style`** picks the rendering technique from a fixed set (Section 6) — `typing-animation` for a code reveal that benefits from being watched appearing; `line-by-line-reveal` for a multi-part definition (see the Classes and Objects example); `static-panel-with-highlight` for code that's already fully visible and just needs attention drawn to one line; `before-after-comparison` for two versions shown together; `cursor-walkthrough` for following execution flow.
- **Consecutive code scenes reuse the same `code_snippet`** when they're really the same code panel with attention shifting (see Variables' two scenes both showing the same snippet with different `focus_line` values, and Classes and Objects' class-definition and instantiation scenes sharing one snippet). This keeps the visual continuous rather than implying the code panel resets between beats.
- **No storyboard invents code the script didn't provide.** Every `code_block.code_snippet` traces back to the script's `code_narration.code_snippet`. A scene that wants to gesture at code conceptually without showing an actual traceable snippet (see StringBuilder's `comparison` scene, which describes a conceptual split-screen race in `visual_description` rather than fabricating a second, untraceable code sample) sets `code_block: null` and relies on `visual_description` and `on_screen_text` instead.

## 9. Subtitle and Text Planning

- **`subtitle_segments`** at the scene level are the script's own `subtitle_segments` (Section 7 of `script-spec.md`), sliced to this scene's time range and given absolute `start_time`/`end_time` on the reel's timeline instead of the script's relative durations. Concatenating a scene's `subtitle_segments[].text` must reproduce that scene's `voiceover_text` exactly, and concatenating every scene's `voiceover_text` in `scene_order` must reproduce the script's `full_spoken_script` exactly — this is verified programmatically for every example in `storyboard-examples.md`, not merely asserted.
- **`highlighted_words`** at the scene level is the union of `emphasis_words` across that scene's `subtitle_segments` — a flat, renderer-convenient list so a caption renderer doesn't need to walk the nested array just to know what to bold.
- **Readable line lengths and mobile-first sizing** are a `subtitle_style` concern (`bold-centered`, `karaoke-highlight`, `minimal-bottom-third`, `boxed-caption`), chosen once for the whole reel rather than per scene, since inconsistent subtitle presentation mid-reel reads as a rendering bug, not a style choice.
- **Safe layout regions** are governed by `aspect_ratio` (Section 5) — a `9:16` frame reserves different safe regions for on-screen text than a `1:1` or `4:5` frame would, particularly given most short-form platforms overlay their own UI (captions, usernames, engagement buttons) near the bottom and sometimes top of the frame. This spec does not fix exact pixel-safe regions (that's a rendering-time concern), but `scene_text`/`on_screen_text` placement should assume the bottom ~15-20% and top ~10% of a `9:16` frame may be obscured by platform chrome.

## 10. Short-Form Pacing (Render-Readiness Checklist)

A storyboard is render-ready when it satisfies all of the following, every one of which is mechanically checkable from the schema without human judgment:

- `scenes[0].scene_type` is `hook`, and its `start_time` is `0` — the reel opens immediately, no lead-in.
- No single scene's `duration` is disproportionately long relative to `pacing_profile.average_scene_duration_seconds` — `pacing_profile.longest_scene_duration_seconds` makes this checkable directly (see `storyboard-spec.md` Section 3's "no long static scenes" requirement).
- `scenes` are perfectly contiguous — every scene's `start_time` equals the previous scene's `end_time`; there is no gap for a renderer to fill in ambiguously and no overlap to resolve.
- `total_duration_seconds` equals the sum of every scene's `duration`, and lands at or under `target_duration_seconds`.
- Every scene that shows code has a non-null `code_block` with both `code_snippet` and `focus_line` populated — never code shown with no indication of what matters about it.
- Every scene's `subtitle_segments` cover that scene's full `voiceover_text` with no missing spoken content.

## 11. Future Extensibility

- **Template-based rendering** — `visual_style`/`animation_style`/`render_style`/`transition_style` are already independent, named enums rather than free text; a future template system maps each combination to concrete render instructions without changing this schema.
- **Multiple visual styles** — nothing here assumes one storyboard per script; a future orchestration layer could request several storyboards (different `render_style`/`visual_style` combinations) from the same script, the same way `variant_id` (in `script-output-schema.json`) supports multiple scripts from one lesson.
- **A/B tests for motion patterns** — `animation_style` and `transition_style` are exactly the two fields such a test would vary; `confidence_notes.flagged_uncertainties` already provides a place to record which motion choice was uncertain and worth testing.
- **Platform-specific formats** — `platform` and `aspect_ratio` are already independent inputs; a future platform-aware pass would primarily affect Section 9's safe-region guidance without restructuring scenes.
- **Automatic scene compression** — if a future implementation needs to shorten a storyboard (e.g. a platform duration cap), `pacing_profile` already exposes exactly the numbers (`shortest_scene_duration_seconds`, scene count) such a compression pass would need to decide which scenes to merge or trim.
- **Audio-reactive motion** — `motion_notes` is free text now specifically so it can later reference audio cues (beat drops, emphasis words) without a schema change; `highlighted_words` already marks the words most likely to drive such motion.
- **Visual analytics feedback** — mirrors the Content Director's `strategy_performance` extension point (`content-director-spec.md` Section 8) and the Script Generator's equivalent (`script-spec.md` Section 10): once performance is tracked per `scene_type`/`visual_style`/`animation_style` combination across reels, not just per topic, `emphasis_points` and `pacing_profile` become the fields a feedback loop would check against actual retention curves.

## 12. Edge Cases and Limitations

- **A script beat doesn't cleanly split into scenes without breaking a sentence mid-thought.** The storyboard should merge those script fields into one scene rather than force an artificial cut — see Section 7 and the Arrays example. This is a deliberate exception to "one scene per script field," not an inconsistency.
- **A `code_narration` snippet contains two logically distinct moments** (e.g. a class definition and its instantiation). The storyboard may split it across two scenes that reuse the same `code_snippet` with different `focus_line` values, rather than either cramming both moments into one scene or fabricating two separate snippets — see Section 8.
- **The Content Director's `visual_style` doesn't fit every scene equally well.** The top-level `visual_style` field states the dominant strategy; individual scenes are free to use a different technique in `visual_description` where it genuinely serves that beat better (Section 6). This should not be treated as ignoring the strategy — it's the same kind of refinement `script-spec.md` Section 3 already permits for `teaching_style`.
- **A revision reel's storyboard carries real, unresolved risk** (see the Arrays example, `confidence_notes.overall_confidence: medium`). The storyboard stage should not smooth this over — if an upstream stage flagged uncertainty about whether a strategy will actually work, and the storyboard is the stage where that strategy is finally made concrete and visible, the flag belongs here too, phrased in terms of what could fail visually.
- **No persisted `brand_voice` schema exists yet** (Section 5) — every example in `storyboard-examples.md` chooses `render_style`/`animation_style` from topic content and content strategy alone, with no brand-specific override. Same acknowledged Phase 1 gap as `script-spec.md` Section 11.
