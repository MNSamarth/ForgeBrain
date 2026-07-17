# Topic Selector Specification

Status: Design specification — no implementation yet.

This document describes the algorithm the topic selector uses to pick the next Java topic. The scoring model it references lives in [topic-ranking.json](topic-ranking.json); worked examples live in [examples/sample-decisions.json](examples/sample-decisions.json).

## 1. Inputs

The selector takes exactly two inputs, both already defined elsewhere in the repo:

- **Curriculum roadmap** — `curriculum/java-roadmap.json`. Provides, for every topic: `id`, `level`, ordering position, `difficulty`, `prerequisites`, `next_topics`, `related_topics` (once populated).
- **Memory state** — an object matching `memory/memory-schema.json`. Provides, for every tracked topic: `status`, `times_used`, `last_used_at`, `last_posted_at`, `revision_count`, `performance_score`, `retention_score`, `priority`, `avoid_until`, plus the global `current_topic_id` and `queue`.

A curriculum topic with no corresponding entry in `memory.topics` is treated as `status: "not_covered"` with no usage history — this is the default state for a topic that has never been touched.

The selector also takes one **mode** parameter (`next_topic`, `revision_topic`, `high_performance_topic`, or `gap_filler_topic`) and a **selection_timestamp**, which defaults to "now" but is explicit so decisions are reproducible.

The selector never introduces a topic that isn't in the curriculum roadmap. Every `selected_topic_id` it can possibly output already exists in `curriculum/java-roadmap.json`.

## 2. Candidate Pool Construction

For the requested mode, the selector first builds a **candidate pool**: every curriculum topic whose combined roadmap + memory state matches that mode's `candidate_pool` rule in `topic-ranking.json`.

Every topic in the curriculum is then run through the three gates, in order:

1. **Not already in progress** — if the topic equals `memory.current_topic_id` and its status is `in_progress`, it is dropped silently (it isn't a rejection, it's already committed).
2. **Readiness** — every id in the topic's `prerequisites` must have `status == "posted"` in memory. A topic that fails this check is recorded in `rejected_topics` and its id is added to `blocked_by_prerequisite`, *even if it is currently sitting in `memory.queue`*. Being queued does not exempt a topic from prerequisite validation — the selector re-validates every candidate on every run.
3. **Cooldown** — if `avoid_until` is set and is on or after `selection_timestamp`, the topic is recorded in `rejected_topics` and its id is added to `blocked_by_recent_use`.

Whatever remains after all three gates is the scored candidate set for that mode.

## 3. Scoring

Every surviving candidate is scored using the mode's weight profile from `topic-ranking.json`:

```
score = Σ (factor_value × factor_weight)   across all six factors for that mode
```

Factor values (`educational_sequence_fit`, `novelty_score`, `repetition_penalty`, `performance_boost`, `revision_priority`, `audience_demand_signal`) are each computed as described in `topic-ranking.json`. A mode that assigns a factor a weight of `0.0` still computes the value (for transparency in the output) but it does not influence the score.

The candidate with the highest score is `selected_topic_id`. All scored candidates — selected or not — appear in `candidate_topics` with their individual factor breakdown, so the choice can be audited.

## 4. Decision Modes

| Mode | Purpose | Typical trigger |
| --- | --- | --- |
| `next_topic` | Normal forward progression through the curriculum. | Default mode for every regular production cycle. |
| `revision_topic` | Re-teach a topic that underperformed. | A topic's `status` is `needs_revisit` and its cooldown has expired. |
| `high_performance_topic` | Ride momentum from a topic that performed unusually well by teaching something closely related next. | A posted topic's `performance_score` crosses `thresholds.high_performance_threshold`. |
| `gap_filler_topic` | Surface eligible content the audience seems to want but that isn't queued. | An external signal (future audience feedback) flags demand for an uncovered, unqueued topic. |

Only one mode runs per selector invocation. Choosing *which* mode to run is a decision made by the caller (e.g. the orchestration layer described in `docs/ARCHITECTURE.md`), not by the selector itself — the selector answers "what's the best topic for this mode," not "which mode should run right now." That trigger logic is intentionally out of scope for this first version; see Section 7.

