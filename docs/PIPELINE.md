# The ForgeBrain Pipeline: Curriculum to MP4

Status: Planning document — Phase 1
Scope: The authoritative, current description of how one piece of data — a single reel — flows from the Java curriculum all the way to a finished, reviewed video package. This document supersedes the stage list and data-flow order in `docs/ARCHITECTURE.md` Sections 4-5, which described an earlier, shorter version of this pipeline before the Content Director, Voice, Subtitles, Assets, and post-render Reviewer stages existed.

## 1. The Full Pipeline, in Order

```
Curriculum
   ↓
Memory
   ↓
Topic Selection
   ↓
Research
   ↓
Lesson
   ↓
Content Director
   ↓
Script
   ↓
Storyboard
   ↓
Voice
   ↓
Subtitles
   ↓
Assets
   ↓
Renderer
   ↓
Reviewer
   ↓
Publishing Package
```

Fourteen stages. Every arrow is a structured, schema-defined handoff — no stage passes freeform prose to the next. Every stage's full contract lives in its own spec + schema pair; this document is the map connecting them, not a replacement for any of them.

## 2. Stage-by-Stage: Input, Output, and Where It Lives

| # | Stage | Input | Output | Spec | Schema |
| --- | --- | --- | --- | --- | --- |
| 1 | **Curriculum** | — (static data) | The full Java topic roadmap: levels, prerequisites, difficulty. | `curriculum/README.md` | `curriculum/java-roadmap.json` |
| 2 | **Memory** | — (persisted state) | What's been posted, queued, or flagged for revision, and how each topic performed. | `memory/README.md` | `memory/memory-schema.json` |
| 3 | **Topic Selection** | Curriculum + Memory | One `TopicSelectionDecision`: which topic, why, and what was rejected. | `brain/topic-selector-spec.md` | `brain/topic-selector-schema.json` |
| 4 | **Research** | Selected topic + curriculum context + memory | A `ResearchResult`: validated facts, misconceptions, an analogy, and safety notes. | `brain/research-spec.md` | `brain/research-output-schema.json` |
| 5 | **Lesson** | Research result | A `Lesson` blueprint: one committed concept, one example, one takeaway. | `brain/lesson-spec.md` | `brain/lesson-output-schema.json` |
| 6 | **Content Director** | Lesson | A content strategy: hook type, teaching posture, pacing, tone — never dialogue. | `brain/content-director-spec.md` | `brain/content-director-schema.json` |
| 7 | **Script** | Lesson + content strategy | A `Script`: the actual spoken words and on-screen text, word-count-estimated for duration. | `brain/script-spec.md` | `brain/script-output-schema.json` |
| 8 | **Storyboard** | Script | A scene-by-scene visual plan with timing, code panels, motion, and transitions. | `brain/storyboard-spec.md` | `brain/storyboard-output-schema.json` |
| 9 | **Voice** | Storyboard | Narrated audio per scene, plus *real* measured timing that supersedes the script's estimate. | `renderer/voice-spec.md` | `renderer/voice-schema.json` |
| 10 | **Subtitles** | Storyboard + Voice result | Final captions, reconciled against real audio timing. | `renderer/subtitle-spec.md` | `renderer/subtitle-schema.json` |
| 11 | **Assets** | Storyboard | An `AssetManifest`: fonts, color theme, music, watermark resolved from style names. | `renderer/asset-management-spec.md` | `renderer/asset-management-schema.json` |
| 12 | **Renderer** | Storyboard + Voice + Subtitles + Assets | A `RenderJob`, and on success, a `VideoPackage`: the finished MP4 + thumbnail + technical metadata. | `renderer/render-spec.md` | `renderer/render-schema.json` |
| 13 | **Reviewer** | VideoPackage + QualityScore + lesson safety notes | A `ReviewResult`: `approved`, `needs_revision`, or `rejected`. | `reviewer/reviewer-spec.md`, `reviewer/quality-scoring-spec.md` | `reviewer/reviewer-schema.json`, `reviewer/quality-scoring-schema.json` |
| 14 | **Publishing Package** | An **approved** ReviewResult + VideoPackage + captions | A `PublishingPackage`: title, description, tags, and file references — never an actual post. | `publishing/publishing-spec.md` | `publishing/publishing-schema.json` |

