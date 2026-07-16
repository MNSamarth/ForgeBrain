# Lesson Stage Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's lesson stage: the layer that turns a research brief into a **lesson blueprint** — a teachable, single-concept plan the Script Generator can convert into narration. It corresponds to the Lesson Generator component in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md), and sits between the [research stage](research-spec.md) and script generation.

## 1. Why a lesson layer has to sit between research and script writing

A research brief is deliberately broad: 3-5 `core_concepts`, 2-4 `code_example_ideas`, 2-4 `common_misconceptions` — a curated set of *candidate* teaching material, not a commitment to any one of them. That breadth is correct for research, whose job is to make sure nothing important was missed. It is wrong for a 30-45 second reel, which can only responsibly land one idea.

The lesson stage exists to make that narrowing decision deliberately, once, in its own step — rather than leaving it to whichever stage writes the actual words:

- **Turns raw topic facts into a teachable structure.** Research answers "what's true and worth knowing about this topic." The lesson stage answers "given all of that, what is the *one* thing this specific reel teaches, and in what order do we walk up to it." That is a pedagogical decision, distinct from a factual one.
- **Improves clarity, pacing, and retention.** A lesson built around one main concept, one example, and one takeaway is easier to follow and easier to remember than one that tries to cover everything research surfaced. Retention drops sharply when a short-form video tries to do too much — the lesson stage's entire job is preventing that by construction, not by later editing.
- **Keeps the final reel focused on one learning goal.** `lesson_objective` is a single required sentence, not a list. Even when the research brief offered several strong `core_concepts`, the lesson stage must pick the throughline they all serve and structure `key_points` as steps toward that one objective — not as a list of separate, competing concepts. See Section 4.

## 2. Position in the Pipeline

```
Topic Selector → Research Stage → Lesson Stage → Script Generator → Reviewer → ...
(decision)        (topic brief)    (lesson       (script)
                                     blueprint)
```

The lesson stage consumes one research brief and produces exactly one lesson blueprint. It does not write spoken narration, on-screen text formatting, or timing beats — those belong to the Script Generator (see Section 5).

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `selected_topic` | `topic_id` from the research brief | Identifies which topic this lesson is for. |
| `research_brief` | Full output of the research stage, matching `research-output-schema.json` | The factual and pedagogical raw material the lesson stage narrows down. |
| `curriculum_context` | The topic's entry in `curriculum/java-roadmap.json` | Confirms `level` and where this topic sits in the learning path, useful for calibrating how much can be assumed vs. must be taught fresh. |
| `memory_state` | The relevant slice of a memory state matching `memory/memory-schema.json` | Tells the lesson stage whether this is a first pass or a revision, and what specifically underperformed last time (see Section 6). |
| `audience_level` | Carried forward from the research brief unless explicitly overridden | Controls how much the `step_by_step_explanation` can assume vs. must spell out. |
| `target_duration` | Carried forward from the research brief's `target_reel_length_seconds` unless overridden | Bounds how many `key_points` and how long `step_by_step_explanation` can be. |
| `desired_teaching_style` | Caller-supplied, one of the styles in `lesson-output-schema.json` (`direct-explanation`, `problem-first`, `story-driven`, `comparison-based`, `myth-busting`) | Shapes how the lesson approaches its one concept — see Section 6. |

## 4. The "One of Everything" Rule

A lesson blueprint must contain exactly:

- **one main concept** — stated as `lesson_objective`, and everything else in the lesson serves it
- **one simple explanation** — the throughline `step_by_step_explanation` walks
- **one code example** — `core_example`, a single chosen example, not a menu
- **one beginner takeaway** — `beginner_takeaway`, the one sentence a viewer should remember
- **one memorable contrast or mistake** — the first, primary entry in `common_mistakes`
- **one visual cue** — the first, primary entry in `visual_notes`

Some of these fields are structured as short arrays (`key_points`, `common_mistakes`, `visual_notes`) rather than single strings, because supporting detail is still useful downstream (the Reviewer, or a secondary on-screen beat) — but the array is never a list of *competing* ideas. `key_points` are ordered steps that build toward the single `lesson_objective`; if two `key_points` could stand alone as separate lessons, that is a sign the lesson stage narrowed too little and should split further, not that the reel should teach both.

This rule is what keeps a reel from feeling like a rushed summary of everything research found. It is the lesson stage's central responsibility.

## 5. Output: The Lesson Blueprint

Full field-level contract: [lesson-output-schema.json](lesson-output-schema.json). What each field is for and which part of script generation it feeds:

