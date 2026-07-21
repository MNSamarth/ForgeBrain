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
| `services` | One interface per pipeline stage (14 total) plus `MemoryService` and `ReelAnalyticsService`. Topic Selection, Memory, Research, Lesson, Content Director, Script, Storyboard, Voice, Subtitles, Assets, Reviewer, Publishing, and analytics ingestion/feedback all have real implementations now — see the stage sections below. Research/Lesson/Content Director/Script no longer call `VertexAiClient` directly — they call `AiGateway` (see "AI Gateway" below). Only Renderer's job-lifecycle management (`RendererService`) and `AnalyticsService` (reserved for real audience/platform metrics — see "Analytics / Feedback Loop" below) remain interface-only. |
| `models` | 22 immutable domain records, one per pipeline artifact, mirroring every `*-schema.json` in `brain/`, `renderer/`, `reviewer/`, `publishing/`. |
| `entities` | The small subset of state that's actually persisted (memory, topic tracking, pipeline runs) — see Section 4 for why these are shaped differently from `models`. |
| `dto` | API request/response wrappers, decoupled from the internal `models` shapes. |
| `repositories` | Persistence contracts for `entities`. |
| `shared` | Types reused across every layer: `ConfidenceNotes`, `PipelineStage`, `Timestamped`, `IdGenerator`. |
| `exceptions` | `PipelineStageException` and its specific subtypes. |

**Domain-specific packages** — supporting types for one part of the system that don't fit a generic layer:

| Package | Holds |
| --- | --- |
| `pipeline` | `PipelineOrchestrator` (topic selection through storyboard) and `ReelExportService` (the full production continuation through a rendered MP4 — see "End-to-End Reel Export" below), plus their execution reports and `PipelineContext` (the run-scoped accumulator — see Section 4). |
| `ai` | `AiGateway` — the centralized orchestration seam (routing, retry, timeout, validation, cache, metrics) every generative service calls through instead of `vertex.VertexAiClient` directly. See "AI Gateway" below. |
| `vertex` | The Vertex AI integration seam `AiGateway` calls through — a real `VertexAiClientImpl`, unmodified by the AI Gateway mission. |
| `memory` | `MemoryQueries` — the six standard lookups from `memory/README.md`'s decision table, promoted to named methods. |
| `curriculum` | `CurriculumLoader`, `RoadmapLevel` — reading `curriculum/java-roadmap.json`. |
| `rendering` | The rendering foundation (`RenderPlan`, `SceneRenderPlan`, `RenderAssetManifest`, `SubtitleTimeline`, `RenderPlanBuilder`, `AssetCollector`, `RenderValidator`) plus its real FFmpeg execution path in `rendering.ffmpeg` (`FfmpegRenderEngine`, `RenderCommandBuilder`, `SrtWriter`, `PlaceholderAssetResolver`). See "Rendering Foundation" and "Storyboard to MP4" below. |
| `job` | The durable job layer on top of `ReelExportService`: `ReelJob`, `ReelJobRepository`, `OutputPackagingService`, `OutputStorage`, `ReelJobReport`, `ReelJobService`. See "Reel Job System" below. |
| `runtime` | The single coordinator on top of `ReelJobService`: `ForgeBrainRuntime`, `RuntimeState`, `RuntimeReport`, `RuntimeReportWriter`. See "ForgeBrain Runtime" below. |
| `validation` | The Production Validation Suite: `PipelineInvariants`, `ArtifactValidator`, `ProductionReadinessReport`/`ProductionReadinessReportWriter`. See "Production Validation" below. |
| `gcp` | Live Google Cloud connectivity: `CloudConnectivityChecker`/`CloudConnectivitySmokeTestRunner`. See "Live GCP Configuration" below. |
| `analytics` | Two generations of analytics types side by side: `PerformanceSnapshot`/`StrategyPerformanceAggregate` (real audience/platform metrics — see `analytics/analytics-spec.md`, still not active, no publishing integration posts anywhere real yet) and `ReelOutcomeSnapshot`/`TopicPerformanceAggregate`/`DimensionPerformanceAggregate`/`AnalyticsReport` plus `AnalyticsAggregator`/`AnalyticsMemoryFeedback` (real pipeline-outcome analytics, active today — see "Analytics / Feedback Loop" below). |
| `publishing` | `PublishingMetadataGenerator`, `PlatformFormatter` (per-platform metadata formatting), and the `PlatformPublishAdapter` seam — dry-run (`AbstractDryRunPlatformPublishAdapter`, `YouTubeShortsPublishAdapter`, `InstagramReelsPublishAdapter`) and real (`YouTubeRealPublishAdapter`, `InstagramRealPublishAdapter`) implementations, chosen per platform by `PlatformPublishAdapterFactory`/`PlatformPublishAdapterBeanConfig`. See "Publishing" and "Real Platform Publishing" below. |

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

## AI Gateway

Every generative pipeline stage — Research, Lesson, Content Director, Script — calls `AiGateway`
(`ai/AiGatewayImpl.java`, the `AiGateway` bean) instead of `vertex/VertexAiClient` directly.
`VertexAiClient`/`VertexAiClientImpl` are completely unchanged; the gateway is a layer in front of
them, not a replacement. `AiGatewayImplTest` covers routing, retry, cache, validation, fallback,
and metrics against a mocked `VertexAiClient`; each stage's own `Vertex*ServiceImplTest` now wires
a real `AiGatewayImpl` (retries/caching disabled for deterministic tests) around that same mock,
so every prior fallback scenario (missing config, thrown exception, malformed JSON, incomplete
JSON) is still exercised exactly as before.

**Prompt Registry and model routing.** `PromptRegistryImpl` (`ai/PromptRegistryImpl.java`) builds
one `PromptDefinition` per stage — name, version, purpose, model id, temperature, max output
tokens, response MIME type — directly from `VertexAiConfig` at startup. A stage asks for its
prompt by name (`aiGateway.execute(new AiPromptExecution<>("research", promptText, variables,
VertexResearchContent.class, this::validate))`); the gateway resolves which model handles it from
the registry. Switching a model is purely an `application.yml` edit:

```
Research         → forgebrain.vertex-ai.research-model         (gemini-2.0-flash-001)
Lesson           → forgebrain.vertex-ai.lesson-model            (gemini-2.0-pro-001)
Content Director → forgebrain.vertex-ai.content-director-model  (gemini-2.0-flash-001)
Script           → forgebrain.vertex-ai.script-model            (gemini-2.0-pro-001)
```

Adding a future stage means adding one entry to `PromptRegistryImpl` and calling `AiGateway`, not
writing a new "check config, build request, call the client, parse, validate, fall back" block
from scratch.

