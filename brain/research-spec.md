# Research Stage Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's research stage: the layer that turns a selected topic into a short, trustworthy **topic brief** the Lesson Generator can build on. It corresponds to the Research Agent component in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md), and sits directly after the [topic selector](topic-selector-spec.md) in the pipeline.

## 1. Why research has to happen before lesson generation

The topic selector decides *what* to teach; it does not know *how* to teach it correctly. Handing a bare topic_id straight to a lesson generator would leave every factual and pedagogical decision to whichever model writes the lesson, with nothing to check its work against and no record of what it decided. Research exists to make that handoff safe:

- **Accuracy and clarity.** The research stage distills a topic into a fixed set of core concepts, a validated analogy, and example ideas *before* any teaching language is written. The Lesson Generator writes to a brief, not from a blank page — it's shaping already-correct material into narration, not deciding what's true while also deciding how to phrase it.
- **Hallucination prevention.** Java has enough sharp edges (autoboxing, pass-by-value semantics, checked vs. unchecked exceptions, memory model subtleties) that a model asked to "explain X" under time pressure will occasionally state something confidently and incorrectly. The brief's `safety_notes` field exists specifically to name those traps in advance, so the Lesson Generator and Reviewer both have a fixed list of statements that must never appear in the reel.
- **Better hooks, examples, and explanations.** `simple_analogy`, `topic_summary`, and `code_example_ideas` are pre-selected here, not invented under the pressure of also writing a full script. Separating "what's the best way to explain this" from "write the narration" produces a stronger hook because the hook-writing gets its own dedicated step.

## 2. Position in the Pipeline

```
Topic Selector  →  Research Stage  →  Lesson Generator  →  Script Generator  →  Reviewer  →  ...
(decision object)   (topic brief)      (lesson)             (script)
```

The research stage consumes the topic selector's decision object (specifically `selected_topic_id`) and produces exactly one topic brief per run. It does not select topics and does not write lesson prose — see `docs/ARCHITECTURE.md` Section 4 for how these responsibilities are divided.

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `selected_topic` | `selected_topic_id` from a topic selector decision object | Identifies which curriculum topic to research. |
| `curriculum_context` | The topic's full entry in `curriculum/java-roadmap.json` | Supplies `title`, `level`, `difficulty`, `learning_objective`, `common_mistakes`, and `example_ideas` as a starting point — research refines and validates these, it doesn't invent from nothing. |
| `prerequisite_topics` | The topic's `prerequisites` array, resolved to titles via the curriculum | Tells the research stage what the audience can already be assumed to know, so the brief doesn't re-explain foundational material that belongs to an earlier reel. |
| `audience_level` | Caller-supplied, defaults to the topic's curriculum `difficulty` if not overridden | Controls how much simplification the `beginner_explanation` should apply. |
| `desired_reel_length` | Caller-supplied, in seconds (typically 30-90 for this format) | Bounds how many `core_concepts` and `code_example_ideas` the brief should produce — a 30-second reel cannot responsibly carry five concepts. |
| `current_memory_state` | The relevant slice of a memory state matching `memory/memory-schema.json` for this `topic_id` (if any) | Tells the research stage whether this is a first-time topic or a revisit, and surfaces `times_used`, `revision_count`, and prior `notes`. |
| `recent_performance_notes` | `memory.topics[topic_id].notes` and `audience_response`, when present | Directly informs what must change this time — e.g. a prior note about pacing or an inaccurate explanation shapes `core_concepts` ordering and `safety_notes`. |

Example input shape (illustrative, not a formal schema — inputs are assembled by the orchestration layer from sources that already exist elsewhere in the repo):

```json
{
  "selected_topic": "java-operators",
  "curriculum_context": { "...": "full entry from curriculum/java-roadmap.json" },
  "prerequisite_topics": [{ "topic_id": "java-variables-and-data-types", "title": "Variables and Data Types" }],
  "audience_level": "beginner",
  "desired_reel_length": 45,
  "current_memory_state": { "...": "matching entry from memory.topics, or absent if never covered" },
  "recent_performance_notes": null
}
```

## 4. Research Process (Conceptual)

No implementation exists yet; this describes the intended behavior, not code:

