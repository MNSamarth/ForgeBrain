# Analytics — The Feedback Loop (Not Active in Phase 1)

This folder designs, but does not activate, ForgeBrain's performance feedback loop. Per `docs/ARCHITECTURE.md` Section 10, analytics dashboards and live tracking are explicitly out of Phase 1 scope — this is architecture prepared ahead of need, not a running system.

| File | Purpose |
| --- | --- |
| [analytics-spec.md](analytics-spec.md) | Why this exists now despite being inactive, and — most importantly — a table mapping every field this component populates back to the exact spot in `brain/`, `memory/`, and `reviewer/` that already reserved space for it. |
| [analytics-schema.json](analytics-schema.json) | `PerformanceSnapshot` (per-reel metrics) and `StrategyPerformanceAggregate` (metrics grouped by strategy choice across topics). |

## Why this folder is worth reading even though nothing in it runs yet

Every other stage in this pipeline — the Content Director, the Script Generator, the Storyboard Generator, Quality Scoring — already wrote a "Future Extensibility" section pointing at data it doesn't have yet. This is that data's actual, designed home. Reading `analytics-spec.md` Section 3 alongside those sections turns five separate "someday" notes into one coherent, already-connected plan.
