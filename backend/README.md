# ForgeBrain Backend

A Spring Boot project structure for the pipeline described in [`../docs/PIPELINE.md`](../docs/PIPELINE.md), now with real implementations landing stage by stage — see "Current Status" in the root [`README.md`](../README.md) for what's live versus still a scaffold. See [`../TODO.md`](../TODO.md) for what comes next.

## Stack

Java 25, Spring Boot 3.3, Maven. `com.google.cloud:google-cloud-vertexai` is declared for the
research stage — see Section 5 and "Vertex AI Research Stage" below.

## Package Structure

Two organizing principles are layered on top of each other, both requested explicitly for this pass:

**Cross-cutting layers** — generic to every pipeline stage:

| Package | Holds |
| --- | --- |
| `config` | Configuration placeholders (Vertex AI, Cloud Storage, Firestore, Cloud Scheduler, Cloud Run, general application). See `docs/CONFIGURATION.md`. |
| `controllers` | HTTP endpoints. Only a health check exists — see Section 3. |
| `services` | One interface per pipeline stage (14 total) plus `MemoryService`. Contracts only, no implementations. |
| `models` | 19 immutable domain records, one per pipeline artifact, mirroring every `*-schema.json` in `brain/`, `renderer/`, `reviewer/`, `publishing/`. |
| `entities` | The small subset of state that's actually persisted (memory, topic tracking, pipeline runs) — see Section 4 for why these are shaped differently from `models`. |
| `dto` | API request/response wrappers, decoupled from the internal `models` shapes. |
| `repositories` | Persistence contracts for `entities`. |
| `shared` | Types reused across every layer: `ConfidenceNotes`, `PipelineStage`, `Timestamped`, `IdGenerator`. |
| `exceptions` | `PipelineStageException` and its specific subtypes. |

**Domain-specific packages** — supporting types for one part of the system that don't fit a generic layer:

| Package | Holds |
| --- | --- |
| `pipeline` | `PipelineOrchestrator`, `PipelineContext` (the run-scoped accumulator — see Section 4), `PipelineRunStatus`. |
| `vertex` | The Vertex AI integration seam every generative service is expected to call through. |
| `memory` | `MemoryQueries` — the six standard lookups from `memory/README.md`'s decision table, promoted to named methods. |
| `curriculum` | `CurriculumLoader`, `RoadmapLevel` — reading `curriculum/java-roadmap.json`. |
| `rendering` | The rendering foundation (`RenderPlan`, `SceneRenderPlan`, `RenderAssetManifest`, `SubtitleTimeline`, `RenderPlanBuilder`, `AssetCollector`, `RenderValidator`) plus its real FFmpeg execution path in `rendering.ffmpeg` (`FfmpegRenderEngine`, `RenderCommandBuilder`, `SrtWriter`, `PlaceholderAssetResolver`). See "Rendering Foundation" and "Storyboard to MP4" below. |
| `analytics` | `PerformanceSnapshot`, `StrategyPerformanceAggregate` — see `analytics/analytics-spec.md`. Not active in Phase 1. |
| `publishing` | `PlatformFormatter` — per-platform metadata formatting. |

## Design Decisions Worth Knowing About

### 1. Records for `models` and `dto`, plain classes for `entities`

`models` and `dto` types are Java records: immutable, naturally value-like, and a close structural match for the JSON schemas they mirror. `entities` are deliberately plain mutable classes with a no-arg constructor and getters/setters — the conventional shape for document-database mapping, and a shape that signals "this one is different: persisted and mutable" on sight, without needing to read the Javadoc first.

### 2. `models` vs. `entities` vs. `dto`

Three types can look similar; they answer different questions:

- **`models`** — "what does one pipeline stage produce?" Transient, in-memory, passed directly between `services` calls.
- **`entities`** — "what actually gets written to Firestore?" Only `MemoryStateEntity`, `TopicEntity`, and `PipelineRunEntity` exist — most pipeline artifacts (a `Script`, a `Storyboard`) are never persisted on their own in this design; they're consumed by the next stage and whatever final artifact matters (`PublishingPackage`) is what would eventually be stored, not every intermediate step.
- **`dto`** — "what does a future HTTP API accept and return?" Kept separate so the pipeline's internal shapes can change without breaking an API contract, and vice versa.

### 3. `controllers` is nearly empty, on purpose

A controller calling a `services` interface with no implementation behind it would either fail at startup or require a fake implementation — this phase's rules exclude both. `HealthController` is the one exception: a real, working, standard Spring Boot convention endpoint, not pipeline logic, included so the application is genuinely runnable and deployable now, with real endpoints added once real services exist.

