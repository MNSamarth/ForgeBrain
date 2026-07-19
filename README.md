# ForgeBrain

ForgeBrain is an AI-powered content pipeline for generating short-form educational videos, starting with Java learning content.

## What ForgeBrain Is Building

ForgeBrain turns a structured curriculum into publish-ready reels through a staged pipeline that combines deterministic logic, LLM generation, and production/review tooling.

## End-to-End Pipeline Flow

```mermaid
flowchart LR
    A[Curriculum] --> B[Memory]
    B --> C[Topic Selection]
    C --> D[Research]
    D --> E[Lesson]
    E --> F[Content Director]
    F --> G[Script]
    G --> H[Storyboard]
    H --> I[Renderer]
    I --> J[Reviewer]
    J --> K[Publishing]
```

Current executable slice: **Curriculum -> Storyboard** in the Spring Boot backend.

## Architecture (Current + Planned)

```mermaid
flowchart TB
    subgraph Inputs
        CURR[curriculum/java-roadmap.json]
        MEM[memory/*.json state]
    end

    subgraph Backend["Spring Boot Backend (backend/)"]
        ORCH[PipelineOrchestratorImpl]
        BRAIN[Topic Selector + Research + Lesson + Content Director + Script + Storyboard]
        RESULT[PipelineResultStore JSON output]
        VERTEX[VertexAiClientImpl]
    end

    subgraph GCP["Google Cloud (target platform)"]
        VAI[Vertex AI]
        GCS[Cloud Storage]
        FS[Firestore]
        CR[Cloud Run]
    end

    CURR --> ORCH
    MEM --> ORCH
    ORCH --> BRAIN --> RESULT
    BRAIN --> VERTEX --> VAI
    RESULT -. planned persistence .-> GCS
    MEM -. planned migration .-> FS
    ORCH -. planned deployment .-> CR
```

## Current Status

This reflects the repository as of the latest backend vertical slice (`NEXT_EXECUTION.md`, `backend/README.md`).

### Implemented

- End-to-end orchestration from **Topic Selection -> Storyboard** (`PipelineOrchestratorImpl`, `runFullPipeline()`).
- Working stage implementations for:
  - Curriculum loading
  - Memory storage (local JSON files)
  - Topic selection
  - Research
  - Lesson
  - Content Director
  - Script
  - Storyboard
- Vertex AI integration in backend for:
  - Research generation (`VertexAiResearchServiceImpl`)
  - Lesson generation (`VertexAiLessonServiceImpl`)
  - Shared client (`VertexAiClientImpl`) with ADC-based auth.
- Pipeline result persistence to local JSON artifacts.
- Pipeline execution report generation per run under `reports/` (stage-by-stage observability).
- Automated tests for orchestrator and stage behavior.

### Planned / Not Yet Implemented

- Production stages after storyboard in the backend pipeline:
  - Voice generation
  - Subtitles
  - Asset management
  - Renderer
  - Reviewer
  - Publishing
- Analytics feedback loop activation.
- Firestore-backed persistence (currently local file memory state).
- Cloud Storage-backed media/output storage.
- Cloud Run deployment path (config scaffolding exists; deployment infra not yet implemented).

## Technology Stack (Google Cloud Focused)

- **Spring Boot 3 / Java 17**: Core orchestration and service runtime.
- **Vertex AI (Gemini via Java SDK)**: Live LLM integration for research + lesson stages.
- **Cloud Storage**: Planned artifact/media storage target.
- **Firestore**: Planned persistent memory and pipeline state backend.
- **Cloud Run**: Planned deployment target for the backend service.

## Repository Structure

| Folder | Purpose |
| --- | --- |
| `backend/` | Spring Boot implementation and pipeline orchestration. |
| `brain/` | Specs/schemas/examples for topic selection, research, lesson, strategy, script, storyboard. |
| `renderer/` | Specs/schemas for voice, subtitles, assets, rendering. |
| `reviewer/` | Specs/schemas for quality scoring and review decisions. |
| `publishing/` | Specs/schemas for packaging approved output for publishing workflows. |
| `curriculum/` | Java roadmap dataset driving topic selection. |
| `memory/` | Memory model/spec describing learning state and decisions. |
| `analytics/` | Planned performance feedback loop specs. |
| `docs/` | Architecture and configuration documentation. |
| `TODO.md` | Backlog and implementation priorities. |
| `NEXT_EXECUTION.md` | Latest executable-slice implementation summary. |

## Project Roadmap

### Phase 1 - Brain Pipeline Execution
- Deliver reliable Curriculum -> Storyboard execution in backend.
- Expand deterministic + Vertex-backed generation quality.
- Keep schema-contract fidelity and strong test coverage.

### Phase 2 - Production Pipeline
- Implement Voice, Subtitles, Asset Management, and Renderer stages.
- Produce repeatable video package outputs from storyboard artifacts.

### Phase 3 - Quality and Publishing
- Implement Reviewer scoring/gates and Publishing package flow.
- Close the full path from curriculum topic to publish-ready output.

### Phase 4 - Cloud and Optimization
- Move persistence/artifacts to Firestore + Cloud Storage.
- Deploy runtime to Cloud Run.
- Activate analytics-driven iteration and performance feedback loops.

## Getting Started

1. Read [`docs/PIPELINE.md`](docs/PIPELINE.md) for stage contracts.
2. Read [`backend/README.md`](backend/README.md) for executable backend details.
3. See [`NEXT_EXECUTION.md`](NEXT_EXECUTION.md) for the current vertical slice and known gaps.
4. See [`TODO.md`](TODO.md) for prioritized next implementation steps.
