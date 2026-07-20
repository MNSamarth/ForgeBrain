# ForgeBrain Backend

A Spring Boot project structure for the pipeline described in [`../docs/PIPELINE.md`](../docs/PIPELINE.md), now with real implementations landing stage by stage — see "Current Status" in the root [`README.md`](../README.md) for what's live versus still a scaffold. See [`../TODO.md`](../TODO.md) for what comes next.

## Stack

**Java 21 LTS**, **Spring Boot 3.x**, **Maven**. `com.google.cloud:google-cloud-vertexai` is
declared for the research stage — see Section 5 and "Vertex AI Research Stage" below.

## Package Structure

Two organizing principles are layered on top of each other, both requested explicitly for this pass:

**Cross-cutting layers** — generic to every pipeline stage:

| Package | Holds |
| --- | --- |
| `config` | Configuration placeholders (Vertex AI, Cloud Storage, Firestore, Cloud Scheduler, Cloud Run, general application). See `docs/CONFIGURATION.md`. |
| `controllers` | HTTP endpoints. Only a health check exists — see Section 3. |
| `services` | One interface per pipeline stage (14 total) plus `MemoryService`. Topic Selection, Memory, Research, Lesson, Content Director, Script, Storyboard, Voice, Subtitles, and Assets all have real implementations now — see the stage sections below. Only Renderer's job-lifecycle management (`RendererService`), Reviewer, and Publishing remain interface-only. |
| `models` | 19 immutable domain records, one per pipeline artifact, mirroring every `*-schema.json` in `brain/`, `renderer/`, `reviewer/`, `publishing/`. |
| `entities` | The small subset of state that's actually persisted (memory, topic tracking, pipeline runs) — see Section 4 for why these are shaped differently from `models`. |
| `dto` | API request/response wrappers, decoupled from the internal `models` shapes. |
| `repositories` | Persistence contracts for `entities`. |
| `shared` | Types reused across every layer: `ConfidenceNotes`, `PipelineStage`, `Timestamped`, `IdGenerator`. |
| `exceptions` | `PipelineStageException` and its specific subtypes. |

**Domain-specific packages** — supporting types for one part of the system that don't fit a generic layer:

| Package | Holds |
| --- | --- |
| `pipeline` | `PipelineOrchestrator` (topic selection through storyboard) and `ReelExportService` (the full production continuation through a rendered MP4 — see "End-to-End Reel Export" below), plus their execution reports and `PipelineContext` (the run-scoped accumulator — see Section 4). |
| `vertex` | The Vertex AI integration seam every generative service is expected to call through. |
| `memory` | `MemoryQueries` — the six standard lookups from `memory/README.md`'s decision table, promoted to named methods. |
| `curriculum` | `CurriculumLoader`, `RoadmapLevel` — reading `curriculum/java-roadmap.json`. |
| `rendering` | The rendering foundation (`RenderPlan`, `SceneRenderPlan`, `RenderAssetManifest`, `SubtitleTimeline`, `RenderPlanBuilder`, `AssetCollector`, `RenderValidator`) plus its real FFmpeg execution path in `rendering.ffmpeg` (`FfmpegRenderEngine`, `RenderCommandBuilder`, `SrtWriter`, `PlaceholderAssetResolver`). See "Rendering Foundation" and "Storyboard to MP4" below. |
| `job` | The durable job layer on top of `ReelExportService`: `ReelJob`, `ReelJobRepository`, `OutputPackagingService`, `OutputStorage`, `ReelJobReport`, `ReelJobService`. See "Reel Job System" below. |
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

### 5. Google Cloud SDK: Vertex AI and Cloud Storage, so far

`pom.xml` declares `com.google.cloud:google-cloud-vertexai`, used by `vertex/VertexAiClientImpl`
for the research/lesson/content-director/script stages, and `com.google.cloud:google-cloud-storage`,
used by `job/CloudStorageOutputStorage` (see "Cloud Storage Seam" below) — both authenticate via
Application Default Credentials, no embedded keys. No other Google Cloud client library
(`spring-cloud-gcp`, Firestore) is declared yet — `repositories` and `entities` are still shaped
for that addition without restructuring.

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

