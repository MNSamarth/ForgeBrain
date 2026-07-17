# ForgeBrain Architecture

Status: Planning document — Phase 1
Scope: Defines the end-to-end system design before implementation begins.

## 1. Overview

ForgeBrain is an AI content operating system. It does not simply generate video clips — it plans what to teach, remembers what it has already taught, generates the underlying lesson before touching video, checks its own work, and only then renders a finished vertical reel.

The problem it solves: producing a steady stream of educational short-form video is normally bottlenecked by a human doing four jobs at once — curriculum planning, scriptwriting, editing, and quality control. ForgeBrain splits those jobs into discrete, automatable stages so that content quality does not depend on one person's daily bandwidth, and so each stage can be improved independently over time.

ForgeBrain is content-domain agnostic by design, but Phase 1 runs a single domain end to end: **Java**.

## 2. Product Goal

The immediate goal is to produce premium, faceless Java reels with as little manual intervention as possible, while keeping quality high enough to publish without a human rewrite pass.

Concretely:

- Produce faceless, vertical, short-form Java educational reels.
- Automate topic selection so no human has to decide "what do we teach next."
- Hold output quality above speed — a wrong or shallow explanation is worse than a missed day.
- Support scaling to a cadence of 4 reels per day once the single-reel path is proven reliable.

Scaling to 4/day is a target for a later phase, not a Phase 1 requirement. Phase 1 must prove the pipeline works correctly for one reel before volume is considered.

## 3. Design Principles

- **One language first.** Java only, until the pipeline has proven itself. No multi-language abstraction until there is a second language to abstract for.
- **Curriculum driven content.** Topics come from a structured curriculum, not ad hoc prompting or trending-topic scraping.
- **Lessons before reels.** A reel is a rendering of a lesson, never a direct topic-to-video shortcut. The lesson is the source of truth; the reel is a derived artifact.
- **Modular components.** Each pipeline stage (research, script, review, render) is a separate, independently replaceable unit with a defined input and output.
- **Replaceable AI providers.** No component should hard-depend on one specific model or vendor. Prompts and orchestration logic are decoupled from any single provider's API.
- **Quality over speed.** Every stage that can introduce error (research, script, review) is treated as a checkpoint, not a pass-through.
- **Structured outputs over freeform text.** Every AI stage returns structured data (JSON-shaped) that the next stage consumes programmatically, not prose that has to be re-parsed or re-interpreted.

## 4. System Architecture

> **This section is superseded by [`docs/PIPELINE.md`](PIPELINE.md), the authoritative, current description of all fourteen stages.** It's kept here, updated, because it's part of this document's original narrative — but treat `docs/PIPELINE.md` as the source of truth if the two ever disagree, and its own field-level schemas (linked from there) as the source of truth over either document's prose.

At a high level, ForgeBrain is a pipeline of specialized components, each with one job:

- **Curriculum Engine** — Owns the structured map of what Java topics exist, their order, and their dependencies. Decides what "the next topic" means in context. (`curriculum/`)
- **Topic Memory** — Tracks what has already been covered, when, and how it performed. Prevents repetition and informs the Curriculum Engine's next choice. (`memory/`)
- **Research Agent** — Gathers and verifies the factual/technical grounding for a chosen topic before any teaching content is written. (`brain/research-spec.md`)
- **Lesson Generator** — Turns a topic plus research into a structured lesson: the core explanation, examples, and teaching sequence. (`brain/lesson-spec.md`)
- **Content Director** — Decides how a lesson should be taught (hook, pacing, tone, visual strategy) before any dialogue is written. (`brain/content-director-spec.md`)
- **Script Generator** — Converts a lesson and a content strategy into a short-form video script: narration lines, timing beats, and on-screen text cues. (`brain/script-spec.md`)
- **Storyboard Generator** — Breaks the script into discrete visual scenes/shots: what appears on screen for each narration segment. (`brain/storyboard-spec.md`)
- **Voice Generation** — Synthesizes narration audio and establishes the real, measured timing that supersedes every estimate made upstream. (`renderer/voice-spec.md`)
- **Subtitle Generation** — Produces final captions reconciled against real audio timing. (`renderer/subtitle-spec.md`)
- **Asset Management** — Resolves abstract style choices into concrete fonts, color themes, music, and brand assets. (`renderer/asset-management-spec.md`)
- **Renderer** — Turns the storyboard, narration audio, subtitles, and resolved assets into a finished vertical video file. (`renderer/render-spec.md`)
- **Reviewer** — The pipeline's final quality gate: combines hard safety checks with scored quality judgment to approve, request revision, or reject a finished video. Runs *after* rendering, not before — see `docs/PIPELINE.md` Section 2 for why this supersedes this document's original pre-storyboard placement. (`reviewer/reviewer-spec.md`, `reviewer/quality-scoring-spec.md`)
- **Publishing Package** — Packages an approved reel with the metadata a future publishing step will need (title, description, thumbnail frame). Does not post anywhere in Phase 1. (`publishing/publishing-spec.md`)
- **Analytics and Feedback Loop** — Captures performance data on published reels and feeds it back into Topic Memory to influence future topic and content decisions. Not active in Phase 1. (`analytics/analytics-spec.md`)