**Retry.** `RetryExecutor` (`ai/RetryExecutor.java`, a plain class the gateway owns directly —
same "logic class owned by its orchestrator" convention as `QualityScorer`) retries a failed
attempt with exponential backoff — `forgebrain.ai-gateway.max-retries` (default `2`) attempts
after the first, delay starting at `initial-backoff-millis` (default `250`) and multiplying by
`backoff-multiplier` (default `2.0`) each time. A `ConfigurationException` (blank project id, or
any other "Vertex AI is not usable at all" signal from `VertexAiClientImpl`) or a blank model id
is **never** retried — it fails immediately as `AiGatewayException.Reason.CONFIGURATION`. Every
other failure (a thrown exception, a timeout, malformed JSON, or a failed validator) is retried,
then surfaces as `Reason.EXECUTION_FAILED` once retries are exhausted.

**Timeout.** Each Vertex AI call runs on a virtual thread with a bounded wait
(`forgebrain.ai-gateway.timeout-millis`, default `30000`); exceeding it is treated as a retryable
failure, same as any other transient error.

**Response validation.** Two layers, both reused rather than duplicated: the response must parse
into the stage's existing `Vertex*Content` record via the shared `ObjectMapper` (structural/schema
validation — a response that doesn't fit the shape fails to parse), and then, if the stage
supplies one, its own pre-existing `validate(...)` method runs as an `AiPromptExecution.validator`
— passed in as a plain method reference (`this::validate`), not reimplemented. Either failure is
retried exactly like a call failure.

**Fallback.** A stage's catch block is unchanged in spirit: `catch (AiGatewayException e)` branches
on `e.reason()` (`CONFIGURATION` logs at INFO, `EXECUTION_FAILED` logs at WARN with the full
exception), calls `aiGateway.recordFallbackUsed(promptName)`, then calls its own heuristic
`*ServiceImpl` — the exact same real, exercised fallback path every stage already had.

**Caching.** `AiResponseCache` (`ai/AiResponseCache.java`) is a pluggable seam —
`InMemoryAiResponseCache` is the only implementation today, a plain in-process map cleared on
restart, adequate for this phase's single-instance deployment. A cache key is a SHA-256 hash of
the prompt name, model id, and prompt text; an exact repeat reuses the previous raw response
without calling Vertex AI again (`forgebrain.ai-gateway.cache-enabled`, default `true`). A future
Redis- or Firestore-backed cache can implement the same interface and be swapped in as the sole
`@Component`, exactly like `OutputStorage`'s local-vs-cloud seam.

**Metrics.** `PromptMetricsRecorder` (`ai/PromptMetricsRecorder.java`, `InMemoryPromptMetricsRecorder`
the default implementation) tracks, per prompt name: invocation count, failures, fallback count,
cache hits, total retries consumed, average call duration, and an estimated total token count (a
documented `(prompt + response length) / 4` character-based proxy, not a real tokenizer — no
tokenizer is wired up in this phase). `AiGateway.metricsSnapshot()` returns the current snapshot
for every prompt executed so far.

**Future expansion.** Adding a real second AI provider (or a second model tier for an existing
stage) means adding a `PromptDefinition` and, if the provider isn't Vertex AI, a new
`VertexAiClient`-shaped implementation behind the same interface — no stage's own code changes.
Storyboard, Reviewer, and Publishing remain deterministic/mechanical stages with no AI Gateway
involvement, unchanged by this mission.

## Vertex AI Research Stage

The research stage (`services/VertexAiResearchServiceImpl`, the `ResearchService` bean) calls
Google Vertex AI to generate the parts of a topic brief that benefit from generation —
`topicSummary`, `coreConcepts`, `simpleAnalogy`, `beginnerExplanation`, `advancedNotes`,
`safetyNotes`. Every other `ResearchResult` field (`learningObjective`, `commonMisconceptions`,
`codeExampleIdeas`, `relatedTopics`, `prerequisites`, ...) still comes straight from the
curriculum's own curated data, unchanged from the original heuristic approach.

**How it works**: `ResearchPromptBuilder` builds a prompt grounded in the curriculum's
`learning_objective`/`common_mistakes`/`example_ideas`, asking for a strict JSON response.
`VertexAiResearchServiceImpl` hands that prompt to `AiGateway` under the `"research"` prompt name
(see "AI Gateway" above) — the gateway resolves the model, calls `VertexAiClientImpl` (which opens
a `VertexAI` client with `GenerationConfig`'s `responseMimeType` set to `application/json` and
calls `GenerativeModel.generateContent`, authenticating via Application Default Credentials — no
API keys in this repo), and parses the response into `VertexResearchContent` with the shared
snake_case `ObjectMapper` before this stage assembles the full `ResearchResult`.

**Fallback**: any `AiGatewayException` — missing `project-id`/`research-model` config, a failed
API call after retries, or a response that doesn't parse into `VertexResearchContent` — falls back
to `ResearchServiceImpl`'s original heuristic brief. This is a real, tested code path, not just a
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

The lesson stage (`services/VertexAiLessonServiceImpl`, the `LessonService` bean) calls `AiGateway`
under the `"lesson"` prompt name (model configured separately via
`forgebrain.vertex-ai.lesson-model`, defaulting to `gemini-2.0-pro-001` — a higher-tier model than
research, since narrowing a brief into a committed lesson is a bigger judgment call) to narrow a
research brief into the single-concept lesson blueprint required by `brain/lesson-spec.md` Section
4's "One of Everything" rule:
`lessonObjective`, `lessonSummary`, `keyPoints`, `stepByStepExplanation`, `coreExample`,
`analogy`, `commonMistakes`, `whatToAvoidSaying`, `beginnerTakeaway`, `retentionHook`,
`visualNotes`, and `confidenceNotes` all come from the model this time — unlike research, the
narrowing decision itself (which concept, which example, which mistake) is genuinely a judgment
call the model is asked to make and justify via `confidenceNotes.flaggedUncertainties`.
`topicId`/`topicTitle`/`audienceLevel`/`targetDurationSeconds` are still carried straight from
the research brief, and `teachingStyle` is still decided deterministically (the caller's request,
or the same heuristic `LessonServiceImpl` uses) before the prompt is built, not by the model.

**Fallback**: identical pattern to research — any `AiGatewayException` (missing
`project-id`/`lesson-model` config, a failed API call after retries, or a response that doesn't
parse into `VertexAiLessonContent`) falls back to `LessonServiceImpl`'s original heuristic
narrowing. Real, exercised path, not a stub.

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
`ContentDirectorService` bean) calls `AiGateway` under the `"content-director"` prompt name (model
configured separately via `forgebrain.vertex-ai.content-director-model`, defaulting to
`gemini-2.0-flash-001`) to make all seven directorial decisions over a committed `Lesson`: `hookType`/`hookReason`,
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