**Audio**: when a `RenderPlan` was built via the enriched, reconciled `RenderPlanBuilder.build(Storyboard,
VoiceResult, SubtitleResult, AssetManifest)` overload (see "End-to-End Reel Export" below),
`RenderPlan.audio().voiceoverTrackRef()` is already a real file `VoiceServiceImpl` wrote —
`PlaceholderAssetResolver`'s own lookup only matters for the simpler, standalone `RenderPlanBuilder.build(Storyboard)`
path. Either way, the underlying convention is the same: a real file at
`forgebrain.rendering.voiceover-assets-directory/<topicId>.{mp3,wav}`, with a silent audio track
(`ffmpeg`'s `anullsrc`) as the documented fallback when none exists — see "Voice Generation" below.

**Placeholder-safe assets**: when a `RenderPlan` comes from the enriched builder, fonts/music/watermark
come from `AssetServiceImpl`'s resolved `AssetManifest` (see "Asset Management" below), which
checks `forgebrain.rendering.assets-directory` for real catalog files before falling back to the
same deterministic, `RenderStyle`-keyed names as before. Code panels still show the focus line as
text rather than a real code screenshot image — that generation step doesn't exist yet.

**What's still needed**: a real Text-to-Speech provider behind `VoiceServiceImpl` (Google Cloud
Text-to-Speech, per `renderer/voice-spec.md` Section 6 — the seam is ready, nothing calls it yet);
a real, populated asset catalog (font files, background media, licensed music) behind
`AssetServiceImpl`, ideally backed by Cloud Storage in a real deployment rather than a local
directory; code screenshot/card image generation; per-scene transition styles beyond hard cuts
(`QUICK_FADE`/`SLIDE`/`ZOOM_PUNCH`/`MATCH_CUT` are defined on `Scene.TransitionStyle` but not yet
implemented as distinct FFmpeg filters); and wiring `RendererService`/`RenderJob` around this
engine for asynchronous job tracking (still just an interface — this path runs synchronously).

## Voice Generation

`VoiceServiceImpl` (`services/VoiceServiceImpl.java`, the `VoiceService` bean) is the seam a real
Google Cloud Text-to-Speech integration plugs into (`renderer/voice-spec.md` Section 6) without
changing its contract. Two real, exercised paths:

- **A real narration file exists** at `forgebrain.rendering.voiceover-assets-directory/<topicId>.{mp3,wav}`
  — its real duration is measured via `ffprobe` and distributed across scenes proportionally to
  each scene's estimated share of the storyboard's total (the same idea `renderer/subtitle-spec.md`
  Section 4 uses for its own fallback, applied here since no per-scene files or word-level timing
  exist yet).
- **No real file exists** (the normal case today) — a real silent WAV is synthesized via `ffmpeg`
  at exactly the storyboard's total estimated duration and written to that same path, so every
  scene's `actualDurationSeconds` equals its estimate (zero drift, honestly reported) and the
  renderer has a genuine file to mix in. This is the documented fallback Part 3 of this mission
  asked for, not a stub — and it's what the render path actually uses today.

Both paths report `wordTimings` as empty, which `renderer/voice-spec.md` Section 8 explicitly
sanctions.

## Subtitle Generation