| Field | Feeds | Purpose |
| --- | --- | --- |
| `topic_id`, `topic_title` | all stages | Identity and traceability back to curriculum and research. |
| `lesson_objective` | hook, closing line | The one thing this reel teaches. Everything else must serve this. |
| `lesson_summary` | script planning | 2-3 sentences on what the lesson covers and how it's approached — a quick-scan overview before the detail. |
| `key_points` | short spoken lines | 3-5 ordered steps that build toward `lesson_objective`, not a list of separate concepts. |
| `step_by_step_explanation` | short spoken lines, pacing cues | The ordered teaching beats — the walkthrough the Script Generator turns into narration, beat by beat. |
| `core_example` | code callouts | The one chosen example: a description, a short code sketch, and a note on what to emphasize. Not final on-screen code — the Script Generator formats and times it. |
| `analogy` | hook, short spoken lines | The one analogy this lesson uses, carried or refined from research. |
| `common_mistakes` | short spoken lines, on-screen text | 1-3 items; the first is the lesson's one memorable contrast or mistake. |
| `what_to_avoid_saying` | review checklist | Carried directly from the research brief's `safety_notes` — statements that must not appear in the script. |
| `beginner_takeaway` | closing line, on-screen text | The one sentence a viewer should walk away remembering. |
| `retention_hook` | hook | A committed opening line or hook concept — sharper than research's `topic_summary`, chosen specifically for this lesson's angle. |
| `visual_notes` | on-screen text, visual storyboard | 1-3 short visual cue descriptions; the first is the lesson's one required visual cue. |
| `confidence_notes` | review checklist | Carried and, if the lesson stage had to make a narrowing judgment call between multiple strong research concepts, updated to note that choice. |
| `audience_level`, `target_duration_seconds`, `teaching_style` | pacing cues | Constraints the Script Generator must respect. |
| `lesson_version`, `generated_at`, `based_on_research_version` | traceability (future caching) | Ties this blueprint back to the exact research brief version it was built from. |

## 6. Topic-Specific Tailoring

The schema does not change shape between topics — every lesson blueprint has the same fields. What changes is the *focus lens* the lesson stage applies when deciding `lesson_objective`, `key_points`, and `analogy` for a given topic category:

| Topic category | Focus lens |
| --- | --- |
| Variables | Storage and type clarity — what's actually being stored, and why the type matters. |
| Loops | Repetition and control flow — what runs, how many times, and what decides when it stops. |
| Methods | Reuse and structure — why bundling logic into a named, callable unit is worth it. |
| Arrays | Fixed-size, ordered data — what you gain (position-based access) and what you give up (no resizing). |
| Strings | Immutability and text handling — why "modifying" a String actually creates a new one. |
| Classes and Objects | Modeling real-world entities — the blueprint-vs-instance relationship. |

This table is guidance for the narrowing decision in Section 4, not a rule engine — a topic outside these six categories still goes through the same process: identify what this *kind* of topic is fundamentally about, and build `key_points` around that, rather than trying to cover the topic exhaustively.

## 7. Lesson-First, Not Script-First

The lesson stage stops short of the actual reel script. The boundary:

| The lesson blueprint owns | The Script Generator owns |
| --- | --- |
| Which one concept is being taught (`lesson_objective`) | The exact words used to say it |
| The ordered teaching beats (`step_by_step_explanation`) | Timing/duration for each beat |
| Which example is used and what it emphasizes (`core_example`) | The final, formatted on-screen code |
| The one analogy and the one mistake to contrast against | How they're phrased as spoken lines |
| Which visual cue matters (`visual_notes`) | Exact on-screen text, animation, and transition instructions |

If a field would require deciding exact wording or timing, it does not belong in the lesson blueprint — it belongs to script generation.

## 8. Future Extensibility

Not built in Phase 1, but the schema is shaped so these can be added without changing its structure:

- **Multiple lesson styles** — `teaching_style` is already an enum; new styles can be added to it without changing any other field.
- **Deeper explanation modes** — `step_by_step_explanation` is already an ordered array; a "deep dive" mode would simply produce more steps, not a different field.
- **Short vs. medium video formats** — `target_duration_seconds` already drives how much `key_points`/`step_by_step_explanation` content is reasonable; a medium-format mode changes the caps, not the schema.
- **Difficulty tuning** — `audience_level` is already carried through from research; a future tuning pass would just be additional logic writing to the same field.
- **Audience-specific versions** — multiple lesson blueprints could be generated from the same `research_brief` with different `audience_level` values; nothing in the schema assumes a 1:1 topic-to-lesson relationship.
- **Revision lessons** — `memory_state` is already an input specifically so a revision can restructure `key_points` or swap `core_example` in response to prior performance, as shown in Section 6's revision case in the examples.
- **Topic comparison lessons** ("X vs Y") — out of scope for a single-topic schema; would need a distinct blueprint type referencing two `topic_id`s, deliberately not designed here since Phase 1 has no such curriculum topics yet.

## 9. Edge Cases

- **Research offered several equally strong `core_concepts` with no obvious single throughline.** The lesson stage must still pick one. `confidence_notes.flagged_uncertainties` should name what was set aside and why, so a future revision can reconsider the choice with real performance data instead of guessing again from scratch.
- **`target_duration` is too short for the topic's minimum viable explanation.** The lesson stage should still produce a blueprint, but flag it in `confidence_notes` — this is a signal for a curriculum or duration adjustment, not something the lesson stage should silently cram to fit.
- **`memory_state` shows a prior revision that already addressed the most common mistake.** `common_mistakes` should reflect what's still actually a risk, not repeat a mistake the previous revision already fixed.
- **The research brief's `common_misconceptions` don't cleanly map to a single "memorable" one.** The lesson stage picks the one most likely to land as a hook (surprising, concrete, checkable) and demotes the rest to secondary entries — this is a judgment call, and like the concept-narrowing case above, worth a `confidence_notes` entry if the choice was close.
- **A topic's category doesn't match any row in the Section 6 table.** The lesson stage falls back to asking "what is this topic type fundamentally about" and proceeds with the same process; the table is illustrative, not exhaustive.
