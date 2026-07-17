# Reviewer Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Reviewer: the pipeline's final quality gate, deciding whether a finished `VideoPackage` is approved to move into [Publishing Package](../publishing/publishing-spec.md) preparation, or must be sent back for revision. It sits between the [Renderer](../renderer/render-spec.md) and Publishing Package preparation.

## 1. Position in the Pipeline, and Its Relationship to Earlier Safety Checks

```
Renderer → Reviewer → Publishing Package
```

This is worth being explicit about, because ForgeBrain already has review-like behavior earlier in the pipeline, and the two should not be confused: every stage from Research onward carries a `confidence_notes` field and self-flags uncertainty as it works (`research-spec.md`, `lesson-spec.md`, `content-director-spec.md`, `script-spec.md`, `storyboard-spec.md` all do this). That is **continuous, first-line self-checking** — each stage watching its own work. The Reviewer described here is different: it is the **one authoritative, final gate**, run once, after every other stage — including rendering — has already produced its output. `docs/ARCHITECTURE.md`'s original data-flow description placed a "Review" step before storyboard creation; this document supersedes that placement with the Reviewer positioned after rendering instead, consistent with the pipeline order this architecture pass formalizes — see `docs/PIPELINE.md` for the reconciled, authoritative order.

The distinction matters because a pre-render review can only ever check the *plan*; only a post-render review can check the *actual artifact* — real audio timing, real rendered text legibility, a real finished video file. Both matter, but only one of them is the last checkpoint before a reel could be published.

## 2. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `video_package` | Full `VideoPackage`, matching `render-schema.json` | The finished artifact being reviewed. |
| `quality_score` | Full `QualityScore`, matching `quality-scoring-schema.json` | The six-dimension evaluation this decision weighs — see Section 3. |
| `lesson` | Full lesson blueprint | `safety_notes` — the hard-gate source of truth. |
| `script` | Full script object | `what_to_avoid_saying` — carried directly from the lesson's `safety_notes`, checked against what the script actually says. |
| `memory_state` | The relevant slice of memory for this topic | Whether this is a first pass or a revision already, informing how a `needs_revision` verdict should be phrased. |

## 3. Hard Gates vs. Scored Judgment

The Reviewer's decision combines two fundamentally different kinds of check, and does not treat them the same way:

- **Hard gates** — binary, non-negotiable conditions drawn from `lesson.safety_notes` and `script.what_to_avoid_saying`. If the script contains language matching something explicitly flagged as incorrect (e.g. "int overflow throws an exception," which `research-examples/java-variables-and-data-types.json` explicitly flags as a statement that must never appear), that is an automatic `rejected` verdict, regardless of how well the reel scores everywhere else. `hard_gate_violations` lists these by name; a non-empty list forces `verdict: "rejected"` — this is not a weighted consideration, it's a veto.
- **Scored judgment** — the six-dimension `QualityScore` (`quality-scoring-spec.md`). No single dimension scoring low is automatically disqualifying the way a hard gate violation is; instead, `overall_score` falling below a configured approval threshold, or any individual dimension falling below its own configured floor, produces `verdict: "needs_revision"` rather than `"rejected"` — the reel isn't necessarily *wrong*, it may just not be *good enough yet*.

This separation exists because conflating them would either make every minor quality shortfall as severe as a factual error, or let a low-but-nonzero technical_accuracy score be "averaged away" by strong production polish. Neither is acceptable for a channel whose whole premise is that a wrong explanation is worse than a missed day (`docs/ARCHITECTURE.md` Section 2).

## 4. The Three Verdicts

| Verdict | Meaning | What happens next |
| --- | --- | --- |
| `approved` | No hard gate violations; `overall_score` and every dimension at or above their configured thresholds. | Moves to Publishing Package preparation. |
| `needs_revision` | No hard gate violations, but one or more quality dimensions fell short. | `issues[]` names which dimension(s) and, via `suggested_stage_to_revisit`, which upstream stage's output is the likely place to fix it — e.g. a low `hook_strength` score points back to the Content Director or Script stage, not the Renderer. |
| `rejected` | One or more hard gate violations. | Must restart from at least the Script stage (a hard gate violation is, by definition, something the script said that it should never have said) — `issues[]` names the specific violated `safety_notes` entry. |

## 5. Output

Full field-level contract: [reviewer-schema.json](reviewer-schema.json). `suggested_stage_to_revisit` uses the same pipeline stage names as `docs/PIPELINE.md`, so a `needs_revision` or `rejected` verdict points at something directly actionable rather than a vague "try again."

## 6. Future Extensibility

- **Human-in-the-loop override** — nothing in this schema assumes the Reviewer's verdict is automatically executed; a `needs_revision` or `rejected` verdict is a recommendation a future orchestration layer (or a human) can act on, override, or escalate.
- **Configurable thresholds per topic difficulty** — `quality-scoring-spec.md`'s weights and this stage's approval thresholds are both external configuration, not fixed in either schema, so a harder topic could reasonably have a different bar than a foundational one.
- **Revision-loop tracking** — `memory_state.topics[topic_id].revision_count` (already in `memory-schema.json`) is the natural place a future implementation would track how many times a topic has bounced off this gate, feeding `topic-ranking.json`'s existing `max_revision_count_before_deprioritize` threshold.

## 7. Edge Cases

- **A hard gate violation and a low quality score both apply.** `verdict` is `rejected` — hard gates always take precedence over scored judgment, per Section 3. `issues[]` should still list the quality shortfalls too, so a revision addresses everything at once rather than looping through the gate twice.
- **`quality_score.dimensions.hook_strength` used a neutral default because no AI judgment step was wired up** (see `quality-scoring-spec.md` Section 6). The Reviewer should not treat a neutral default the same as a confidently-measured score when deciding `needs_revision` — `confidence_notes` should reflect that this part of the decision is less certain than the rest.
- **A topic is being reviewed for the second or third time after prior revisions.** `memory_state` makes this visible; `issues[]` should note whether previously-flagged problems were actually fixed this time, not just re-list the same issue generically.