`SubtitleServiceImpl` (`services/SubtitleServiceImpl.java`, the `SubtitleService` bean) reconciles
the storyboard's word-count-estimated subtitle timing against `VoiceResult`'s real timing, per
`renderer/subtitle-spec.md` Section 4's two methods — **word-alignment** (consumes real per-word
timestamps when `wordTimings` is populated) and **proportional-estimate** (scales each segment's
original timing by `actualDurationSeconds / estimatedDurationSeconds` when it isn't, which is what
runs today given `VoiceServiceImpl`'s current fallback). Both methods build the reconciled reel
timeline from a running cursor over each scene's *real* duration, not the storyboard's estimate —
scene order/content is never changed, only timing. Output stays mobile-readable via a fixed
`SafeRegion` (10% top / 18% bottom) matching `storyboard-spec.md` Section 9's guidance on platform
UI overlap; burn-in styling itself happens at render time (see "Storyboard to MP4" above).

## Asset Management

`AssetServiceImpl` (`services/AssetServiceImpl.java`, the `AssetService` bean) resolves a
storyboard's abstract `render_style` into concrete references, per
`renderer/asset-management-spec.md`. It checks `forgebrain.rendering.assets-directory` (the local
stand-in for the repository's `assets/` catalog, still empty per `TODO.md` 5.2 — a real deployment
would back this with Cloud Storage instead) for real files at documented per-category paths
(`fonts/<style>-heading.ttf`, `music/<style>.mp3`, `watermark/default.png`, ...) before falling
back to the same deterministic, `RenderStyle`-keyed placeholder names used elsewhere. Every
category the mission asked for is covered: fonts, background/color theme, watermark, music, and
per-scene code-card references for scenes with a code block.

## End-to-End Reel Export

`ReelExportServiceImpl` (`pipeline/ReelExportServiceImpl.java`, the `ReelExportService` bean) is
the full production path this mission's Part 6 asks for:

```
Topic Selection → Research → Lesson → Content Director → Script → Storyboard   (PipelineOrchestrator, unchanged)
    → Voice → Subtitles → Assets → RenderPlan → RenderValidation → Render → MP4
```

Call it from a test (see `ReelExportServiceImplTest`, a real `@SpringBootTest` running this whole
path against the real curriculum and a real `ffmpeg` binary), from any Spring-managed component
via `ReelExportService`, or as a "one command" local run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.reel-export.run-on-startup=true
```

Every run writes one output folder (under `forgebrain.rendering.output-directory`) containing
`reel.mp4`, `thumbnail.jpg`, `subtitles.srt`, `metadata.json` (the run's `VideoPackage`, serialized),
and `report.json` (see "Observability" below) — everything needed to inspect or hand off one reel
without reading logs. A failure at any stage still writes a `report.json` (to a `failed-<topicId>-<timestamp>`
folder if it happened before a video existed), so a broken run is always diagnosable, matching
`PipelineOrchestratorImpl`'s own established try/catch/finally-report pattern.

### Observability

`ReelExportReport` (`pipeline/ReelExportReport.java`) covers everything this mission's Part 7
asked for, for the stages after `PipelineExecutionReport` already covers: a `runId` independent of
the underlying AI pipeline's own `pipelineId`, start/end timestamps and per-stage `Duration`,
success/failure per stage (`AI_PIPELINE`, `VOICE`, `SUBTITLES`, `ASSETS`, `RENDER_PLAN`,
`RENDER_VALIDATION`, `RENDER_EXECUTION`), fallback usage (flagged as a warning whenever
`VoiceServiceImpl` or `AssetServiceImpl` used their placeholder path), a human-readable render
validation summary, every output path written, and accumulated warnings/errors. Persisted by
`ReelExportReportWriterImpl` as `report.json` alongside the reel it describes.

## Reel Job System

`ReelExportServiceImpl` above is the one-shot function-call path — call it, get a reel or an
exception. `com.forgebrain.backend.job` (`ReelJobServiceImpl`, the `ReelJobService` bean) is the
job-aware sibling on top of it: the same production stages, composed independently around an
explicit, durable, persisted job record, so ForgeBrain behaves like a content service with
inspectable jobs rather than only a script you run and watch the console for. **The sync path is
completely unmodified** — `ReelExportServiceImpl`/`ReelExportServiceImplTest` have a zero-line
diff from before this layer was added; the job layer reuses the same lower-level stage services
(`VoiceService`, `SubtitleService`, `AssetService`, `RenderPlanBuilder`, `RenderValidator`,
`RenderEngine`, `PipelineOrchestrator`) independently rather than wrapping or modifying it.

### Job Lifecycle

`ReelJob` (`job/ReelJob.java`) is an immutable record; every transition below returns a new
snapshot (never mutates in place) and is directly unit-tested in isolation (`ReelJobTest`):

```
QUEUED → RUNNING → VALIDATING → RENDERING → PACKAGING → COMPLETED
                                                        ↘ FAILED (from any stage)
```

`QUEUED` (job created) → `RUNNING` (AI pipeline through render-plan construction) → `VALIDATING`
(`RenderValidator`) → `RENDERING` (`RenderEngine`) → `PACKAGING` (`OutputPackagingService`) →
`COMPLETED`. Each `ReelJob` carries: `jobId` and a separate `pipelineRunId` (a distinct
correlation id per execution attempt — see the class javadoc for why "rerun" means "submit a new
job" today, not retry-in-place), topic id/title, `createdAt`/`startedAt`/`completedAt`/`duration`,
`outputDirectory`, `outputFiles` (category → stored reference), `failureReason`, `warnings`,
`fallbackStages` (which stages used a documented fallback), and `renderChecksum` (from the
rendered `VideoPackage`, once rendering completes).

**`ReelJobService.submitJob()` never throws** for a pipeline/render/packaging failure — unlike
`ReelExportService.exportReel()`, it always returns a `ReelJob`, with `Status.FAILED` and
`failureReason` set when something went wrong. That's the intended job-system contract: inspect
the returned record's status instead of wrapping the call in try/catch.

### Output Packaging

`OutputPackagingServiceImpl` (`job/OutputPackagingServiceImpl.java`) turns one render's raw
working-directory files into a structured package: writes `metadata.json` (the run's
`VideoPackage`, serialized — same format the sync path already uses), then passes every file
(`reel.mp4`, `thumbnail.jpg`, `subtitles.srt`, `metadata.json`, and later `report.json`) through
the `OutputStorage` seam. Category keys are consistent across both this layer and the sync path's
report (`"video"`, `"thumbnail"`, `"subtitles"`, `"metadata"`, `"report"`), so nothing downstream
needs to know which stage produced which file.

### Cloud Storage Seam

`OutputStorage` (`job/OutputStorage.java`) is the abstraction every output file passes through:
`store(jobId, localFile) → stable reference`. Exactly one implementation is active at a time,
chosen by `OutputStorageFactory` (`job/OutputStorageFactory.java`) and wired as the single
`OutputStorage` bean by `OutputStorageBeanConfig` — `OutputPackagingServiceImpl` and everything
above it depend only on the interface, so they never know or care which backend is active.

**Local storage (the default).** `LocalOutputStorage` copies each file into
`<forgebrain.jobs.output-storage-root>/<jobId>/<fileName>`. Every committed profile uses this —
`forgebrain.cloud-storage.enabled` defaults to `false` — and nothing below changes that behavior.

**Cloud Storage.** `CloudStorageOutputStorage` uploads each file to `gs://<media-bucket>/<output-
prefix>/<jobId>/<fileName>` using the official `com.google.cloud:google-cloud-storage` client,
authenticated via Application Default Credentials (the same pattern as the Vertex AI client — no
embedded keys), and returns that `gs://` URI as the stored reference. The GCS client is only ever
constructed once cloud storage is confirmed enabled and validated, so a disabled (default) setup
never attempts credential discovery — no GCP project or credentials are needed to build or test
this project.

**Enabling it later.** Set, in `application.yml` or via a property/environment override:

```yaml
forgebrain:
  cloud-storage:
    enabled: true
    media-bucket: "your-bucket-name"
    output-prefix: "reels"
    project-id: ""   # optional; blank uses the ADC-associated project
```

If `enabled: true` but `media-bucket` is blank, `OutputStorageFactory` fails fast at startup with
a clear `ConfigurationException` instead of silently falling back — misconfiguration should be
loud when cloud storage was explicitly opted into. With `enabled: false` (or omitted), cloud
storage is never touched and local storage behaves exactly as it always has.

**What's stored, either way:** `reel.mp4`, `thumbnail.jpg`, `subtitles.srt`, `metadata.json`, and
`report.json` — the same five artifacts, same category keys (`"video"`, `"thumbnail"`,
`"subtitles"`, `"metadata"`, `"report"`), through both `OutputPackagingService` and the job report
writer, regardless of which backend is active. Publishing (actually distributing a finished reel)
remains out of scope — this is storage, not distribution.

### Job Storage

`ReelJobRepository` (`job/ReelJobRepository.java`) persists `ReelJob` snapshots — `create`,
`update`, `findById`, `findAll`. `LocalFileReelJobRepository` is the only implementation: one
`<jobId>.json` file per job under `forgebrain.jobs.jobs-directory`, overwritten on every `update`
— the same one-JSON-file-per-record convention already established by `ReportWriterImpl` and
`ReelExportReportWriterImpl`, not a new database dependency this project doesn't otherwise have.

### Diagnostics and Reporting

`ReelJobReport` (`job/ReelJobReport.java`) extends the report pattern `ReelExportReport` already
established (reusing `StageExecutionSummary` directly) with this layer's own fields: `jobId`
distinct from `pipelineRunId`, explicit `fallbackStages`, and a `packagingSummary`. Written by
`ReelJobReportWriterImpl` as `report.json` alongside the reel, then stored through `OutputStorage`
like every other output file — readable by a human opening the JSON file, or by code via the
shared `ObjectMapper` (see `ReelJobServiceImplTest` for both). Written on success **and on
failure** (to a `failed-job-<jobId>` folder if the job never reached a render directory), so a
broken job is always diagnosable from one file, per this mission's "never silently swallow a
failure" requirement.

### Entry Points

Call `ReelJobService.submitJob()` from any Spring-managed component, from a test (see
`ReelJobServiceImplTest`, a real `@SpringBootTest` proving one job to completion and a second
rerun failing cleanly with its own report), or as a "one command" local run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.jobs.run-on-startup=true
```

### Where This Fits

`ReelJob.Status.COMPLETED` plus a populated `outputFiles` map is exactly the shape a future
Publishing stage would need to pick up — a job id, a topic, and stored references to every
artifact, already real `gs://` URIs when Cloud Storage is enabled (see "Cloud Storage Seam"
above). Publishing itself is out of scope for this mission (see `TODO.md` 1.13/1.14).

## What's Deliberately Not Here

Per this phase's explicit rules: no Docker, no Kubernetes, no deployment/CI infrastructure, no publishing/auto-upload, and no authentication beyond Application Default Credentials for Vertex AI (see "Vertex AI Research Stage" above — the one real Google Cloud client integration so far; local FFmpeg execution, see "Storyboard to MP4" above, is the one real non-Google rendering integration). See the repository root [`TODO.md`](../TODO.md) for the tracked path from here.