### 4. `PipelineContext` vs. explicit service parameters

Every `services` interface takes explicit, named parameters (e.g. `ResearchService.research(String selectedTopicId, Topic curriculumContext, ...)`) rather than one shared context object — reading a method signature should tell you exactly what that stage needs, matching its `*-spec.md`'s Inputs table. `PipelineContext` (in `pipeline`) exists at a different altitude: it's the orchestrator's own bookkeeping object, accumulating every stage's output as one topic moves through all fourteen stages, and the shape `PipelineRunEntity` would persist. Services don't take it; the orchestrator that calls services does.

### 5. Google Cloud SDK: Vertex AI only, so far

`pom.xml` declares `com.google.cloud:google-cloud-vertexai`, used by `vertex/VertexAiClientImpl`
for the research and lesson stages (see "Vertex AI Research Stage" and "Vertex AI Lesson Stage"
below). No other Google Cloud client library (`spring-cloud-gcp`, Firestore, Cloud Storage) is
declared yet — `repositories` and `entities` are still shaped for that addition without
restructuring, but nothing beyond research and lesson calls a real GCP API in this phase.

### 6. Verification status

This project could not be built with Maven in the environment this scaffold was produced in (Maven is not installed there). Instead: every file in `models`, `shared`, `exceptions`, `services`, `entities`, `repositories`, and the seven domain packages (75 files total) — the entire subset with zero external dependencies — was compiled directly with `javac` and produced zero errors. `pom.xml` was checked for well-formed XML and balanced tags; both `application.yml` files were checked for valid YAML structure (no tabs, consistent indentation). The Spring-dependent files (`config`, `controllers`, `dto`, `BackendApplication`, the test) follow the same conventions but were not independently compiled — **run `mvn compile` (or `mvn test`) as the first verification step** before building on this scaffold.

## Vertex AI Research Stage

The research stage (`services/VertexAiResearchServiceImpl`, the `ResearchService` bean) calls
Google Vertex AI to generate the parts of a topic brief that benefit from generation —
`topicSummary`, `coreConcepts`, `simpleAnalogy`, `beginnerExplanation`, `advancedNotes`,
`safetyNotes`. Every other `ResearchResult` field (`learningObjective`, `commonMisconceptions`,
`codeExampleIdeas`, `relatedTopics`, `prerequisites`, ...) still comes straight from the
curriculum's own curated data, unchanged from the original heuristic approach.

**How it works**: `ResearchPromptBuilder` builds a prompt grounded in the curriculum's
`learning_objective`/`common_mistakes`/`example_ideas`, asking for a strict JSON response.
`VertexAiClientImpl` (`vertex/`) opens a `VertexAI` client with `GenerationConfig`'s
`responseMimeType` set to `application/json` and calls `GenerativeModel.generateContent`,
authenticating via Application Default Credentials — no API keys in this repo.
`VertexAiResearchServiceImpl` parses the JSON response into `VertexResearchContent` with the
shared snake_case `ObjectMapper` and assembles the full `ResearchResult`.

**Fallback**: any failure — missing `project-id`/`research-model` config, a failed API call, or
a response that doesn't parse into `VertexResearchContent` — falls back to
`ResearchServiceImpl`'s original heuristic brief. This is a real, tested code path, not just a
comment: it's what runs in any environment without GCP credentials configured, including this
project's own test suite.

**Local setup**:

```bash
gcloud auth application-default login
gcloud config set project <your-gcp-project-id>
```

Then supply `forgebrain.vertex-ai.project-id` and `forgebrain.vertex-ai.location` as environment
variables (`FORGEBRAIN_VERTEX-AI_PROJECT-ID`, `FORGEBRAIN_VERTEX-AI_LOCATION`) or a git-ignored
local YAML override — never commit real project IDs. `forgebrain.vertex-ai.research-model`
already defaults to `gemini-2.0-flash-001` in `application.yml`.

## Vertex AI Lesson Stage

