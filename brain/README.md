# Brain — The Decision and Content Pipeline

This folder is ForgeBrain's thinking layer: the sequence of stages that decide *what* to teach and *how* to teach it, from a bare topic selection through to a fully planned, scene-by-scene storyboard. It is the concrete implementation target for the Curriculum Engine, Topic Memory, Research Agent, Lesson Generator, Script Generator, and Storyboard Generator components described in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md), plus the Content Director layer that sits between lesson planning and script writing.

Every stage in this folder is a **decision or planning layer, not a media generator**. Nothing here produces audio, video, or a rendered frame — that begins in [`renderer/`](../renderer/). Everything here produces structured data the next stage consumes programmatically.

## The pipeline, in order

```
Topic Selector → Research → Lesson → Content Director → Script → Storyboard
```

Each stage takes the previous stage's structured output as its primary input, narrows or transforms it, and hands off a new structured artifact — never raw prose, never a shortcut back to an earlier stage's job. See [docs/PIPELINE.md](../docs/PIPELINE.md) for how this connects to the stages beyond `brain/` (voice, subtitles, assets, rendering, review, and publishing).

| Stage | Spec | Schema | Examples | What it decides |
| --- | --- | --- | --- | --- |
| Topic Selector | [topic-selector-spec.md](topic-selector-spec.md) | [topic-selector-schema.json](topic-selector-schema.json) | [examples/sample-decisions.json](examples/sample-decisions.json) | Which curriculum topic gets produced next, and why. |
| Research | [research-spec.md](research-spec.md) | [research-output-schema.json](research-output-schema.json) | [research-examples/](research-examples/) | The factual and pedagogical grounding for the selected topic. |
| Lesson | [lesson-spec.md](lesson-spec.md) | [lesson-output-schema.json](lesson-output-schema.json) | [lesson-examples/](lesson-examples/) | The single concept, example, and takeaway this reel teaches. |
| Content Director | [content-director-spec.md](content-director-spec.md) | [content-director-schema.json](content-director-schema.json) | [content-director-examples.md](content-director-examples.md) | The hook, pacing, tone, and visual strategy — how to present the lesson. |
| Script | [script-spec.md](script-spec.md) | [script-output-schema.json](script-output-schema.json) | [script-examples.md](script-examples.md) | The actual spoken words and on-screen text. |
| Storyboard | [storyboard-spec.md](storyboard-spec.md) | [storyboard-output-schema.json](storyboard-output-schema.json) | [storyboard-examples.md](storyboard-examples.md) | The scene-by-scene visual plan a renderer can execute. |

`topic-ranking.json` is a supporting file for the Topic Selector, not a pipeline stage of its own — it's the scoring model `topic-selector-spec.md` references, kept separate so the algorithm and the tunable weights aren't tangled together.

## How it uses curriculum and memory

Every stage in this pipeline ultimately traces back to two sources of truth that never get bypassed:

- **`curriculum/java-roadmap.json`** — the structural truth: what topics exist, their difficulty, and their prerequisite relationships. This defines what is *possible*.
- **A memory state matching `memory/memory-schema.json`** — the historical truth: what's been posted, what's in progress, queued, or flagged for revision, and how each topic performed. This defines what's *actually happened*.

The Topic Selector reconciles both directly. Later stages (Research, Lesson, Content Director, Script, Storyboard) mostly consume the *output of the previous stage*, but still reach back to `memory_state` when a topic is a revision — see the Arrays worked examples threaded through `research-examples/`, `lesson-examples/`, `content-director-examples.md`, `script-examples.md`, and `storyboard-examples.md`, which all carry the same prior-underperformance context forward and visibly respond to it at every stage.

## Why this is split into six stages instead of one

A single prompt from "topic" to "finished storyboard" would collapse every distinct judgment call — what's true, what's worth teaching, how to hook attention, what words to use, what the viewer sees — into one unreviewable step. Splitting them means:

- Each stage's output can be validated independently (every schema in this folder is a closed contract with `additionalProperties: false`).
- A failure is traceable to exactly one stage, not a guess across an entire generation.
- A stage can be revised or re-run (e.g. a topic flagged for revision gets fresh Research, Lesson, and Script output) without discarding work from stages that were already correct.

## How it supports consistency and avoids repetition

Every decision any stage in this folder makes is deterministic and reconstructable from its inputs. Repetition is avoided at the Topic Selector layer specifically: a hard cooldown gate (`avoid_until`) removes a topic from consideration entirely for a period, and a softer `repetition_penalty` scoring factor keeps recently- or frequently-used topics ranked lower even after cooldown ends. Later stages don't re-decide *what* topic to cover — they only decide how to teach the topic they were handed, which is what keeps six stages from drifting out of sync with each other.