1. **Ground in the curriculum first.** Start from `curriculum_context` — the topic's `learning_objective`, `common_mistakes`, and `example_ideas` are the seed, not a blank slate.
2. **Cross-check against trustworthy references.** Facts and nuances beyond what the curriculum already captures should be validated against official Java documentation (the JLS, Oracle's Java SE docs), other trusted technical references, and any prior internally validated research for related topics — see Section 6.
3. **Distill, don't dump.** The output is a brief, not a research report. Anything that doesn't directly serve one of the brief's fields (Section 5) is discarded rather than included "just in case."
4. **Incorporate memory context.** If `current_memory_state` shows this topic was previously posted and underperformed, or was flagged `needs_revisit`, the brief must reflect that — e.g. resequencing `core_concepts`, adding a `safety_notes` entry for a previously-made mistake, or noting the change in `confidence_notes`.
5. **Flag uncertainty instead of guessing.** Where a fact can't be confidently validated, it belongs in `confidence_notes.flagged_uncertainties`, not in `core_concepts` or `beginner_explanation` dressed up as settled.

## 5. Output: The Topic Brief

Full field-level contract: [research-output-schema.json](research-output-schema.json). Summary of what each field is for and which downstream artifact it feeds:

| Field | Feeds | Purpose |
| --- | --- | --- |
| `topic_id`, `topic_title` | all stages | Identity and traceability back to the curriculum. |
| `topic_summary` | hook | One to three plain-language sentences on what the topic is and why it matters — the seed for the reel's opening line. |
| `learning_objective` | lesson, review checklist | The single outcome the reel must deliver; mirrors the curriculum entry. |
| `difficulty`, `audience_level`, `target_reel_length_seconds` | lesson, storyboard | Constraints the rest of the pipeline must respect. |
| `prerequisites` | lesson | What can be assumed already known — the lesson should build on these, not re-teach them. |
| `common_misconceptions` | hook, review checklist | What beginners get wrong; often the sharpest hook material ("you probably think X — you're wrong"). |
| `core_concepts` | short lesson, storyboard | Ordered list of the ideas the reel must cover, already sequenced for teaching. |
| `simple_analogy` | hook, short lesson | One validated, concrete analogy to anchor the explanation. |
| `code_example_ideas` | code example | Pre-selected example concepts, refined from the curriculum's `example_ideas`. |
| `beginner_explanation` | short lesson | A plain-language explanation the Lesson Generator can adapt into narration directly. |
| `advanced_notes` | review checklist | Correctness nuances beyond the beginner explanation — not meant to be spoken, but used to confirm the simplified version didn't become false. |
| `related_topics` | storyboard, future recommendation | Mirrors curriculum `related_topics`; useful for callbacks or a closing "next up" line. |
| `safety_notes` | review checklist | Statements that must **not** be said, because they're technically wrong or misleading — the primary hallucination guardrail. |
| `confidence_notes` | review checklist | Where the research stage wasn't fully certain, so review attention goes to the right place. |
| `sources` | review checklist (future) | Reserved for citation tracking once external source-fetching exists. Empty in Phase 1. |
| `research_version`, `generated_at` | caching (future) | Traceability and staleness detection once research caching exists. |

## 6. Trustworthy Factual Grounding

Phase 1 has no external source-fetching implementation. The design assumes that when one exists, it will draw from, in order of trust:

1. **Official Java documentation** — the Java Language Specification and Oracle's Java SE API/tutorial docs.
2. **Other trusted technical references** — established, widely-used Java reference material.
3. **Prior validated internal knowledge** — research briefs and reviewer-approved content ForgeBrain has already produced for related topics.

Until that fetching layer exists, research briefs are produced from the curriculum's existing `common_mistakes`/`example_ideas` seed content plus general Java knowledge, with `confidence_notes` used honestly to flag anything not fully grounded. The `sources` field exists now specifically so that behavior doesn't change shape later — see Section 8.

## 7. Keeping the Brief Lesson-Ready

A topic brief is not a study guide. Practical caps that keep it short enough to feed directly into the Lesson Generator without further editing:

- `core_concepts`: 3-5 items, hard cap. A 30-45 second reel cannot responsibly carry more.
- `code_example_ideas`: 2-4 items — options for the Lesson/Script stage to choose from, not a menu of everything possible.
- `common_misconceptions`: 2-4 items — the sharpest ones, not an exhaustive list.
- `advanced_notes` and `safety_notes`: as many as genuinely needed, but each entry must be one sentence. These are reference material for the Reviewer, not narration.
- `beginner_explanation`: short enough to read aloud in under 20 seconds — a paragraph, not a page.

If a topic genuinely can't fit these caps, that's a signal the curriculum topic itself is too broad and should be split — a curriculum problem, not a reason to loosen the brief.

## 8. Future Extensibility

Not built in Phase 1, but the schema is shaped so these can be added without changing its structure:

- **Multiple sources** — `sources` is already an array; a real fetching layer just needs to populate it.
- **Source ranking** — `sources[].trust_tier` is already reserved (`official`, `trusted-reference`, `internal-validated`, `unverified`); ranking logic would read this field, not add a new one.
- **Citation tracking** — each `sources` entry reserves `url` and `retrieved_at` for exactly this.
- **Confidence scoring** — `confidence_notes.overall_confidence` is a coarse three-tier value now; a numeric score can be added later without removing the tier (useful for quick human scanning even after numeric scoring exists).
- **Contradiction handling** — `confidence_notes.unresolved_conflicts` is reserved and empty until multiple sources are actually cross-checked against each other.
- **Research caching** — `research_version` and `generated_at` exist so a future cache can key on `topic_id` + curriculum version and know when a cached brief is stale.
- **Topic-specific fact stores** — nothing here precludes a future internal knowledge base being consulted as an additional trusted source in Section 6; it would slot in as another `sources` entry type.

## 9. Edge Cases

- **A prerequisite hasn't actually been posted yet.** The research stage still produces a brief (it doesn't gate on this — that's the topic selector's job, see `topic-selector-spec.md`), but notes the gap in `confidence_notes.flagged_uncertainties` so the Reviewer knows the audience may not have the assumed background.
- **`current_memory_state` shows prior underperformance.** The brief must visibly respond to it — see Section 4, step 4. A revised brief that looks identical to the original is a sign the research stage ignored the feedback.
- **`audience_level` conflicts with the topic's curriculum `difficulty`** (e.g. an `advanced` topic requested at `beginner` audience level). The brief is still produced, but `confidence_notes.flagged_uncertainties` should note the mismatch rather than silently oversimplifying an advanced topic into something misleading.
- **No prior research exists and no caching layer exists yet.** Every run does full research from scratch in Phase 1; this is expected, not a gap to fix now.
- **A topic is revisited with an unchanged curriculum entry.** Once caching exists, this should return the cached brief rather than re-researching identical material — out of scope for Phase 1, noted here so the field reservations in Section 8 aren't accidentally removed later.