A fifteenth component, **Analytics** (`analytics/analytics-spec.md`), is not part of this linear flow — it's a feedback loop that only begins once a `PublishingPackage` is actually posted somewhere, which Phase 1 never does. See Section 5.

## 3. The Three-Layer Shape of the Pipeline

The fourteen stages fall into three layers, each living in its own top-level folder, each with a distinct job:

| Layer | Folder | Stages | Job |
| --- | --- | --- | --- |
| **Decide** | [`brain/`](../brain/) | Topic Selection → Research → Lesson → Content Director → Script → Storyboard | Decide *what* to teach and *how* to present it. Produces structured text/JSON only — no media. |
| **Produce** | [`renderer/`](../renderer/) | Voice → Subtitles → Assets → Renderer | Turn the storyboard's decisions into real media: audio, captions, resolved assets, a finished video file. |
| **Gate & Package** | [`reviewer/`](../reviewer/), [`publishing/`](../publishing/) | Reviewer → Publishing Package | Decide whether the finished artifact is good enough, then bundle it for a future publishing step. |

No stage skips a layer. A storyboard never talks directly to the Renderer without Voice, Subtitles, and Assets resolving first; a `VideoPackage` never reaches Publishing Package without passing through the Reviewer. This is deliberate — see `docs/ARCHITECTURE.md` Section 3's "Quality over speed" principle.

## 4. The Throughline: Estimate, Then Reality

One design decision threads through six consecutive stages and is worth naming explicitly, because it's easy to miss reading any single spec in isolation:

- **Script** estimates duration from a word-count formula (2.5 words/second — `brain/script-spec.md` Section 8).
- **Storyboard** builds scene timing from that same estimate.
- **Voice** is where a *real* voice finally speaks those words, and the estimate is checked against reality for the first time — `renderer/voice-spec.md` Section 4 makes this an explicit handoff of timing authority.
- **Subtitles** re-times every caption against Voice's real measurements, never the original estimate.
- **Renderer** cuts the final video to Voice's real timing.
- **Quality Scoring**'s `pacing_fit` dimension checks how much drift occurred between the original estimate and reality.

Every stage in this chain treats the *previous* stage's numbers as provisional and the *next* stage's measurements as more trustworthy, right up until Voice Generation produces a real, measured number that nothing after it second-guesses.

## 5. What Happens After Publishing Package (and What Doesn't, Yet)

`PublishingPackage.scheduling.status` can only ever be `draft` or `ready` in Phase 1 — nothing in this pipeline actually posts a reel anywhere (`docs/ARCHITECTURE.md` Section 10; `publishing/publishing-spec.md` Section 1). If a package were posted (by a future, not-yet-built stage), real audience metrics would eventually become available, and `analytics/analytics-spec.md` describes — but does not activate — the loop that would follow:

```
Publishing Package → (posting, not built) → Analytics → Memory (performance_score, retention_score) → Topic Selection (next cycle)
```

This closes the loop back to the top of this document: the next Topic Selection run would have real performance data to weigh, not just curriculum structure and cooldowns. See `analytics/analytics-spec.md` Section 3 for the complete list of fields every upstream stage already reserved for this data.

## 6. Traceability: How to Follow One Reel Through the Whole Pipeline

Every stage's output carries a `based_on_<previous_stage>_version` field (or equivalent — `based_on_review_id`, `based_on_video_package_id`) pointing at the exact upstream artifact it was built from, and every stage's decision-bearing fields (`reason`, `confidence_notes`, `flagged_uncertainties`) are designed to be read together across stages, not just within one. The `java-arrays-basics` revision case threaded through `brain/research-examples/`, `brain/lesson-examples/`, `brain/content-director-examples.md`, `brain/script-examples.md`, `brain/storyboard-examples.md`, `renderer/examples.md`, and `reviewer/examples.md` demonstrates this end to end: one topic, one underlying concern (a previous version's weak retention), visibly carried and responded to at every single stage, from research through final review.

## 7. What This Document Is Not

This is a map, not a contract. It does not redefine any field — every type, enum, and requirement lives in the stage's own schema file, and if this document and a schema ever disagree, the schema is authoritative. It also does not describe *how* any stage is implemented (no API calls, no rendering code, no orchestration logic) — see `docs/CONFIGURATION.md` for the infrastructure this would eventually run on, and the `backend/` package structure for the code-level contracts (interfaces, DTOs) that mirror every schema referenced above.