## 5. Determinism and Tie-Breaking

Given the same curriculum file, the same memory state, the same mode, and the same `selection_timestamp`, the selector always produces the same decision. There is no randomness anywhere in the algorithm.

If two or more candidates end up with an equal score (to a defined precision, e.g. three decimal places), ties are broken in this fixed order:

1. The candidate closer to the curriculum's current frontier (smaller `educational_sequence_fit` distance) wins.
2. If still tied, the candidate with the alphabetically lower `topic_id` wins.

This guarantees a single, reproducible answer every time, and the tie-break path itself is visible in `reason` when it was the deciding factor.

## 6. Output: The Decision Object

Full field-level contract: [topic-selector-schema.json](topic-selector-schema.json). Every selector run produces exactly one decision object, regardless of whether a topic was actually selected:

| Field | Type | Description |
| --- | --- | --- |
| `mode` | string | Which of the four modes produced this decision. |
| `selected_topic_id` | string \| null | The chosen topic's id, or `null` if no eligible candidate existed. |
| `selected_topic_title` | string \| null | The chosen topic's title, for readability. |
| `reason` | string | A plain-language explanation of why this topic won — or, if `selected_topic_id` is `null`, why nothing qualified. |
| `score` | number \| null | The selected topic's final weighted score. |
| `candidate_topics` | array | Every topic that passed all gates, each with its id, title, final score, and per-factor breakdown. |
| `rejected_topics` | array | Every topic considered and excluded, each with its id, title, and a short reason. |
| `blocked_by_prerequisite` | array of strings | topic_ids excluded specifically because a prerequisite wasn't yet posted. |
| `blocked_by_recent_use` | array of strings | topic_ids excluded specifically because of an active cooldown. |
| `needs_revision` | array of strings | topic_ids currently at status `needs_revisit`, regardless of whether this run's mode acted on them — surfaced for visibility in every mode's output. |
| `selection_timestamp` | string (ISO date-time) | When this decision was computed. |

A `null` selection is a valid, complete answer — not an error. See Section 8.

## 7. Combining Curriculum and Memory — Explicitly

Every gate and every scoring factor reads from both sources at once; neither is sufficient alone:

- `prerequisites` (curriculum) is checked against `status` (memory) — structure meets history.
- `educational_sequence_fit` (curriculum position) is weighed against `novelty_score` and `repetition_penalty` (memory usage) — where the topic sits in the roadmap is balanced against whether it's actually still fresh.
- `related_topics` (curriculum) is used to look up `performance_score` (memory) on already-posted neighbors, to compute `performance_boost` for a topic that hasn't been posted yet.

No factor is computed from curriculum or memory in isolation; the selector's entire value is in that reconciliation.

## 8. Edge Cases

- **No candidates survive the gates.** `selected_topic_id` is `null`, `score` is `null`, `candidate_topics` is empty, and `reason` explains which gate emptied the pool (e.g. "All not_covered topics are blocked by unmet prerequisites" or "The only needs_revisit topic is still in its cooldown window until 2026-08-01"). This is expected and correct behavior, not a failure state.
- **A queued topic fails the readiness gate.** It appears in `rejected_topics` and `blocked_by_prerequisite` even though it was in `memory.queue`. The queue is a hint, not a guarantee — the selector is the final authority on eligibility.
- **A topic is in progress.** It is excluded from every mode's candidate pool without appearing in `rejected_topics`, since it isn't being turned down — it's simply already committed elsewhere in the pipeline.
- **`gap_filler_topic` runs with no audience data.** Because `audience_demand_signal` defaults to `0.0` in Phase 1, this mode will typically select nothing or select weakly. The output's `reason` should say so explicitly (e.g. "No audience demand signal available yet; gap_filler_topic cannot differentiate candidates beyond curriculum order") rather than silently behaving like `next_topic`.
- **A `needs_revisit` topic has been revised the maximum number of times** (`revision_count >= thresholds.max_revision_count_before_deprioritize`). It remains eligible but its `revision_priority` factor is reduced, so it will naturally lose to a fresher revision candidate if one exists, rather than being hard-excluded.
