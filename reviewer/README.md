# Reviewer — The Final Quality Gate

This folder holds ForgeBrain's last pipeline checkpoint: the authoritative decision on whether a finished [`VideoPackage`](../renderer/render-spec.md) is allowed to proceed to [Publishing Package](../publishing/publishing-spec.md) preparation.

| Component | Spec | Schema | Role |
| --- | --- | --- | --- |
| Quality Scoring | [quality-scoring-spec.md](quality-scoring-spec.md) | [quality-scoring-schema.json](quality-scoring-schema.json) | Produces a six-dimension `QualityScore` for every finished reel. Not a decision — an input to one. |
| Reviewer | [reviewer-spec.md](reviewer-spec.md) | [reviewer-schema.json](reviewer-schema.json) | Combines the `QualityScore` with hard safety gates drawn from `lesson.safety_notes` to produce one of three verdicts: `approved`, `needs_revision`, `rejected`. |

Worked examples (including a rejected counter-example) live in [examples.md](examples.md).

## The one rule that matters most here

Hard gates and scored quality are never averaged together. A reel that violates a specific, named `safety_notes` entry is `rejected` regardless of how well it scores everywhere else — see `reviewer-spec.md` Section 3. This is a direct consequence of `docs/ARCHITECTURE.md`'s founding principle that a wrong explanation is worse than a missed day.

## Relationship to earlier "review-like" behavior

Every stage in [`brain/`](../brain/) already self-flags uncertainty via `confidence_notes` as it works. That is continuous first-line checking by each stage on its own output. The Reviewer described here is different and singular: one authoritative pass, run once, after rendering, checking the actual finished artifact rather than a plan for one. See `reviewer-spec.md` Section 1 for why both matter and neither replaces the other.
