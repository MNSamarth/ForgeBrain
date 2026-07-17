# Analytics Specification

Status: Design specification — no implementation yet. **Not active in Phase 1** (`docs/ARCHITECTURE.md` Section 10 explicitly excludes analytics dashboards and live tracking). This document designs the architecture ahead of need, the same way every earlier stage reserved fields for data it couldn't yet populate — see Section 3.

This document describes ForgeBrain's Analytics component: the layer that captures real audience performance data once a reel is published, writes it back into Memory, and aggregates it by strategy dimension for every earlier stage that already reserved a place for exactly this data.

## 1. Why This Exists Ahead of Need

Nothing in this pipeline can learn from outcomes yet — every decision from topic selection through review is made from static rules, curriculum structure, and per-topic history, never from "did this actually work." Designing Analytics now, even with nothing to analyze yet, means every upstream stage's forward-looking fields (Section 3) were reserved against a concrete, specific plan rather than a vague promise. This is architecture for a capability the system doesn't have yet, not a working feedback loop — that distinction matters and is stated plainly here rather than implied.

## 2. Position in the Pipeline

Analytics does not sit in the linear `Curriculum → ... → Publishing Package` pipeline (`docs/PIPELINE.md`) — it runs *after* a `PublishingPackage` has actually been posted somewhere, which Phase 1 never does (see `publishing-spec.md` Section 1). It is better understood as a **feedback loop** that closes back onto Memory and the Topic Selector, not a forward pipeline stage:

```
Publishing Package → (real-world posting, out of Phase 1 scope) → Analytics → Memory → Topic Selector
```

## 3. Every Reserved Hook This Component Closes

This is not the first time this data has been designed for — it's the fourth or fifth. Analytics is the component that finally populates what every earlier stage already left space for:

| Where it was reserved | What it's waiting for |
| --- | --- |
| `memory-schema.json` → `topics[].audience_response` | `view_count`, `watch_time_seconds`, `average_retention_percent`, `likes`, `comments`, `shares`, `saves`, `follower_conversion` — all already-defined fields, all `null` until now. |
| `memory-schema.json` → `topics[].performance_score` / `retention_score` | Computed from the above, not hand-set. |
| `brain/topic-ranking.json` → `audience_demand_signal` factor | Explicitly documented as "not yet populated in Phase 1" — this is exactly the signal Analytics produces. |
| `brain/content-director-spec.md` Section 8 | `strategy_performance`, aggregated by `hook_type`/`teaching_style`/etc. across topics, not just per-topic — see Section 5 below. |
| `brain/script-spec.md` Section 10 | "Audience feedback adaptation... mirrors the Content Director's own `strategy_performance`." |
| `brain/storyboard-spec.md` Section 11 | "Visual analytics feedback... once performance is tracked per `scene_type`/`visual_style`/`animation_style`." |
| `reviewer/quality-scoring-spec.md` Section 5 | "Analytics-informed weighting" for the six scoring dimensions. |

Every one of these was written with this exact component in mind, before this component existed. Reading `analytics-schema.json` alongside those sections should make each reserved field's purpose concrete rather than aspirational.

## 4. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `publishing_package` | Full `PublishingPackage` | Identifies which topic and which specific reel a performance snapshot belongs to. |
| Raw platform metrics | An external analytics API (e.g. YouTube Analytics, platform-native insights) — **not designed here**; Phase 1 has no publishing integration to pull metrics from in the first place. | The actual view/watch-time/engagement numbers. |

## 5. Output: Two Shapes, Two Purposes

Full field-level contract: [analytics-schema.json](analytics-schema.json), which defines two related but distinct objects:

- **`PerformanceSnapshot`** — one reel's real metrics at a point in time. This is what gets written back into `memory.topics[topic_id].audience_response` and used to compute `performance_score`/`retention_score`.
- **`StrategyPerformanceAggregate`** — metrics grouped **across topics** by a single strategy dimension and value (e.g. every reel that used `hook_type: "myth"`, regardless of topic). This is the shape `content-director-spec.md`, `script-spec.md`, and `storyboard-spec.md` all specifically need, and which no per-topic memory record can answer on its own — memory tracks performance *per topic*, never *per strategy choice across topics* (this exact gap is called out explicitly in `content-director-spec.md` Section 8).

## 6. Future Extensibility

- **Automated memory updates** — a `PerformanceSnapshot` is designed to map directly onto `memory.topics[topic_id].audience_response` and trigger a `performance_score`/`retention_score` recompute, once a real write path exists.
- **Feeding `topic-ranking.json`'s `audience_demand_signal`** — `StrategyPerformanceAggregate`, grouped by topic-adjacent signals (e.g. related-topic engagement), is the natural source once that factor stops defaulting to `0.0`.
- **Confidence-weighted aggregates** — `StrategyPerformanceAggregate.sample_size` already exists specifically so a future consumer can discount an aggregate backed by only 2 reels versus one backed by 200, rather than treating every aggregate as equally reliable.

## 7. Edge Cases and Limitations

- **No publishing integration exists in Phase 1.** This entire component has no real data source yet — every field in `analytics-schema.json` is correct in shape and currently unpopulatable in practice. This is stated as a known, tracked limitation (`TODO.md`), not hidden behind the schema's completeness.
- **A `StrategyPerformanceAggregate` with a very small `sample_size`.** Should not be treated as equivalent confidence to a large one — see Section 6. This spec does not fix a minimum sample size threshold; that's a tuning decision for whoever eventually builds the consuming logic in the Content Director/Script/Storyboard stages.
- **Metrics from different platforms aren't directly comparable** (e.g. "retention" is defined differently across platforms). `PerformanceSnapshot` does not attempt cross-platform normalization — that would be inventing a methodology decision this document has no basis to make yet.