The lesson stage (`services/VertexAiLessonServiceImpl`, the `LessonService` bean) calls the same
`VertexAiClient` (model configured separately via `forgebrain.vertex-ai.lesson-model`, also
defaulting to `gemini-2.0-flash-001`) to narrow a research brief into the single-concept lesson
blueprint required by `brain/lesson-spec.md` Section 4's "One of Everything" rule:
`lessonObjective`, `lessonSummary`, `keyPoints`, `stepByStepExplanation`, `coreExample`,
`analogy`, `commonMistakes`, `whatToAvoidSaying`, `beginnerTakeaway`, `retentionHook`,
`visualNotes`, and `confidenceNotes` all come from the model this time — unlike research, the
narrowing decision itself (which concept, which example, which mistake) is genuinely a judgment
call the model is asked to make and justify via `confidenceNotes.flaggedUncertainties`.
`topicId`/`topicTitle`/`audienceLevel`/`targetDurationSeconds` are still carried straight from
the research brief, and `teachingStyle` is still decided deterministically (the caller's request,
or the same heuristic `LessonServiceImpl` uses) before the prompt is built, not by the model.

**Fallback**: identical pattern to research — missing `project-id`/`lesson-model` config, a
failed API call, or a response that doesn't parse into `VertexAiLessonContent` all fall back to
`LessonServiceImpl`'s original heuristic narrowing. Real, exercised path, not a stub.

**What remains heuristic**: Storyboard is still a deterministic, mechanical stage — unchanged by
any of this. Content Director and Script are both Vertex AI-backed too — see "Vertex AI Content
Director Stage" and "Vertex AI Script Stage" below.

**Configuration**: `forgebrain.vertex-ai.lesson-temperature` (default `0.4`),
`lesson-max-output-tokens` (default `2048`), and `lesson-response-mime-type` (default
`application/json`) tune generation for this stage specifically; `VertexAiPromptRequest` carries
them through to `VertexAiClientImpl`'s `GenerationConfig` when set, falling back to the SDK's
defaults when null. No new local setup beyond the research stage's ADC steps above.

## Vertex AI Content Director Stage

The content director stage (`services/VertexAiContentDirectorServiceImpl`, the
`ContentDirectorService` bean) calls the same `VertexAiClient` (model configured separately via
`forgebrain.vertex-ai.content-director-model`, defaulting to `gemini-2.0-flash-001`) to make all
seven directorial decisions over a committed `Lesson`: `hookType`/`hookReason`,
`teachingStyle`/`teachingStyleReason`, `emotionalGoal`/`emotionalGoalReason`,
`pacing`/`pacingReason`/`scenePacing`, `visualStyle`/`supportingVisuals`/`visualStyleReason`,
`codeStyle`/`codeStyleReason`, `ctaStyle`/`ctaReason`, `retentionGoal`, `estimatedWatchTime`, and
`confidenceNotes` — see `brain/content-director-spec.md` Section 5. `topicId`/`topicTitle`/
`targetDurationSeconds` are still carried straight from the lesson, and `contentDirectorVersion`/
`generatedAt`/`basedOnLessonVersion` are still set deterministically, not by the model.