## 5. Data Flow

See [`docs/PIPELINE.md`](PIPELINE.md) Section 1 for the full, authoritative fourteen-stage sequence and Section 4 for the "estimate, then reality" throughline connecting Script through Rendering. In brief: topic selection and research ground the reel in curriculum and fact; lesson and content strategy decide what's taught and how; script and storyboard commit that to words and a visual plan; voice, subtitles, assets, and rendering turn the plan into a real video; the Reviewer gates the finished artifact; Publishing Package bundles what's approved. Analytics closes the loop back to Memory once a reel is actually posted somewhere — which Phase 1 never does.

## 6. Repository Structure

```text
forgebrain/
├── brain/          # Decision layer: topic selection, research, lesson, content strategy, script, storyboard
├── renderer/        # Production layer: voice, subtitles, asset resolution, rendering
├── reviewer/        # Final quality gate: quality scoring and review verdicts
├── publishing/       # Bundles an approved reel for a future publishing step
├── analytics/        # Performance feedback loop design (not active in Phase 1)
├── backend/         # Spring Boot project structure: interfaces, models, config placeholders
├── curriculum/       # Curriculum structure: topic definitions, ordering, dependencies
├── memory/          # Topic memory and content memory state
├── prompts/         # Individual prompt templates, one per pipeline stage (not yet populated)
├── assets/          # Static assets: fonts, brand elements, background music, code themes (not yet populated)
├── docs/           # Design and planning documentation
├── scripts/         # Developer and automation scripts (not yet populated)
└── .github/         # Repository configuration (workflows, templates)
```

Each folder maps to one or more stages or supporting concerns in the architecture above — no folder exists without a corresponding component in Section 4. See the root `README.md` for a one-line purpose per folder.

## 7. Data Models

The illustrative JSON this section originally contained (written before any real schema existed) has been superseded and removed to avoid drift — it no longer matched the field names, types, or structure of the schemas actually built, which is exactly the kind of inconsistency this document should not carry. Every data model is now formally defined as a JSON Schema (draft-07), each with its own worked examples:

| Model | Authoritative schema |
| --- | --- |
| Topic (curriculum) | `curriculum/java-roadmap.json` |
| Memory State | `memory/memory-schema.json` |
| Topic Selection Decision | `brain/topic-selector-schema.json` |
| Research Result | `brain/research-output-schema.json` |
| Lesson | `brain/lesson-output-schema.json` |
| Content Strategy | `brain/content-director-schema.json` |
| Script | `brain/script-output-schema.json` |
| Storyboard / Scene | `brain/storyboard-output-schema.json` |
| Voice Result | `renderer/voice-schema.json` |
| Subtitle Result | `renderer/subtitle-schema.json` |
| Asset Manifest | `renderer/asset-management-schema.json` |
| Render Job / Video Package | `renderer/render-schema.json` |
| Quality Score | `reviewer/quality-scoring-schema.json` |
| Review Result | `reviewer/reviewer-schema.json` |
| Publishing Package | `publishing/publishing-schema.json` |
| Analytics (not active) | `analytics/analytics-schema.json` |

`backend/src/main/java/com/forgebrain/backend/models/` mirrors every one of these as a Java record — see `backend/README.md` Section on fidelity for how closely.

## 8. AI Prompting Strategy

No single prompt attempts to go from "topic" to "finished script." Each pipeline stage that involves an AI call uses its own narrowly-scoped prompt, so failures are isolated and each stage's output can be validated independently:

- **Planner prompt** — Given curriculum state and topic memory, selects the next topic and states why.
- **Research prompt** — Given a topic, produces verified technical grounding: definitions, correct behavior, common misconceptions to avoid.
- **Teaching prompt** — Given research, produces the structured lesson: what to teach and in what order.
- **Review prompt** — Given a lesson/script, checks for technical accuracy, clarity, and pacing, and returns a verdict plus specific issues.
- **Storyboard prompt** — Given an approved script, breaks it into discrete visual scenes with a description of what should appear on screen for each.

Each prompt takes structured input and returns structured output (JSON), so downstream stages consume data directly rather than re-interpreting prose.

## 9. Rendering Strategy

Rendering focuses on making short, vertical Java content easy to follow at a glance:

