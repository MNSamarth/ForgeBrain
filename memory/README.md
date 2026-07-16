# Memory

This folder is ForgeBrain's memory layer. It tracks what the system has already taught, what it's working on now, what's queued next, and how each piece of content actually performed. It is state, not logic — the Curriculum Engine and other components read and update it, but the rules for interpreting it live in code, not here.

This is a schema design, not a database implementation. No storage engine, service, or persistence code is introduced in this phase.

## Why ForgeBrain needs memory

The [curriculum](../curriculum/README.md) defines the full map of Java topics and their prerequisites, but the roadmap alone can't say what's *actually happened* — which topics have been posted, how recently, how well they did, or which one is being worked on right now. Memory is the record of what happened, layered on top of the curriculum's record of what's possible.

Without it, every pipeline run would be stateless: capable of picking a technically valid topic, but blind to its own history.

## How memory helps topic selection

The Curriculum Engine selects the next topic by combining two sources: the roadmap (what's structurally eligible, per prerequisites) and memory (what's actually been covered, and how it went). Memory is what turns "any eligible topic" into "the *right* eligible topic right now" — for example, preferring a topic marked `high` priority, skipping one still inside its `avoid_until` window, or resurfacing one flagged `needs_revisit` because it underperformed and deserves a second attempt.

## How memory prevents repetition

Each topic gets exactly one record in `topics`, keyed by `topic_id`. Every time that topic is used, `times_used` increments and `last_used_at` / `last_posted_at` update. `recently_used_hooks` and `recently_used_examples` go a level deeper: they track not just *which topics* were covered but *which specific hooks and examples* were used, so a topic that's revisited later (e.g. after `avoid_until` passes) doesn't reuse the same opening line or code example verbatim.

## How memory will later support analytics and improvement

Every topic record reserves an `audience_response` block (views, watch time, retention, likes, comments, shares, saves, follower conversion) alongside a computed `performance_score` and `retention_score`. None of this data exists yet — Phase 1 has no publishing or analytics integration — but the fields exist now so that when analytics data starts flowing in, it has a defined place to land without changing the shape of the memory file every other component already reads. Over time, `performance_score` trends across topics are what a future recommendation/ranking engine would use to weight topic selection beyond simple prerequisite order.

## Answering topic decisions

The schema is deliberately shaped so these questions are direct field lookups, not derived computations:

| Question | Where the answer comes from |
| --- | --- |
| What topic should be taught next? | `queue` (ordered, with `priority`), filtered against each candidate's `status` and `avoid_until` |
| What topic was posted most recently? | `global_stats.last_posted_topic_id` / `global_stats.last_posted_at` |
| What topics are overused? | `topics[*].times_used`, sorted descending |
| What topics need a revisit? | `topics[*].status == "needs_revisit"` |
| What topics performed well? | `topics[*].performance_score` / `retention_score`, sorted descending |
| What topics should be delayed? | `topics[*].avoid_until` in the future, or `priority == "low"` |

## Files

- `memory-schema.json` — the JSON Schema (draft-07) defining the shape of a memory state file. This is the contract other components read and validate against.
- `examples/sample-memory-state.json` — a realistic populated memory state after several Java reels, showing posted, in-progress, queued, and revisit-flagged topics together.

## Future extensibility

The schema is intentionally flat and dictionary-keyed (`topics` keyed by `topic_id`) so it maps cleanly onto a future database table (one row per topic, or one row per topic-use event) without restructuring. `audience_response` is already isolated as its own object so it can later be sourced from a separate analytics table/service and merged in, rather than being hand-maintained. Nothing here assumes a specific storage engine, and nothing here decides *how* topic ranking should work — it only guarantees the data a future ranking engine would need is being captured from day one.
