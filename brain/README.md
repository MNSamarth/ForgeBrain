# Brain — Topic Selector

This folder is ForgeBrain's decision layer. It is the concrete implementation target for the **Curriculum Engine** and **Topic Memory** components described in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md): given the curriculum roadmap and the current memory state, it decides which single Java topic should be produced next, and states why.

The topic selector is a decision layer, not a content generator. It never writes a lesson, a script, or a line of narration — it outputs one structured decision object naming a topic that already exists in `curriculum/java-roadmap.json`, and that decision becomes the input to the Lesson Generator stage.

## How it uses curriculum and memory

The selector reads two existing sources and never invents a third:

- **`curriculum/java-roadmap.json`** — the structural truth: what topics exist, their difficulty, and their prerequisite relationships. This defines what is *possible*.
- **A memory state matching `memory/memory-schema.json`** — the historical truth: what's been posted, what's in progress, what's queued, how each topic performed, and what's on cooldown. This defines what's *actually happened*.

Neither source is sufficient alone. The roadmap can't say a topic was already covered twice this month; memory can't say a topic's prerequisites haven't been taught yet. The selector's entire job is reconciling the two into one defensible choice.

## Why this has to exist before script generation

Script generation needs a committed topic to work from — it can't reasonably start until something has decided *what* is being taught. Without a dedicated selection step, that decision would either be made ad hoc inside the script generator (mixing two concerns) or left to chance (an arbitrary or repeated topic slipping through). Putting selection in its own stage, with its own explainable output, means every downstream stage can trust that the topic it received was chosen deliberately and can be audited after the fact.

## How it supports consistency and avoids repetition

Every decision the selector makes is deterministic and reconstructable from its inputs — see [topic-selector-spec.md](topic-selector-spec.md) for the full logic and [topic-ranking.json](topic-ranking.json) for the scoring model. Repetition is avoided at two layers: a hard cooldown gate (`avoid_until`) that removes a topic from consideration entirely for a period, and a softer `repetition_penalty` scoring factor that keeps recently- or frequently-used topics ranked lower even once their cooldown has passed. Consistency comes from the same scoring model being applied the same way every run — the selector doesn't get "creative" about topic choice; that's deliberate.

## Files

- `topic-selector-spec.md` — the full selection algorithm: candidate pool construction, gates, scoring, modes, tie-breaking, the output schema, and edge cases.
- `topic-ranking.json` — the scoring model itself: factor definitions, per-mode weight profiles, thresholds, and tie-breakers, as structured data other components can read directly.
- `examples/sample-decisions.json` — worked examples showing the selector's output for each decision mode, computed against `memory/examples/sample-memory-state.json`.