- **Readability** — Text on screen sized and timed for a vertical, sound-optional viewing context; no dense paragraphs on screen at once.
- **Motion** — Purposeful, minimal motion that directs attention (e.g., highlighting the line of code being discussed) rather than decorative animation.
- **Subtitles** — Narration is subtitled by default, synced to the script's timing beats.
- **Code presentation** — Code snippets rendered with syntax highlighting and only the relevant lines emphasized, not entire files.
- **Brand consistency** — A consistent visual identity (fonts, colors, code theme) applied uniformly across reels via shared templates.
- **Modular templates** — Scene types (title card, code explanation, comparison, summary) are reusable templates the Storyboard Generator selects from, not one-off designs per reel.

## 10. Phase 1 Scope

**Included in Phase 1:**

- Repository scaffold (complete).
- This architecture document, and the fourteen-stage pipeline it now points to in `docs/PIPELINE.md` (complete).
- Curriculum structure for Java (topic definitions and ordering) — complete.
- Memory model (structure for tracking covered topics) — complete.
- A fully specified architecture (spec + JSON Schema + worked examples) for every pipeline stage: topic selection, research, lesson, content direction, script, storyboard, voice, subtitles, assets, rendering, review, quality scoring, and publishing preparation — complete.
- A Spring Boot backend project structure mirroring every stage's schema as an interface and domain model — complete, but **contains no business logic, no AI provider integration, and no working implementation of any stage**. See `TODO.md`.
- One complete reel generation path, implemented end to end for a single topic, is **not yet complete** — the architecture for it is finished; the code that executes it is not.

**Explicitly not included in Phase 1:**

- Auto-posting to social media platforms.
- Analytics *dashboards* or any live metrics collection. Note the distinction from `analytics/`: that folder's *architecture* was designed in this pass specifically because every other stage already reserved fields for the data it would produce (see `analytics/analytics-spec.md` Section 3) — designing the shape of a capability ahead of need is not the same as activating it, and nothing in `analytics/` runs or collects anything.
- Multi-language support (any language other than Java).
- Advanced A/B testing of content variants (though `variant_id` fields are already reserved in `script-output-schema.json` for exactly this, unpopulated).
- Scaling infrastructure for high-volume (multi-reel-per-day) production, and no deployment infrastructure (Docker, Kubernetes, CI/CD) of any kind — see `backend/README.md`.

Phase 1 succeeds when one topic can move through the entire pipeline and produce a finished, correctly rendered reel without manual intervention at any stage. The architecture for that is now fully designed; implementing it is the next phase of work — see Section 12 and `TODO.md`.

## 11. Risks and Tradeoffs

- **Repetitive content** — Without strict topic memory, the system may re-teach the same concept in different words. Mitigated by the Curriculum Engine + Topic Memory pairing, but the memory model must be enforced consistently.
- **Hallucinated technical explanations** — AI-generated teaching content about Java can be confidently wrong. The Research Agent and Reviewer stages exist specifically to catch this, but neither is a guarantee.
- **Weak retention** — Short-form educational content can be technically correct but boring or hard to follow, leading to low watch-through. Rendering and script pacing decisions directly affect this and are hard to validate without real audience data.
- **Render quality issues** — Automated rendering can produce output that is technically complete but visually inconsistent (bad timing, cramped text, clashing themes). Modular templates reduce but do not eliminate this risk.
- **Over-automation before validation** — Building out topic selection, analytics, and scaling before a single reel's quality is proven wastes effort on infrastructure for a pipeline that may still need fundamental rework. Phase 1's scope boundary exists specifically to prevent this.

## 12. Next Implementation Steps

The architecture-design steps this section originally listed (curriculum structure, memory model, topic selection design) are complete. The next steps are implementation, not design — tracked in full, with priority and dependencies, in the repository root [`TODO.md`](../TODO.md). In summary:

1. Wire a real `VertexAiClient` implementation (`backend/vertex/`) against Vertex AI, and draft the first narrowly-scoped prompts (planner, research, teaching) this document's Section 8 already calls for — currently `prompts/` is unpopulated.
2. Implement `TopicSelector` and `MemoryService` against real Firestore-backed repositories, replacing the placeholder `repositories/` contracts.
3. Implement `ResearchService`, `LessonService`, `ContentDirectorService`, and `ScriptService`, each validated against its schema's worked examples in `brain/`.
4. Build the single end-to-end path for one topic through Storyboard, without Voice/Subtitles/Assets/Rendering yet — confirming the decision layer alone produces correct, schema-valid output.
5. Implement the production layer (`VoiceService`, `SubtitleService`, `AssetService`, `RendererService`) and the Reviewer, completing one full reel end to end.
6. Populate `assets/` with a real asset catalog for `AssetService` to resolve against.
7. Revisit scope for a post-Phase-1 pass — including activating `analytics/` — only after one reel has been produced correctly through the full pipeline.