**How it works**: `ContentDirectorPromptBuilder` builds a prompt grounded entirely in the
lesson's own content (objective, analogy, `core_example`, `common_mistakes`, `key_points`,
`visual_notes`) — the Content Director never invents new topic facts or chooses a different
example than the lesson already committed to. Requested enum values use underscore form (e.g.
`COMMON_BUG`, `TRY_THIS_YOURSELF`) matching the Java enum constant names in `ContentStrategy`,
rather than the hyphenated form in `brain/content-director-schema.json` — the shared
`ObjectMapper`'s case-insensitive enum matching does not also translate hyphens to underscores
(see `config/JacksonConfig`'s Javadoc), so asking for underscores directly keeps parsing reliable
without a custom deserializer.

**Fallback**: identical pattern to research and lesson — missing `project-id`/
`content-director-model` config, a failed API call, or a response that doesn't parse into
`VertexAiContentStrategy` all fall back to `ContentDirectorServiceImpl`'s original deterministic
rule-based strategy. Real, exercised path, not a stub.

**What remains heuristic**: Storyboard is still a deterministic, mechanical stage — unchanged by
this. Script is now Vertex AI-backed too — see "Vertex AI Script Stage" below.

**Configuration**: `forgebrain.vertex-ai.content-director-temperature` (default `0.4`),
`content-director-max-output-tokens` (default `2048`), and
`content-director-response-mime-type` (default `application/json`) tune generation for this
stage specifically. No new local setup beyond the research stage's ADC steps above.

## Vertex AI Script Stage

The script stage (`services/VertexAiScriptServiceImpl`, the `ScriptService` bean) calls the same
`VertexAiClient` (model configured separately via `forgebrain.vertex-ai.script-model`, defaulting
to `gemini-2.0-flash-001`) to turn a committed `Lesson` and its binding `ContentStrategy` into
actual spoken narration and on-screen text: `hook`, `introLine`, `mainScript`,
`codeNarration.spokenLines`/`focusLine`, `recapLine`, `ctaLine`, `sceneText`, `tone`, and
`confidenceNotes` — see `brain/script-spec.md` Section 3 for the binding hook/teaching-style/
pacing/code-style/CTA mapping the prompt enforces.

**Deliberately not requested from the model**: `hookType`/`teachingStyle` are echoed verbatim
from the `ContentStrategy` rather than asked of the model, because Section 3 requires they "must
match exactly" — the Script Generator doesn't get to choose a different hook or posture than what
the Content Director already decided. `codeNarration.codeSnippet` is carried directly from the
lesson's own `core_example.code_sketch` rather than regenerated, so the on-screen code can never
drift from what the lesson actually committed to. `fullSpokenScript`, `wordCount`,
`estimatedDurationSeconds`, and `subtitleSegments` are all computed deterministically from the
model's structured fields, the same way `ScriptServiceImpl` computes them — asking the model to
also produce four mutually-dependent derived values invites exactly the kind of internal
inconsistency `brain/script-spec.md` Section 9 explicitly rules out (`full_spoken_script` must
reconstruct byte-for-byte from the structured fields; `subtitle_segments` must concatenate back
to it losslessly).

**Fallback**: identical pattern to research, lesson, and content director — missing
`project-id`/`script-model` config, a failed API call, or a response that doesn't parse into
`VertexAiScriptContent` all fall back to `ScriptServiceImpl`'s original deterministic templates.
Real, exercised path, not a stub.

**What remains heuristic**: Storyboard is the only stage left that's still deterministic and
mechanical — it groups the script's own already-validated segments into scenes rather than
generating anything new, so there's little a model would add there.

**Configuration**: `forgebrain.vertex-ai.script-temperature` (default `0.4`),
`script-max-output-tokens` (default `2048`), and `script-response-mime-type` (default
`application/json`) tune generation for this stage specifically. No new local setup beyond the
research stage's ADC steps above.

## Rendering Foundation

The AI content pipeline ends at `Storyboard`. Everything from there to a finished video is a
separate concern — production, not decision-making — and this is its architecture, not its
implementation:

```
Storyboard
    │  RenderPlanBuilder (pure transformation, no rendering)
    ▼
RenderPlan  ──────────────►  AssetCollector  ──────────────►  RenderAssetManifest
    │                                                          (every asset the plan needs,
    │  RenderValidator                                          deduplicated + categorized)
    ▼
RenderValidationResult
    │
    ▼  FfmpegRenderEngine.render(RenderPlan) — real, local, deterministic
reel.mp4 (+ thumbnail.jpg)  ──────────────►  VideoPackage
```

**`RenderPlan`** (`rendering/RenderPlan.java`) is everything one reel needs to render: video
`dimensions`/`fps` (derived from `Storyboard.aspectRatio`), `totalDurationSeconds`, one
`SceneRenderPlan` per scene, a reel-wide `FontSet`, a `SubtitleTimeline`, an `AudioPlan`, the
transition between every consecutive scene pair, and any reel-global asset references (e.g. a
watermark). **`SceneRenderPlan`** (`rendering/SceneRenderPlan.java`) is one scene's complete
blueprint: timing, a `BackgroundSpec`, on-screen `TextLayer`s, an optional `CodeLayer`, animation
instructions carried from the storyboard's `motionNotes`, which `SubtitleTimeline` cues belong to
it, and which assets it references. **`SubtitleTimeline`** (`rendering/SubtitleTimeline.java`) is
the reel's captions as one flat, chronologically-ordered list of cues, independent of scene
boundaries but still traceable back to the scene that produced each one.

**`RenderPlanBuilder`** (`rendering/RenderPlanBuilder.java`) does the `Storyboard` → `RenderPlan`
transformation — pure, deterministic, no generative calls, exactly one input and one output, no
video produced. Where the storyboard doesn't yet carry real production data (no narration audio
file, since Voice Generation isn't implemented; no resolved asset URIs, since Asset Management
isn't implemented), the builder emits deterministic placeholder references (e.g.
`"voiceover/" + topicId`, or a `RenderStyle`-driven font/music table) rather than nulls, so a
`RenderPlan` is always complete enough for `RenderValidator` to meaningfully check.

