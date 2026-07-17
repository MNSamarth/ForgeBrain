# ForgeBrain Backend

A Spring Boot project structure for the pipeline described in [`../docs/PIPELINE.md`](../docs/PIPELINE.md). This phase delivers architecture only: packages, interfaces, domain models, DTOs, and configuration placeholders. **No pipeline business logic, no external API calls, no rendering/encoding code, and no authentication are implemented.** See [`../TODO.md`](../TODO.md) for what comes next.

## Stack

Java 17, Spring Boot 3.3, Maven. No Google Cloud SDK dependency is declared yet — see Section 5.

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

### 5. No Google Cloud SDK dependency yet

`pom.xml` declares only Spring Web, Validation, and the configuration-annotation processor — deliberately not `spring-cloud-gcp` or any Vertex AI/Firestore/Cloud Storage client library. Adding a real cloud dependency implies a real integration is imminent; this phase is architecture only. `config`, `vertex`, `repositories`, and `entities` are all shaped to make that addition straightforward later without restructuring — see `TODO.md`.

### 6. Verification status

This project could not be built with Maven in the environment this scaffold was produced in (Maven is not installed there). Instead: every file in `models`, `shared`, `exceptions`, `services`, `entities`, `repositories`, and the seven domain packages (75 files total) — the entire subset with zero external dependencies — was compiled directly with `javac` and produced zero errors. `pom.xml` was checked for well-formed XML and balanced tags; both `application.yml` files were checked for valid YAML structure (no tabs, consistent indentation). The Spring-dependent files (`config`, `controllers`, `dto`, `BackendApplication`, the test) follow the same conventions but were not independently compiled — **run `mvn compile` (or `mvn test`) as the first verification step** before building on this scaffold.

## What's Deliberately Not Here

Per this phase's explicit rules: no Docker, no Kubernetes, no deployment/CI infrastructure, no real Google Cloud client calls, no authentication, and no service implementation classes (every `services` interface has zero implementing classes — "fake implementations" were explicitly out of scope). See the repository root [`TODO.md`](../TODO.md) for the tracked path from here.
