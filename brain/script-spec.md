# Script Generator Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Script Generator: the layer that converts a lesson blueprint and a Content Director strategy into the actual spoken words and on-screen text for a reel. It sits between the [Content Director](content-director-spec.md) and the storyboard/rendering stages described in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## 1. Why the Script Layer Exists

Every earlier stage in `brain/` deliberately stopped short of writing dialogue:

- The lesson stage decided *what* concept, example, and takeaway the reel is built around, but wrote no narration (`lesson-spec.md` Section 7).
- The Content Director decided *how* to present that lesson — which hook type, which pacing, which emotional arc — but never wrote a line of it (`content-director-spec.md` Section 7).

The Script Generator is where those decisions finally become words. It exists as its own stage, rather than being folded into the Content Director, because "decide the strategy" and "execute the strategy in natural spoken language" are different skills with different failure modes: a content strategy can be sound while the actual sentences that implement it are stiff, too long, or technically imprecise. Separating the two means a bad script can be revised without re-deciding the strategy, and a bad strategy can be revised without discarding good prose.

## 2. How This Differs from the Lesson Layer

The lesson blueprint is structured teaching content: `key_points`, `step_by_step_explanation`, `core_example` — written to be *read and reasoned about*, not spoken aloud. Its sentences are often longer and more explanatory than anything that should be said in a 30-45 second reel (see `lesson-spec.md` Section 4, the "One of Everything" rule, and Section 7's lesson-vs-script boundary table).

The script takes that same committed content and rewrites it as short, natural, spoken sentences, in the specific order and posture the Content Director chose. Nothing new is taught that wasn't already in the lesson; nothing here decides a new concept, example, or takeaway. The Script Generator's only job is turning already-correct, already-sequenced content into words a person can say out loud in the time available.

## 3. How It Uses the Content Director Strategy

The Content Director's output isn't advisory — the Script Generator is required to follow it, not merely informed by it:

| Content Director decision | How the script must reflect it |
| --- | --- |
| `hook_type` | The `hook` field's actual content must be structurally that type — a `mistake`-family hook opens with the mistake itself (see the Methods example in `script-examples.md`), a `question` hook opens with the literal question, a `myth` hook opens by stating (and about to undercut) the misconception. |
| `teaching_style` | Determines opening posture: `code-first` means `code_narration` effectively opens the reel (or is pulled forward immediately after the hook); `analogy-first` means the lesson's `analogy` appears in `intro_line` or the first `main_script` beat, ahead of any code; `question-first` means the hook itself is phrased as an open question the script later answers. |
| `emotional_goal` | Shapes `tone` — see Section 6. |
| `pacing` / `scene_pacing` | Shapes how many `main_script` beats exist and roughly how long each one's spoken line should run (a `fast` pacing beat should be one short sentence, not two). |
| `visual_style` | Informs `scene_text` phrasing — visual techniques that highlight a specific moment (e.g. `highlight-animation`) pair with `scene_text` entries timed to that exact beat. |
| `code_style` | Shapes how `code_narration` frames the example — a `bug-example` code_style narrates the failure directly; an `optimization-example` narrates the "why this is better," not just "what this does." |
| `cta_style` | Determines `cta_line`'s content directly — a `comment` CTA asks a question inviting a reply; a `save` CTA frames the content as worth returning to. |

If the Script Generator's output doesn't visibly reflect the strategy it was given — for instance, a `code-first` strategy that still opens on an explanation — that is a defect in the script, not an acceptable interpretation of the strategy. See Section 5.

## 4. Why It Stays Short and Focused

A script that reads like a blog post or a lecture fails the format regardless of how accurate it is. Every constraint in this spec exists to prevent that:

- One lesson, one script — no field in the output schema allows introducing a second concept.
- Every sentence is written to be spoken, not read (Section 6).
- The full script is checked against its `target_duration_seconds` via `word_count` and `estimated_duration_seconds` (Section 8) — a script that doesn't fit isn't padded to justify itself, it's cut.
- `main_script` is explicitly the *remaining* teaching body after the hook and intro — not a place to re-explain everything the lesson covered.

## 5. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `lesson` | Full lesson blueprint, matching `lesson-output-schema.json` | The committed concept, example, analogy, takeaway, and safety notes — the actual content being scripted. |
| `content_director_output` | Full content strategy, matching `content-director-schema.json` | The required hook type, teaching style, pacing, and tone this script must follow — see Section 3. |
| `topic` | `{ topic_id, topic_title }`, mirrors `lesson.topic_id` / `lesson.topic_title` | Explicit top-level identity so orchestration doesn't need to reach into the lesson object just to address the topic. |
| `curriculum_context` | The topic's entry in `curriculum/java-roadmap.json` | Used for `next_topics` phrasing in a `next-lesson-teaser` CTA, and to sanity-check vocabulary against the topic's `difficulty`. |
| `memory_state` | The relevant slice of a memory state matching `memory/memory-schema.json` | Tells the Script Generator whether this is a revision and what specifically needs to read differently this time (see the Arrays example in `script-examples.md`). |
| `audience_level` | Carried from `lesson.audience_level` unless overridden | Controls vocabulary simplicity and how much is explained versus assumed. |
| `target_duration` | Carried from `content_director_output.target_duration_seconds` unless overridden | Bounds `word_count` — see Section 8. |
| `brand_voice` | Caller-supplied; not yet backed by a persisted schema in this repo (see Section 9) | A voice profile (tone descriptors, catchphrases, banned phrases, formality level) informing `tone` selection. Phase 1 has no `brand_voice` file to reference — this is a known gap, not an oversight; see Section 10. |
| `platform` | Caller-supplied, one of `youtube-shorts`, `instagram-reels`, `tiktok`, `generic-vertical-short` | Minor phrasing/format conventions (e.g. "comment below" vs. platform-specific reply conventions). Does not change the underlying content. |
| `language_constraints` | Caller-supplied, e.g. `{ max_sentence_length_words, avoid_contractions, avoid_idioms }` | Hard limits the script must respect regardless of what reads most naturally — see Section 6. |

## 6. Spoken Delivery Guidelines

The script is written to be heard once, not read at leisure. Concretely:

**Prefer:**
- Short sentences — most `main_script` and `code_narration` lines in `script-examples.md` run 8-16 words.
- Clear, active verbs ("crashes," "replaces," "compiles") over passive or abstract phrasing ("is caused by," "results in").
- Simple transitions ("but," "so," "and then") over formal connectives ("however," "consequently," "as a result").
- Direct address where it helps ("you've probably typed...") to keep the tone conversational.

**Avoid:**
- Dense technical jargon without a plain-language anchor — if a term from `lesson.core_concepts` is necessary (e.g. "ArrayIndexOutOfBoundsException"), it should be paired with what it means in the moment, not assumed understood.
- Long compound sentences stacking multiple clauses — split them into two short sentences instead.
- Unnatural phrasing that reads fine on a page but sounds stilted spoken aloud (a quick "would I actually say this to a friend?" check is a reasonable heuristic).
- Filler ("basically," "so yeah," "kind of") beyond what a specific `tone` deliberately calls for — `playful` or `warm-encouraging` tones can carry a little more casual filler than `direct-and-punchy`.

`tone` (one of `energetic`, `calm-confident`, `playful`, `direct-and-punchy`, `warm-encouraging`) is chosen from `content_director_output.emotional_goal` and, once it exists, `brand_voice` — a `surprise` emotional goal tends toward `direct-and-punchy` or `energetic`; a `curiosity` goal tends toward `calm-confident` or `playful`. `language_constraints` (Section 5) can override defaults regardless of tone — a hard `max_sentence_length_words` limit applies even to an `energetic` tone that would otherwise run long.

## 7. Subtitle and Scene Friendliness

Two fields exist specifically to make downstream storyboarding and captioning mechanical rather than interpretive:

- **`subtitle_segments`** — the entire `full_spoken_script`, broken into phrase-or-sentence-sized cards, in delivery order. Every segment records `source_field` (which part of the script it came from), an `estimated_duration_seconds`, and 0-2 `emphasis_words` — the words worth visually bolding or highlighting when the subtitle renders. `script-examples.md` demonstrates that concatenating every `subtitle_segments[].text` in order reproduces `full_spoken_script` exactly — segmentation must be lossless, not a paraphrase.
- **`scene_text`** — short on-screen keyword overlays (not sentences), each tied to a `scene_reference` matching a Content Director `scene_pacing` entry. This is deliberately a *different* thing from subtitles: subtitles caption what's said, `scene_text` labels what's shown (e.g. "DOES NOT COMPILE," "== vs .equals()").

## 8. Word Count and Duration Estimation

`estimated_duration_seconds` is computed from `word_count` at a baseline conversational speaking rate of **2.5 words per second** (150 words/minute). This is a deliberately simple, documented formula, not a tuned model — `pacing` from the content strategy can nudge the rate slightly in a real implementation (faster pacing tolerates a marginally quicker rate, slower pacing a marginally slower one), but 2.5 wps is the default assumption throughout `script-examples.md`.

`estimated_duration_seconds` should land at or somewhat below `target_duration_seconds` — every example in `script-examples.md` does, by 3-11 seconds — leaving room for natural pauses, transitions, and on-screen beats that don't require new narration. A script that estimates *over* its target duration should be cut, not spoken faster; speaking rate is a viewer-side constant, not a lever the script controls.

## 9. Output: The Script Object

Full field-level contract: [script-output-schema.json](script-output-schema.json). `full_spoken_script` is a **derived** field — it must equal the concatenation of `hook`, `intro_line`, every `main_script[].spoken_line`, every `code_narration.spoken_lines[]`, `recap_line`, and `cta_line`, in that delivery order (adjusted per Section 3 when `teaching_style` pulls `code_narration` earlier). It is not independently authored content; if it doesn't reconstruct from the structured fields, the script is internally inconsistent.

The Reviewer stage consumes `confidence_notes` and the lesson's carried-through `safety_notes`/`what_to_avoid_saying` to check the script never says something the research stage explicitly flagged as incorrect. The Storyboard Generator consumes `scene_text`, `subtitle_segments`, and `code_narration.code_snippet`/`focus_line` directly.

## 10. Future Extensibility

- **A/B testing of hooks** — `variant_id` already exists (default `"primary"`) specifically so multiple scripts can share one `topic_id` with different `hook`/`hook_type` choices, without changing the schema.
- **Multiple script variants** — the same mechanism: a future orchestration layer can request several `variant_id`s for the same lesson + content strategy and compare them once analytics exist.
- **Different creator voices** — `brand_voice` is already a named input (Section 5); Phase 1 has no persisted schema or file backing it, which is a real gap, not a design decision — a future `brand/voice-profile.json` (or similar) would slot in here without changing this schema, since `tone` already reserves the field it would inform.
- **Topic-specific pacing** — already partially live: `pacing`/`scene_pacing` from the Content Director already shape `main_script` beat count and length (Section 3); a future version could tune the 2.5 wps rate per topic category once real delivery data exists.
- **Retention scoring** — `confidence_notes.flagged_uncertainties` already surfaces judgment calls (e.g. the Arrays revision's cold-open bet); once real performance data exists, those flags are exactly what a retention-scoring pass would check against outcomes.
- **Audience feedback adaptation** — mirrors the Content Director's own `strategy_performance` extension point (`content-director-spec.md` Section 8): once performance is tracked per `hook_type`/`tone`/etc. across scripts, not just per topic, script-level heuristics (Section 6's tone defaults, Section 8's speaking rate) become tunable rather than fixed.

## 11. Edge Cases and Limitations

- **The strategy and the lesson content pull in different directions** (e.g. `teaching_style: code-first` but the lesson's `core_example` is thin and the `analogy` is unusually strong). The script must still follow the strategy — `confidence_notes.flagged_uncertainties` should say the fit was awkward, not silently substitute a different opening.
- **`word_count` exceeds the duration budget even after trimming filler.** This signals the lesson or strategy asked for more than fits — `confidence_notes` should flag it rather than the script quietly running over `target_duration_seconds`. Cutting content is a valid resolution; speaking faster is not (Section 8).
- **`language_constraints` conflicts with technical accuracy** — e.g. a constraint to avoid jargon entirely, but the topic requires naming `ArrayIndexOutOfBoundsException` for the crash to make sense on screen. Accuracy wins; the constraint should be satisfied everywhere else in the script, and the conflict noted in `confidence_notes`.
- **`code_narration`'s position in the sequence is ambiguous** — Section 3 documents that `code-first` strategies may pull it forward, but the schema doesn't enforce a single canonical position. This is deliberate flexibility, not an oversight, but it means `full_spoken_script`'s exact ordering (not just its content) must be checked against the delivery order actually used, not assumed from field order in the schema.
- **No `brand_voice` schema exists yet** (Section 5, Section 10) — every example in `script-examples.md` uses a `tone` chosen from `emotional_goal` alone, with no brand-specific override. This is an acknowledged Phase 1 gap.