**`AssetCollector`** (`rendering/AssetCollector.java`) walks a built `RenderPlan` end to end —
its fonts, its audio track, its global refs, and every scene's own asset references — and
deduplicates them into one **`RenderAssetManifest`** (`rendering/RenderAssetManifest.java`), one
entry per distinct `(category, ref)` pair with every scene that uses it recorded in
`usedBySceneIds` (empty for reel-wide assets like background music). This is deliberately a
different type from `models/AssetManifest.java` — that one is Asset Management's own
pipeline-stage *output*, resolving a storyboard's abstract style names against a real catalog
that doesn't exist yet (see `TODO.md` 5.2); `RenderAssetManifest` is the renderer's own
bookkeeping of what a `RenderPlan` references, independent of whether anything can resolve those
references to a real file yet.

**`RenderValidator`** (`rendering/RenderValidator.java`) checks a `RenderPlan` for internal
consistency before anything downstream would act on it: invalid dimensions/fps/duration,
overlapping scenes, timing that doesn't add up (`duration != endTime - startTime`), a code layer
with no asset references, and subtitle problems (an entirely empty timeline is a warning; a
blank cue's text is an error). Every check runs in one pass — a `RenderValidationResult` reports
every issue found, not just the first, with `valid()` true iff nothing reached `ERROR` severity.

## Storyboard to MP4 (FFmpeg Rendering Path)

`FfmpegRenderEngine` (`rendering/ffmpeg/`, the `RenderEngine` bean) turns a validated `RenderPlan`
into a real, playable `.mp4` by shelling out to a local `ffmpeg` binary — no video composition
library, no SaaS, no Docker. One reusable 9:16 vertical template: a `RenderStyle`-driven solid
background for the full duration, one `drawtext` overlay per scene's on-screen text and per code
layer's focus line (each timed to its scene via `enable='between(t,start,end)'`), subtitles
burned in from an `.srt` file generated from `SubtitleTimeline`, and a short 0.3s fade in/out as
the one transition this first version implements — every current storyboard scene already uses a
hard cut between scenes, which a timed `enable=` window reproduces natively, so no per-scene
crossfade logic was needed yet.

**Local FFmpeg requirement**: a working `ffmpeg` binary must be installed and on `PATH` (override
via `forgebrain.rendering.ffmpeg-path` if it isn't). Verified against FFmpeg 8.x with `libx264`,
`libass` (subtitle burn-in), and `libfreetype` (`drawtext`) enabled — the default build from
[gyan.dev](https://www.gyan.dev/ffmpeg/builds/) or most package managers includes all three.
Without it, `FfmpegRenderEngine` throws a `RenderExecutionException` naming the configured path,
rather than failing silently.

**Audio**: `RenderPlan.audio().voiceoverTrackRef()` is currently always a placeholder (Voice
Generation isn't implemented — see `TODO.md` 1.8), so `PlaceholderAssetResolver` looks for a real
file at `forgebrain.rendering.voiceover-assets-directory/<topicId>.{mp3,wav}` and — this is the
documented fallback, not a stub — renders with a silent audio track (`ffmpeg`'s `anullsrc`) for
the plan's full duration when none exists. Drop a real file at that path and the next render
picks it up automatically, no code change.

**Placeholder-safe assets**: background is a solid color keyed off `RenderStyle` (no real
background video/image assets exist yet — Asset Management isn't implemented, see `TODO.md`
5.2/1.10); code panels show the focus line as text rather than a real code screenshot image;
fonts render via FFmpeg's default `fontconfig` resolution rather than a specific font file (the
`FontSet` names in a `RenderPlan` aren't resolved to real font files yet). All genuinely
real-and-working today, just not yet using production-quality assets.

**What's still needed**: real Voice Generation output, real Asset Management output (font files,
background media, code screenshot generation), per-scene transition styles beyond hard cuts
(`QUICK_FADE`/`SLIDE`/`ZOOM_PUNCH`/`MATCH_CUT` are defined on `Scene.TransitionStyle` but not yet
implemented as distinct FFmpeg filters), and wiring `RendererService`/`RenderJob` around this
engine for asynchronous job tracking (still just an interface — this task only implements the
synchronous `RenderEngine` seam itself).

## What's Deliberately Not Here

Per this phase's explicit rules: no Docker, no Kubernetes, no deployment/CI infrastructure, no publishing/auto-upload, and no authentication beyond Application Default Credentials for Vertex AI (see "Vertex AI Research Stage" above — the one real Google Cloud client integration so far; local FFmpeg execution, see "Storyboard to MP4" above, is the one real non-Google rendering integration). See the repository root [`TODO.md`](../TODO.md) for the tracked path from here.