**Fallback**: identical pattern to research and lesson — any `AiGatewayException` (missing
`project-id`/`content-director-model` config, a failed API call after retries, or a response that
doesn't parse into `VertexAiContentStrategy`) falls back to `ContentDirectorServiceImpl`'s original
deterministic rule-based strategy. Real, exercised path, not a stub.

**What remains heuristic**: Storyboard is still a deterministic, mechanical stage — unchanged by
this. Script is now Vertex AI-backed too — see "Vertex AI Script Stage" below.

**Configuration**: `forgebrain.vertex-ai.content-director-temperature` (default `0.4`),
`content-director-max-output-tokens` (default `2048`), and
`content-director-response-mime-type` (default `application/json`) tune generation for this
stage specifically. No new local setup beyond the research stage's ADC steps above.

## Vertex AI Script Stage

The script stage (`services/VertexAiScriptServiceImpl`, the `ScriptService` bean) calls `AiGateway`
under the `"script"` prompt name (model configured separately via
`forgebrain.vertex-ai.script-model`, defaulting to `gemini-2.0-pro-001`, the same higher tier as
lesson generation) to turn a committed `Lesson` and its binding `ContentStrategy` into actual
spoken narration and on-screen text: `hook`, `introLine`, `mainScript`,
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

**Fallback**: identical pattern to research, lesson, and content director — any
`AiGatewayException` (missing `project-id`/`script-model` config, a failed API call after retries,
or a response that doesn't parse into `VertexAiScriptContent`) falls back to `ScriptServiceImpl`'s
original deterministic templates. Real, exercised path, not a stub.

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
library, no SaaS, no Docker. `RenderCommandBuilder` composes the filter graph; a set of small,
pure "creator-quality layer" classes sit on top of it so a reel reads as an edited short rather
than a static slide deck, without any change to `RenderPlan`/`SceneRenderPlan`/`Storyboard`
themselves — the fields these classes consume (`sceneType`, `codeBlock`, `motionNotes`,
`transitionIn`/`transitionOut`, `highlightedWords`, per-cue `emphasisWords`) already existed on
the model, unused, before this layer was built:

- **`CameraMotion`** — the background canvas renders oversized and is cropped down to the exact
  output size with a slow, continuous sinusoidal offset, so the whole reel has a subtle, constant
  pan instead of one dead-static frame.
- **`SceneVisualTemplate`** — a per-`Scene.SceneType` accent color, heading size, optional accent
  card, and short "kicker" label (e.g. `STEP BY STEP`, `WATCH OUT`), so hook/setup/code/step/
  mistake/comparison/recap/CTA scenes each read as a visually distinct beat rather than the same
  template repeated.
- **`TextAnimator`** — every text layer slides up and fades in over its first ~0.35s instead of
  appearing fully-formed the instant its scene starts.
- **`CodeBlockRenderer`** — code scenes render an IDE-style card with *every* source line (not
  just the focus line), with the line matching `CodeBlock.focusLine()` picked out by its own
  highlighted background bar and accent text color.
- **`DiagramRenderer`** — a scene whose on-screen text is a list of short items (e.g. `JVM` /
  `Bytecode` / `Machine Code`) renders as stacked accent cards connected by a plain vertical bar,
  instead of the same items stacked as static paragraphs. The connector is a solid bar, not an
  arrow glyph, so it never depends on the active font having that glyph.
- **`AssSubtitleWriter`** — subtitles burn in from `.ass` (Advanced SubStation Alpha), not `.srt`,
  so each cue's `emphasisWords` render with an inline color override instead of being lost.
- **`ThumbnailCommandBuilder`** — the thumbnail is a dedicated synthetic frame (the hook scene's
  text, bold and outlined, over an accent band) built directly from a `lavfi` color source, not a
  raw mid-video frame grab.

One hard, environment-verified constraint governs every one of these: `drawtext`'s `fontsize` is
**never** driven by a time expression anywhere in this codebase. Animating it was found to
segfault the target `ffmpeg` build (preceded by a `Fontconfig error` log line) during development
of this layer. `y` (position) and `alpha` (opacity) are animated freely instead — that combination
was verified safe — so "punch" comes from per-scene-type size/color/card choices, not from
resizing text at runtime.

A short 0.3s fade in/out remains the one reel-wide transition; every current storyboard scene
already uses a hard cut between scenes, which a timed `enable=` window reproduces natively.
`Scene.TransitionStyle`'s other values (`QUICK_FADE`/`SLIDE`/`ZOOM_PUNCH`/`MATCH_CUT`) are still
declared but not dispatched to distinct filter treatments — the slide-and-fade entrance every
scene already gets from `TextAnimator` covers the "feels edited" goal without that added
complexity; per-transition-type filters remain a documented next step, not a gap in this pass.

**Local FFmpeg requirement**: a working `ffmpeg` binary must be installed and on `PATH` (override
via `forgebrain.rendering.ffmpeg-path` if it isn't). Verified against FFmpeg 8.x with `libx264`,
`libass` (subtitle burn-in), and `libfreetype` (`drawtext`) enabled — the default build from
[gyan.dev](https://www.gyan.dev/ffmpeg/builds/) or most package managers includes all three.
Without it, `FfmpegRenderEngine` throws a `RenderExecutionException` naming the configured path,
rather than failing silently.

**Audio**: when a `RenderPlan` was built via the enriched, reconciled `RenderPlanBuilder.build(Storyboard,
VoiceResult, SubtitleResult, AssetManifest)` overload (see "End-to-End Reel Export" below),
`RenderPlan.audio().voiceoverTrackRef()` is already a real file — either `VoiceServiceImpl`'s
silent placeholder or `GoogleCloudTextToSpeechVoiceServiceImpl`'s real narration, see "Voice
Generation" below. `PlaceholderAssetResolver`'s own lookup only matters for the simpler,
standalone `RenderPlanBuilder.build(Storyboard)` path. Either way, the underlying convention is
the same: a real file at `forgebrain.rendering.voiceover-assets-directory/<topicId>.{mp3,wav}`.

**Placeholder-safe assets**: when a `RenderPlan` comes from the enriched builder, fonts/music/watermark
come from `AssetServiceImpl`'s resolved `AssetManifest` (see "Asset Management" below), which
checks `forgebrain.rendering.assets-directory` for real catalog files before falling back to the
same deterministic, `RenderStyle`-keyed names as before.

**What's still needed**: a real, populated asset catalog (font files, background media, licensed
music) behind `AssetServiceImpl`, ideally backed by Cloud Storage in a real deployment rather than
a local directory; generated illustrations/icons/logos beyond the card/diagram/gradient treatment
`CodeBlockRenderer`/`DiagramRenderer` already provide; per-transition-type filters (see above); and
wiring `RendererService`/`RenderJob` around this engine for asynchronous job tracking (still just
an interface — this path runs synchronously).

## Voice Generation

`VoiceServiceBeanConfig` (`services/VoiceServiceBeanConfig.java`) is the single place that decides
which `VoiceService` bean is active — mirrors `PlatformPublishAdapterBeanConfig`'s dry-run-vs-real
platform adapter selection exactly:

- **`forgebrain.text-to-speech.enabled: false`** (the default) — `VoiceServiceImpl` is active. Two
  real, exercised paths: if a real narration file exists at
  `forgebrain.rendering.voiceover-assets-directory/<topicId>.{mp3,wav}`, its real duration is
  measured via `ffprobe` and distributed across scenes proportionally to each scene's estimated
  share of the storyboard's total; otherwise a real silent WAV is synthesized via `ffmpeg` at
  exactly the storyboard's total estimated duration, so every scene's `actualDurationSeconds`
  equals its estimate (zero drift, honestly reported).
- **`forgebrain.text-to-speech.enabled: true`** — `GoogleCloudTextToSpeechVoiceServiceImpl` is
  active (`renderer/voice-spec.md` Section 6, mission Part 6). It synthesizes each scene's
  `voiceoverText` as its own clip via the Google Cloud Text-to-Speech Java client, measures each
  clip's real duration via `ffprobe`, then concatenates every clip in scene order into one combined
  file with `ffmpeg`'s concat demuxer — preserving the existing convention that every `SceneAudio`
  in one `VoiceResult` references the same single audio file, which `RenderPlanBuilder`'s
  reconciled path depends on to place scenes back-to-back by real duration. Any synthesis failure
  (missing credentials, quota, network) falls back to `VoiceServiceImpl`'s silent track — narration
  never blocks a render. Configure the voice under `forgebrain.text-to-speech.*` (`language-code`,
  `voice-name`, `speaking-rate`, `pitch`); authentication is Application Default Credentials, same
  as every other live Google Cloud call in this codebase — see "Live GCP Configuration" below.
  Enabled by default under the `cloud` profile.

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
QUEUED → RUNNING → VALIDATING → RENDERING → REVIEWING → PACKAGING → PUBLISHING → COMPLETED
                                                                                ↘ FAILED (from any stage)
```

`QUEUED` (job created) → `RUNNING` (AI pipeline through render-plan construction) → `VALIDATING`
(`RenderValidator`) → `RENDERING` (`RenderEngine`) → `REVIEWING` (`ReviewerService` — see
"Reviewer / Quality Gate" below) → `PACKAGING` (`OutputPackagingService`) → `PUBLISHING`
(`PublishingService`, only for an `APPROVED` review — see "Publishing" below) → `COMPLETED`.
Each `ReelJob` carries: `jobId` and a separate `pipelineRunId` (a distinct correlation id per
execution attempt — see the class javadoc for why "rerun" means "submit a new job" today, not
retry-in-place), topic id/title, `createdAt`/`startedAt`/`completedAt`/`duration`,
`outputDirectory`, `outputFiles` (category → stored reference), `failureReason`, `warnings`,
`fallbackStages` (which stages used a documented fallback), `renderChecksum` (from the rendered
`VideoPackage`, once rendering completes), `reviewVerdict`/`recommendedAction` (from the
`REVIEWING` stage, once it completes), and `publishingStatus` (from the `PUBLISHING` stage, or
`"SKIPPED_NOT_APPROVED"` if the review verdict never reached `APPROVED`).

Once a job reaches `COMPLETED` or `FAILED`, `ReelAnalyticsService` captures a durable outcome
snapshot and feeds a signal back into memory — not a lifecycle status of its own (it runs after
the job record is already final), but every job passes through it. See "Analytics / Feedback
Loop" below.

**`ReelJobService.submitJob()` never throws** for a pipeline/render/review/packaging/publishing
failure — unlike `ReelExportService.exportReel()`, it always returns a `ReelJob`, with `Status
.FAILED` and `failureReason` set when something went wrong. That's the intended job-system
contract: inspect the returned record's status instead of wrapping the call in try/catch. A
**rejected or needs-revision review verdict is not a job failure** — the job still reaches
`COMPLETED` (a `REVIEW: ...` warning is added instead, and `PUBLISHING` is skipped rather than
attempted), since scoring a reel poorly is a legitimate content decision, not a broken pipeline
run; see "Reviewer / Quality Gate" and "Publishing" below.

### Reviewer / Quality Gate

`ReviewerServiceImpl` (`services/ReviewerServiceImpl.java`) is the pipeline's final quality gate
— it runs once per job, right after `RENDERING` and before `PACKAGING`, and never touches
rendering or AI-pipeline logic itself. It combines two independent checks, never averaged
together:

- **Hard gates** — the script must not contain any statement `Lesson.whatToAvoidSaying` flags,
  and every output artifact (video, thumbnail if present, subtitles) must exist on disk. Either
  failure alone forces a `REJECTED` verdict, regardless of how well everything else scored.
- **Scored judgment** — `QualityScorer` (`services/QualityScorer.java`) computes nine 0–1
  dimensions (`technical_accuracy`, `pacing_fit`, `hook_strength`, `educational_clarity`,
  `production_polish`, `brand_consistency`, `visual_readability`, `subtitle_quality`,
  `retention_potential`) from real pipeline artifacts — script text, voice drift, subtitle
  timing, the resolved asset manifest — and a configured weighted average, `overall_score`. If
  `overall_score` or any single dimension falls below its configured threshold
  (`forgebrain.reviewer.*` in application.yml), the verdict is `NEEDS_REVISION` rather than a
  rejection: the reel isn't necessarily wrong, just not good enough yet.

Every check is mechanical and explainable — no AI judgment call is involved in this first
version, per this mission's "keep the reviewer deterministic and explainable." `hook_strength`
and `retention_potential` are the weakest proxies (hook word count/strategy match, and an
average of `hook_strength`/`pacing_fit`, respectively) and are flagged as such in both
`dimension_notes` and the review's `confidence_notes`.

The resulting `ReviewResult` carries a `verdict` (`APPROVED`/`NEEDS_REVISION`/`REJECTED`) and a
`recommendedAction` — `APPROVE`, `REJECT` (hard gate), `REGENERATE_SECTION` (every quality issue
traces back to one upstream stage), or `REGENERATE_FULL` (issues span multiple stages, or none
could be pinned down) — deterministically derived from the verdict and each issue's
`suggestedStageToRevisit`. **When a reel is rejected or needs revision**, the job still completes
normally; the full `ReviewResult` (score, per-category scores, issues, warnings, errors,
reviewer notes) is persisted in `ReelJobReport.reviewResult()`, and `ReelJob.reviewVerdict()` /
`recommendedAction()` surface the outcome at a glance without opening the report. Nothing
downstream acts on a rejection automatically — regeneration and publishing are both future work
(see "What's Deliberately Not Here").

`ReviewerService.selectBest(List<ReviewResult>)` picks the best of several reviewed variants of
the same topic (preferring a better verdict before a higher score) — a lightweight seam for a
future multi-variant pipeline, not wired to anything today since nothing currently generates more
than one variant per topic.

### Output Packaging

`OutputPackagingServiceImpl` (`job/OutputPackagingServiceImpl.java`) turns one render's raw
working-directory files into a structured package: writes `metadata.json` (the run's
`VideoPackage`, serialized — same format the sync path already uses), then passes every file
(`reel.mp4`, `thumbnail.jpg`, `subtitles.srt`, `metadata.json`, and later `report.json`) through
the `OutputStorage` seam. Category keys are consistent across both this layer and the sync path's
report (`"video"`, `"thumbnail"`, `"subtitles"`, `"metadata"`, `"report"`), so nothing downstream
needs to know which stage produced which file.

### Publishing

`PublishingServiceImpl` (`services/PublishingServiceImpl.java`) is the last stage in the job
lifecycle, running only when the Reviewer's verdict was `APPROVED` — a `REJECTED` or
`NEEDS_REVISION` verdict skips it entirely (`ReelJob.publishingStatus() == "SKIPPED_NOT_APPROVED"`,
a warning recorded, no job failure). Calling it against anything but an `APPROVED` `ReviewResult`
throws a `PipelineStageException` — this precondition is enforced in code, not just documented.

**What a publishing package contains.** `PublishingPackage` (`models/PublishingPackage.java`)
bundles: the job id, the review verdict it was built from, file references (video, thumbnail,
subtitles), default title/description/hashtags/tags (`PublishingMetadata`, built deterministically
from the lesson and script by `publishing/PublishingMetadataGenerator.java` — title from the
script's hook, falling back to the lesson's retention hook then topic title; description from the
lesson objective plus beginner takeaway), and one `PlatformVariant` per registered platform
adapter. It's written as `publishing-package.json` under the job's `publishing/` subdirectory,
then stored through the same `OutputStorage` seam as every other output file
(`outputFiles["publishing_package"]`).

**Platform adapters.** `PlatformPublishAdapter` (`publishing/PlatformPublishAdapter.java`) is the
seam: one adapter per platform, resolved per publish call by `PlatformPublishAdapterFactory` (see
"Real Platform Publishing" below) rather than looked up from a single fixed map — each platform
has both a dry-run adapter (`YouTubeShortsPublishAdapter`/`InstagramReelsPublishAdapter`, writing
`<platform>-payload.json` — the exact payload a real call would send — to a local file instead of
calling a platform API) and a real one (`YouTubeRealPublishAdapter`/`InstagramRealPublishAdapter`),
and the factory picks which one runs. Every dry-run payload is stored through `OutputStorage`
(`outputFiles["publishing_youtube_shorts"]` / `["publishing_instagram_reels"]`).
`PublishingResult.status` is `READY` (every adapter succeeded), `PARTIAL_FAILURE`, or `FAILED` —
one platform's failure never blocks the others, real or dry-run alike.

### Real Platform Publishing

`YouTubeRealPublishAdapter` and `InstagramRealPublishAdapter` (`publishing/`) implement
`PlatformPublishAdapter` directly (not `AbstractDryRunPlatformPublishAdapter`) and call the real
YouTube Data API v3 and Instagram Graph API. Both are plain classes, not `@Component`s — like
`YouTubeShortsPublishAdapter`/`InstagramReelsPublishAdapter`, they're constructed explicitly by
`PlatformPublishAdapterBeanConfig` (mirroring `OutputStorageBeanConfig`'s local-vs-cloud storage
wiring exactly), since with two adapters per platform Spring would otherwise have no unambiguous
bean to autowire.

**Selection.** `PlatformPublishAdapterFactory` (`publishing/PlatformPublishAdapterFactory.java`,
plain and Spring-free, directly unit-tested) decides real vs. dry-run per platform, deterministically:
the dry-run adapter runs unless `forgebrain.publishing.upload.dry-run-only` is `false` **and** that
platform's own `enabled` flag is `true` **and** a real adapter is actually registered for it.
`dry-run-only: true` is the default in every committed profile, so the dry-run path is exactly what
runs — and what every test exercises — until someone explicitly opts a specific platform in.

**Configuration** (`forgebrain.publishing.upload.*`, bound by `PlatformUploadConfig`) — structure
and secret *references* only, no real value committed (see docs/CONFIGURATION.md Section 5):

```yaml
forgebrain:
  publishing:
    upload:
      dry-run-only: true          # global override; false is required before any real upload happens
      youtube:
        enabled: false            # per-platform opt-in
        client-id: ""             # OAuth 2.0 client id
        client-secret: ""         # OAuth 2.0 client secret
        refresh-token: ""         # long-lived refresh token, youtube.upload scope
        channel-id: ""            # informational/traceability only
        privacy-status: "private" # never defaults to "public"
        category-id: "27"         # Education
      instagram:
        enabled: false
        access-token: ""          # long-lived Graph API token, instagram_content_publish scope
        ig-user-id: ""
        publish-poll-interval-seconds: 5
        publish-poll-max-attempts: 10
```

**YouTube upload flow.** Exchanges `refresh-token` for a short-lived access token
(`https://oauth2.googleapis.com/token`), then `POST`s a multipart request (JSON metadata part +
the reel's `.mp4`) to the Data API's `videos.insert` upload endpoint — the minimal upload flow
appropriate for a short video, not the chunked/resumable protocol meant for multi-gigabyte files.
On success, `PlatformPublishOutcome.payloadReference()` holds the real YouTube video id.

**Instagram upload flow.** The Graph API's documented three-step Reels flow: create a media
container from a video URL, poll it until Instagram finishes processing, then publish the
container. **The Graph API requires a URL it can fetch itself** — if `videoFileUri` isn't an
`http(s)://` URL (i.e. local storage rather than Cloud Storage is in use), the adapter reports a
clear failed outcome instead of attempting a call that could never succeed, naming exactly what's
missing. On success, `payloadReference()` holds the real published media id.

**Neither real adapter has been exercised against its live API** — this project has no real
YouTube or Instagram credentials. Both are a well-reasoned first cut against each platform's
documented API shape, unit-tested end-to-end against a mocked HTTP layer
(`MockRestServiceServer`), not a verified integration.

**Missing credentials.** A platform enabled (`enabled: true`, `dry-run-only: false`) without its
required credentials never throws or crashes the pipeline — the real adapter returns a failed
`PlatformPublishOutcome` naming exactly which field is blank (e.g. `"missing configuration:
client-id, refresh-token"`), which flows into `PublishingResult.errors()` and the job's warnings
exactly like any other adapter failure. A platform that's simply not enabled is not an error at
all; it silently uses the dry-run adapter, same as every committed profile today.

**What's unchanged:** the review-gate precondition, `PublishingPackage` generation, and the job
orchestration flow in `ReelJobServiceImpl` — `PublishingServiceImpl` still resolves one adapter per
platform and collects outcomes the exact same way; it has no idea whether the adapter it just
called was real or dry-run.

### Analytics / Feedback Loop

`ReelAnalyticsServiceImpl` (`services/ReelAnalyticsServiceImpl.java`) closes the loop the rest of
this document describes as forward-only: it captures what happened to every job — successful,
rejected, needs-revision, or failed — and feeds the useful parts back into memory so the next
topic-selection run can act on it. It's distinct from the pre-existing, still-dormant
`AnalyticsService`/`PerformanceSnapshot` (real audience/platform metrics — no publishing
integration posts anywhere real yet, see `analytics/analytics-spec.md`); this component works
entirely from signals the pipeline already produces today.

**What gets captured.** After every job (called from `ReelJobServiceImpl`, right after the job's
own report is written, in both the success and failure paths — best-effort, so an analytics
failure never turns a successfully recorded job into a failure), a `ReelOutcomeSnapshot`
(`analytics/ReelOutcomeSnapshot.java`) is written to
`<forgebrain.analytics.snapshots-directory>/<jobId>.json`: topic id/title, hook type and teaching
style (from the job's `Script`/`ContentStrategy`), render duration, review verdict and score,
publish status, output artifact references, warning count, fallback usage, and failure reason if
any. Each snapshot is classified into one deterministic `Outcome` — `PUBLISHED`,
`PUBLISH_FAILED`, `NEEDS_REVISION`, `REJECTED`, or `FAILED` — from `ReelJob.status()` and the
review verdict, the same signal the aggregation and memory-feedback logic below groups by.

**Aggregation.** `AnalyticsAggregator` (`analytics/AnalyticsAggregator.java`, a plain class like
`QualityScorer`, not a Spring bean) rolls snapshots up by topic (`TopicPerformanceAggregate`:
average review score, approval/rejection/revision/fallback rates, a chronological trend —
`IMPROVING`/`DECLINING`/`STABLE`/`INSUFFICIENT_DATA` below two reviewed snapshots — and a
`revisionPriorityScore`) and by hook type, teaching style, or platform target
(`DimensionPerformanceAggregate`, the pipeline-outcome sibling of the still-dormant
`StrategyPerformanceAggregate`, kept separate since the two would otherwise mean different things
— internal review score vs. real engagement — under the same field names). All of it is
mechanical arithmetic, no AI judgment, same philosophy as the Reviewer's scoring.

**Memory feedback.** `AnalyticsMemoryFeedback` (`analytics/AnalyticsMemoryFeedback.java`, a pure
function, unit-tested without any file I/O) turns one snapshot plus its topic's aggregate into an
updated `MemoryState.TopicRecord`, written through the existing `MemoryService.updateTopicRecord`
— no new memory storage mechanism, no changes to `TopicSelectorImpl`, which already reads
`performanceScore`, `priority`, `avoidUntil`, and `status` directly. Concretely: a rejected reel
forces `status = NEEDS_REVISIT`, `priority = HIGH`, and a cooldown (`avoidUntil`, configurable
days); a topic whose aggregate `revisionPriorityScore` crosses
`forgebrain.analytics.revision-priority-high-threshold` gets the same treatment even without a
fresh rejection; a successfully published reel sets `status = POSTED` (the first code path in
this repository that ever does) with `priority = LOW` for a strong, consistent performer or
`NORMAL` otherwise; `performanceScore` is updated via a configurable exponential moving average
so one bad reel doesn't erase a topic's history. **Curriculum truth is never touched** — title,
difficulty, `timesUsed`, `lastUsedAt`, and `relatedTopics` are always carried forward from the
existing record untouched; only the fields this component owns (`performanceScore`,
`revisionCount`, `priority`, `avoidUntil`, `status`, `notes`) are written.

**Reporting.** `ReelAnalyticsService.generateReport(windowStart, windowEnd)` aggregates every
snapshot in the window into an `AnalyticsReport` — top-performing and weak topics, topics with a
declining trend ("topic drift"), review and publish-readiness trends, hook/teaching-style/platform
performance, and recommended-revisit topic ids (the same `revisionPriorityScore` threshold memory
feedback uses) — written to `<forgebrain.analytics.reports-directory>` as both
`<reportId>.json` and a short `<reportId>.md` summary.

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

`ReelJob.Status.COMPLETED` with `reviewVerdict == "APPROVED"` and `publishingStatus == "READY"`
is proof a reel went all the way from topic selection through a real, inspectable publishing
package — with real YouTube/Instagram upload adapters now behind the same `PlatformPublishAdapter`
seam (see "Real Platform Publishing" above), used the moment `forgebrain.publishing.upload.*` is
configured with real credentials and switched on. What remains is exclusively **obtaining those
real credentials** (OAuth client/refresh token for YouTube, a long-lived Graph API token for
Instagram) and exercising both adapters against a live account at least once — this sandbox has
neither — plus actually scheduling/triggering a post on a timer, still deliberately out of scope
(see `TODO.md` 1.13/1.14).

Every job, regardless of outcome, also closes back onto the next `TopicSelectorImpl` run — see
"Analytics / Feedback Loop" above. What remains there is exclusively **real audience data**: the
`AnalyticsService`/`PerformanceSnapshot` pair is still fully dormant, waiting on a real publishing
integration to post somewhere and a real platform metrics API (YouTube Analytics, Instagram
Insights) to pull view/watch-time/engagement numbers from — everything analytics does today works
from pipeline-internal signals alone, which is a real but partial substitute for that.

## ForgeBrain Runtime

Every stage above — curriculum through memory update — already runs inside one
`ReelJobService.submitJob()` call. The Runtime (`runtime/`) is the layer above that: the single
coordinator that turns "call this service correctly" into "run ForgeBrain," executing a whole
batch of reels autonomously and producing one report for the batch instead of one job at a time.
It does not reimplement or change any stage — it is purely an orchestration layer around the
existing `ReelJobService`, `ReelAnalyticsService`, and `MemoryService` beans.

### Execution Flow

```
ForgeBrainRuntime.run()
  → for each of RuntimeConfig.dailyReelCount() reel slots (up to `parallelism` concurrently):
      → ReelJobService.submitJob()
          Curriculum → Memory → Topic Selection → Research → Lesson → Content Director
          → Script → Storyboard → Voice → Subtitles → Assets → Render → Review → Publishing
          → Analytics capture → Memory update
      → COMPLETED: record success
      → FAILED: retry (up to RuntimeConfig.maxRetriesPerReel()), then record failure and
        move on to the next slot — a failed reel never stops the batch
  → ReelAnalyticsService.generateReport(batchStart, batchEnd) — one AnalyticsReport for exactly
    this batch, reused as-is, not recomputed
  → MemoryService.getTopicRecord(...) for every distinct topic touched — the resulting state
  → RuntimeReportWriter writes one RuntimeReport
```

Every arrow above already existed before this mission except the first and last: `ForgeBrainRuntimeImpl`
(`runtime/ForgeBrainRuntimeImpl.java`) calls `ReelJobService.submitJob()` in a loop and calls
`ReelAnalyticsService`/`MemoryService` once more at the end — nothing in between is new or
modified.

### How to Run ForgeBrain

`forgebrain run`'s Spring Boot equivalent — disabled by default, like every other runner in this
project:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.runtime.run-on-startup=true
```

`ForgeBrainRuntimeCommandLineRunner` never throws — `ForgeBrainRuntime.run()` tolerates every
individual reel failure internally and always returns a `RuntimeReport`; the application keeps
running afterward (no `System.exit`), matching `PipelineCommandLineRunner`/
`ReelExportCommandLineRunner`/`ReelJobCommandLineRunner`'s exact pattern.

Programmatically: inject `ForgeBrainRuntime` into any Spring-managed component and call `run()` —
see `ForgeBrainRuntimeIntegrationTest` for a real `@SpringBootTest` proof, and
`ForgeBrainRuntimeImplTest` for fast, mocked multi-reel/retry/failure-recovery coverage.

### How to Configure the Runtime

`RuntimeConfig` (`forgebrain.runtime.*`):

```yaml
forgebrain:
  runtime:
    run-on-startup: false     # forgebrain.runtime.run-on-startup — read directly, not a RuntimeConfig field
    daily-reel-count: 1       # how many reels one run() call attempts
    parallelism: 1            # concurrent reel slots — see the caveat below before raising this
    max-retries-per-reel: 1   # additional submitJob() attempts before a slot is recorded failed
    retry-backoff-millis: 2000
    runtime-mode: "manual"    # informational label echoed into the report
```

**`parallelism` above 1 is experimental.** The file-based `MemoryService`/topic-selection path
(`TopicSelectorImpl`, `MemoryServiceImpl`) was built for one writer at a time — its individual
methods are synchronized, but "read memory, select a topic, mark it in-progress" is not atomic
across two concurrent `submitJob()` calls, so two reel slots could race to select the same topic.
The default (`1`) avoids this entirely; raising it is a deliberate, documented tradeoff, not a
verified-safe feature.

**Deliberately not duplicated here:** review threshold (`forgebrain.reviewer.approval-threshold`),
publish mode/dry-run (`forgebrain.publishing.upload.*`), Vertex model selection
(`forgebrain.vertex-ai.*`), and storage mode (`forgebrain.cloud-storage.enabled`) each already have
a dedicated config class. `ForgeBrainRuntimeImpl` reads all four directly and echoes their live
values into every `RuntimeReport.configSnapshot()` for visibility, rather than owning a second,
driftable copy — see `RuntimeConfig`'s javadoc.

### Runtime Reports

`RuntimeReportWriter` writes one `<runtimeId>.json` under
`<forgebrain.local-storage.execution-report-directory>/runtime/` per `run()` call — mirrors
`ReelJobReportWriter`'s convention. Every `RuntimeReport` carries: a runtime id, start/end time and
duration, reels requested/completed/failed, one `ReelExecutionSummary` per reel slot (job id,
topic, status, review verdict, publishing status, attempts taken, failure reason), a
publish-status tally across the batch, the batch's `AnalyticsReport`, the resulting
`MemoryState.TopicRecord` summary for every topic touched, and every warning/error recorded along
the way (a retried reel's earlier failures included, not just the final outcome). `RuntimeState`
(`runtime/RuntimeState.java`) is the mutable accumulator behind all of this while a run is still
in progress — the same "mutable in-progress / immutable finished snapshot" split as
`PipelineContext`/`PipelineResult` for one pipeline run, just at the batch level.

## Production Validation

`validation/` is a Production Validation Suite over the pipeline the Runtime already executes —
not a new pipeline stage, not a replacement for any existing test. It never calls a service or
re-runs a stage; it only inspects records the pipeline already produces (`ReelJobReport`,
`ReelJob`, `RuntimeReport`, `ReelOutcomeSnapshot`, `MemoryState.TopicRecord`,
`PublishingPackage`, `AnalyticsReport`).

**What it covers.**

- `PipelineInvariants` (`validation/PipelineInvariants.java`) — six reusable, pure assertions,
  each returning the violations found (empty = held): every stage runs at most once per reel,
  stages execute in canonical order, a completed job has every required output artifact, publishing
  only ever ran after an `APPROVED` review, an analytics snapshot's publish status agrees with the
  job's own, and memory reflects an analytics snapshot's review score.
- `ArtifactValidator` (`validation/ArtifactValidator.java`) — structural/consistency checks for
  the four artifact shapes this suite validates: the runtime report, the render portion of a job
  report, a publishing package, and an analytics report.
- `ProductionReadinessReport`/`ProductionReadinessReportWriter` — aggregates every check above
  into one pass/fail report, written as JSON under
  `<forgebrain.local-storage.execution-report-directory>/validation/<validationId>.json` — mirrors
  `RuntimeReportWriter`'s convention.

**Test coverage** (`src/test/.../validation/`), matching each of this suite's parts:

| Test class | Covers |
| --- | --- |
| `ProductionValidationSuiteTest` | Part 1 — runs the real `ForgeBrainRuntime` end to end (real curriculum, real `ffmpeg`, no real cloud credentials) and confirms every subsystem was genuinely reached: topic selection occurred, the AI Gateway was actually invoked for all four generative prompts (via `AiGateway.metricsSnapshot()`, not just log-watching), rendering completed, review executed, publishing was reached and stayed dry-run, analytics executed, and a `RuntimeReport` was produced — then runs every invariant/artifact check against the real result and writes a `ProductionReadinessReport`. |
| `PipelineInvariantsTest`, `ArtifactValidatorTest` | Part 2, Part 4 — each check's positive and negative cases, as fast fixture-based unit tests. |
| `ProductionValidationFailureScenariosTest` | Part 3 — one fixture per named failure type (AI, render, reviewer rejection, publishing, analytics), proving each is shaped correctly *and* that the checks catch a deliberately broken fixture of the same shape (e.g. publishing run despite a rejected review) — the "detect regressions between subsystems" goal. The Runtime's own retry/continue-on-failure behavior is already covered by `ForgeBrainRuntimeImplTest` and isn't repeated here. |
| `ConfigurationValidationTest` | Part 5 — boots the real application (`SpringApplicationBuilder`, no embedded server) under each named configuration: default dry-run, publishing disabled per platform, real upload enabled with missing credentials (starts fine; fails at publish time, not startup), local storage, cloud storage enabled without a bucket (fails fast, as designed), and custom runtime/AI Gateway retry configuration. |
| `ProductionReadinessReportWriterImplTest` | Confirms the readiness report writes, and round-trips, correctly. |

**How to execute it.**

```bash
./mvnw test -Dtest="com.forgebrain.backend.validation.*"
```

Or as part of the full suite (`./mvnw test`) — no real cloud credentials are required for any of
it; `ProductionValidationSuiteTest` needs a local `ffmpeg` binary (same requirement as
`ReelJobServiceImplTest`/`ForgeBrainRuntimeIntegrationTest`) and skips itself via
`Assumptions.assumeTrue` if one isn't found.

**Expected outputs.** A green run of `ProductionValidationSuiteTest` writes one
`ProductionReadinessReport` (`passed: true`, every check's violation list empty) alongside the
`RuntimeReport` it validated, both under `reports/` in whatever `execution-report-directory` is
configured. A regression in any covered invariant fails that specific named check in the report
(and the test), rather than a generic "something broke."

## Live GCP Configuration

ForgeBrain has a real Google Cloud project behind it: **project `forgebrain-prod`**, region
`us-central1`, **bucket `forgebrain-artifacts`** — Vertex AI, Cloud Storage, and Secret Manager
APIs enabled, a service account created, and **Application Default Credentials already
configured** on the machines that need them. None of that changes how local development or CI
work: every committed profile still defaults to fully local/dry-run, exactly as documented
throughout this README — this section is about the opt-in path to the real project, not a new
default.

**Wiring.** `GcpConfig` (`config/GcpConfig.java`, `forgebrain.gcp.*`) is the one place that answers
"is cloud mode on, and for which project" — `projectId`, `region`, `storageBucket`,
`vertexAiEnabled`, `gcsEnabled`. It doesn't replace `VertexAiConfig`/`CloudStorageConfig`, which
remain exactly what `VertexAiClientImpl`/`CloudStorageOutputStorage` read to do real work,
unchanged — `GcpConfig` exists because Vertex AI had no explicit enablement flag of its own before
this (only a reactive blank-project-id check inside `VertexAiClientImpl`), and because operators
want one place to see the live target at a glance.

**Enabling it.** A new `cloud` Spring profile (`application-cloud.yml`) turns on
`forgebrain.gcp.vertex-ai-enabled`, `gcs-enabled`, and `forgebrain.cloud-storage.enabled` — the
identifying values themselves (`forgebrain-prod`, `forgebrain-artifacts`) are **not committed**,
per docs/CONFIGURATION.md Section 5 and `application-local.yml`'s identical convention; supply
them as environment variables when the profile is active:

```bash
export FORGEBRAIN_GCP_PROJECT-ID=forgebrain-prod
export FORGEBRAIN_GCP_STORAGE-BUCKET=forgebrain-artifacts
export FORGEBRAIN_VERTEX-AI_PROJECT-ID=forgebrain-prod
export FORGEBRAIN_CLOUD-STORAGE_PROJECT-ID=forgebrain-prod
export FORGEBRAIN_CLOUD-STORAGE_MEDIA-BUCKET=forgebrain-artifacts

./mvnw spring-boot:run -Dspring-boot.run.profiles=cloud
```

With ADC already configured (`gcloud auth application-default login`, or the attached service
account in a real deployment), no key file is read or needed. Leave the profile unset (the
default) and every seam behaves exactly as it always has — heuristic fallback for Vertex AI,
`LocalOutputStorage` for output artifacts.

**Smoke test.** `CloudConnectivityChecker` (`gcp/`) proves the live settings actually work: one
minimal Vertex AI call via the existing, unmodified `VertexAiClient` (bypassing `AiGateway`'s
retry/cache/routing on purpose — a connectivity check wants one narrow attempt at the lowest real
layer, not a re-exercise of already-tested orchestration), and one minimal write via the existing,
unmodified `OutputStorage` seam. Both return a result rather than throwing, and both skip without
touching the network when their target isn't enabled — safe to call from anywhere, including a
test with no credentials. Guarded like every other runner in this project:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=cloud \
    -Dspring-boot.run.arguments=--forgebrain.gcp.smoke-test-on-startup=true
```

**Secrets.** `SecretManagerConfig` (`forgebrain.secret-manager.*`) is a structure-only placeholder
— mirrors `FirestoreConfig`'s existing "reserved ahead of need" pattern. Nothing reads a secret
through it yet; ADC remains the only real authentication path this project uses. It exists so a
future real secret consumer (e.g. YouTube/Instagram credentials sourced from Secret Manager
instead of plain environment variables) has somewhere to bind to without inventing a new config
shape.

**Tests.** `CloudConnectivityCheckerImplTest` (mocked `VertexAiClient`/`OutputStorage`, no real
call) and `GcpConfigurationValidationTest` (boots the real app under both cloud-on and cloud-off
scenarios, confirms the smoke-test runner is present only when explicitly enabled, and confirms
`OutputStorageFactory` routes to `CloudStorageOutputStorage` for `forgebrain-artifacts` when
`cloud-storage.enabled` is true) — no live credentials required for any of it.

## What's Deliberately Not Here

Per this phase's explicit rules: no Docker, no Kubernetes, no deployment/CI infrastructure, no publishing/auto-upload, and no authentication beyond Application Default Credentials for Vertex AI (see "Vertex AI Research Stage" above — the one real Google Cloud client integration so far; local FFmpeg execution, see "Storyboard to MP4" above, is the one real non-Google rendering integration). See the repository root [`TODO.md`](../TODO.md) for the tracked path from here.
