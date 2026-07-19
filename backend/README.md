# ForgeBrain Backend

A Spring Boot project structure for the pipeline described in [`../docs/PIPELINE.md`](../docs/PIPELINE.md). This phase delivers architecture only: packages, interfaces, domain models, DTOs, and configuration placeholders. **No pipeline business logic, no external API calls, no rendering/encoding code, and no authentication are implemented.** See [`../TODO.md`](../TODO.md) for what comes next.

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
| `rendering` | `SceneRenderInstruction`, `RenderEngine` — the seam a real rendering technology would plug into. No rendering code. |
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

**What remains heuristic**: Script and Storyboard are still deterministic template/rule-based
stages — unchanged by this or the research stage's Vertex AI integration. Content Director is now
Vertex AI-backed too — see "Vertex AI Content Director Stage" below.

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

**What remains heuristic**: Script and Storyboard are still deterministic template/rule-based
stages — unchanged by this or the research/lesson stages' Vertex AI integration.

**Configuration**: `forgebrain.vertex-ai.content-director-temperature` (default `0.4`),
`content-director-max-output-tokens` (default `2048`), and
`content-director-response-mime-type` (default `application/json`) tune generation for this
stage specifically. No new local setup beyond the research stage's ADC steps above.

## What's Deliberately Not Here

Per this phase's explicit rules: no Docker, no Kubernetes, no deployment/CI infrastructure, no publishing/rendering/auto-upload, and no authentication beyond Application Default Credentials for Vertex AI (see "Vertex AI Research Stage" above — this is the one real Google Cloud client integration so far; see `NEXT_EXECUTION.md` for what else this scaffold now implements). See the repository root [`TODO.md`](../TODO.md) for the tracked path from here.
