# TODO

Status: Tracking document for everything left unfinished after the Phase 1 **architecture** pass (see [`REPORT.md`](REPORT.md) for what that pass produced, and [`docs/PIPELINE.md`](docs/PIPELINE.md) for the pipeline every item below implements a piece of).

Every item follows the same structure: **Current status**, **Future implementation**, **Dependencies**, **Priority** (High / Medium / Low, relative to reaching one working end-to-end reel — see `docs/ARCHITECTURE.md` Section 2's definition of Phase 1 success).

---

## 1. Pipeline Stage Implementations

Every stage below has a complete spec, JSON Schema, worked examples, and a Java interface in `backend/services/`. None has an implementing class. This is intentional for this pass (see `REPORT.md`) — implementation is exactly what's left.

### 1.1 Topic Selection
- **Current status:** `TopicSelector` interface exists (`backend/services/TopicSelector.java`). Algorithm fully specified in `brain/topic-selector-spec.md` and `brain/topic-ranking.json`. No implementing class.
- **Future implementation:** Implement the gate-then-score algorithm exactly as documented — three gates (readiness, cooldown, not-in-progress), then weighted scoring per mode.
- **Dependencies:** `MemoryService` (1.2) and `CurriculumLoader` (2.3) must be implemented first — this stage reads both.
- **Priority:** High. Nothing downstream can run without a real topic selection.

### 1.2 Memory
- **Current status:** `MemoryService` interface and `MemoryQueries` (the six standard lookups from `memory/README.md`) exist. `MemoryStateEntity`/`TopicEntity` placeholder entities exist. No Firestore wiring, no implementing class.
- **Future implementation:** Implement `MemoryService` against Firestore once `FirestoreConfig` (3.2) is real. Decide whether topic tracking is one document per topic (`TopicEntity`) or folded into one `MemoryStateEntity` blob — both are representable now; pick one and delete the other's redundancy.
- **Dependencies:** Firestore integration (3.2).
- **Priority:** High. Topic Selection and every revision-aware stage depends on this.

### 1.3 Research
- **Current status:** `ResearchService` interface exists. Spec (`brain/research-spec.md`) and schema complete, with 3 worked examples.
- **Future implementation:** Implement against `VertexAiClient` (3.1) using a narrowly-scoped research prompt (see `docs/ARCHITECTURE.md` Section 8). Populate `sources[]` once a real fetching layer exists (currently always empty — see `research-spec.md` Section 6).
- **Dependencies:** Vertex AI integration (3.1), a research prompt template (4.1).
- **Priority:** High.

### 1.4 Lesson
- **Current status:** `LessonService` interface exists. Spec complete, 6 worked examples across all requested topic categories.
- **Future implementation:** Implement against `VertexAiClient`, enforcing the "One of Everything" narrowing rule (`lesson-spec.md` Section 4) — this is a judgment call an implementation has to actually make, not just format.
- **Dependencies:** Vertex AI integration (3.1), a teaching prompt template (4.1), Research (1.3) must run first.
- **Priority:** High.

### 1.5 Content Director
- **Current status:** `ContentDirectorService` interface exists. Spec complete, 6 worked examples with full strategy diversity verified.
- **Future implementation:** Implement the seven decision areas (hook, teaching posture, emotional goal, pacing, visual, code, CTA) per the heuristic tables in `content-director-spec.md` Section 5. `strategy_performance`-informed weighting is explicitly future work — see 1.15 (Analytics).
- **Dependencies:** Lesson (1.4) must run first.
- **Priority:** Medium-High.

### 1.6 Script
- **Current status:** `ScriptService` interface exists. Spec complete, 8 worked examples, all numerically verified (word counts, duration formula, `full_spoken_script` reconstruction).
- **Future implementation:** Implement against `VertexAiClient`, enforcing strict fidelity to the given `ContentStrategy` (`script-spec.md` Section 3's binding mapping table) and the 2.5 words/second duration formula.
- **Dependencies:** Vertex AI integration (3.1), a script-writing prompt template (4.1), Content Director (1.5).
- **Priority:** Medium-High.

### 1.7 Storyboard
- **Current status:** `StoryboardService` interface exists. Spec complete, 8 worked examples, fully verified for scene contiguity and voiceover-text traceability back to the source script.
- **Future implementation:** Implement the scene-grouping logic — deciding scene boundaries, `scene_type`, and whether to resequence for storytelling effect (per `storyboard-spec.md` Section 5.5) is a real design decision an implementation must make, not just formatting.
- **Dependencies:** Script (1.6).
- **Priority:** Medium.

### 1.8 Voice Generation
- **Current status:** `VoiceService` interface exists. Spec complete, 1 worked example (as part of the renderer/ combined example).
- **Future implementation:** Implement against Google Cloud Text-to-Speech. Must produce real `actual_duration_seconds` and, where available, `word_timings` per scene — this is the stage where estimate finally meets reality (`voice-spec.md` Section 4).
- **Dependencies:** A Google Cloud Text-to-Speech client dependency (not yet in `pom.xml`), Storyboard (1.7).
- **Priority:** Medium.

### 1.9 Subtitle Generation
- **Current status:** `SubtitleService` interface exists. Spec complete.
- **Future implementation:** Implement both reconciliation methods (`subtitle-spec.md` Section 4) — word-alignment when `word_timings` exists, proportional-estimate fallback otherwise. Both paths need real implementations, not just the fallback.
- **Dependencies:** Voice Generation (1.8).
- **Priority:** Medium.

### 1.10 Asset Management
- **Current status:** `AssetService` interface exists. Spec complete.
- **Future implementation:** Implement the catalog lookup once `assets/` (5.2) actually contains a catalog to resolve against. Until then this stage has nothing real to resolve to.
- **Dependencies:** A populated `assets/` catalog (5.2), Storyboard (1.7).
- **Priority:** Medium, blocked on 5.2.

### 1.11 Renderer
- **Current status:** `RendererService` and `RenderEngine` interfaces exist. Spec complete. **No rendering/encoding code exists or is expected from this pass** — this project's rules explicitly excluded writing rendering code.
- **Future implementation:** This is the largest single implementation gap in the pipeline: an actual video composition/encoding technology has to be selected and integrated behind `RenderEngine`. Out of scope for a documentation-only follow-up; likely its own dedicated implementation phase.
- **Dependencies:** Voice (1.8), Subtitles (1.9), Assets (1.10).
- **Priority:** Medium — high value, but realistically the biggest individual effort in the whole backlog.

### 1.12 Reviewer & Quality Scoring
- **Current status:** `ReviewerService` interface exists (covers both). Specs complete, with a worked `approved` and `rejected` example each.
- **Future implementation:** Five of six `QualityScore` dimensions are mechanically checkable; `hook_strength` is explicitly flagged (`quality-scoring-spec.md` Section 3) as needing an AI-assisted judgment call, not a formula — that's a separate, harder implementation problem from the other five.
- **Dependencies:** Renderer (1.11) must produce a real `VideoPackage` first.
- **Priority:** Medium.

### 1.13 Publishing Package
- **Current status:** `PublishingService` and `PlatformFormatter` interfaces exist. Spec complete.
- **Future implementation:** Implement title/description assembly per `publishing-spec.md` Section 6, and the `approved`-verdict precondition (Section 4) as an enforced check, not just documentation.
- **Dependencies:** Reviewer (1.12).
- **Priority:** Medium-Low.

### 1.14 Publishing/Upload Integration *(not designed — deliberately out of scope)*
- **Current status:** Does not exist, and no schema was written for it. `PublishingPackage.scheduling.status` can only reach `ready`, never an actually-posted state.
- **Future implementation:** A real integration with each target platform's upload API. This is explicitly excluded from Phase 1 per `docs/ARCHITECTURE.md` Section 10 and should stay excluded until a `PublishingPackage` has been manually verified end to end at least once.
- **Dependencies:** Publishing Package (1.13).
- **Priority:** Low — intentionally deferred, not an oversight.

### 1.15 Analytics
- **Current status:** `AnalyticsService`, `PerformanceSnapshot`, `StrategyPerformanceAggregate` exist. Spec complete. **Not active — has no real data source** (see below).
- **Future implementation:** Blocked on 1.14 existing first (no posted reels means no real metrics to capture). Once unblocked, implement the write-back into `MemoryState.audience_response` and the cross-topic `StrategyPerformanceAggregate` computation.
- **Dependencies:** Publishing/Upload Integration (1.14), which is itself not yet designed.
- **Priority:** Low — correctly sequenced last; every earlier stage's "future extensibility" section is already waiting on this, but it cannot start until reels are actually being published somewhere.

---

## 2. Curriculum & Content Data

### 2.1 Curriculum Data
- **Current status:** Complete — 81 topics across 13 levels in `curriculum/java-roadmap.json`, validated for consistent prerequisite references.
- **Future implementation:** None required for Phase 1. Later: consider extending beyond the first-pass 81 topics (noted as an intentional gap in the original curriculum task).
- **Dependencies:** None.
- **Priority:** N/A (done).

### 2.2 Memory Data
- **Current status:** Schema complete; only a hand-authored example state exists (`memory/examples/sample-memory-state.json`), covering 6 of 81 topics. No real, live memory state exists yet.
- **Future implementation:** Real memory state accumulates naturally once Topic Selection (1.1) and Memory (1.2) are implemented and running — no separate population task needed.
- **Dependencies:** 1.1, 1.2.
- **Priority:** N/A (accumulates automatically).

### 2.3 CurriculumLoader Implementation
- **Current status:** Interface exists (`backend/curriculum/CurriculumLoader.java`). No implementation — nothing currently parses `curriculum/java-roadmap.json` into `Topic`/`RoadmapLevel` objects.
- **Future implementation:** A straightforward JSON-parsing implementation; low complexity relative to its High priority, since almost everything else depends on it transitively.
- **Dependencies:** None.
- **Priority:** High.

---

## 3. Cross-Cutting Infrastructure

### 3.1 Vertex AI Integration
- **Current status:** `VertexAiClient`, `VertexAiPromptRequest`/`Response` interfaces exist (`backend/vertex/`). `VertexAiConfig` placeholder exists. No Google Cloud SDK dependency in `pom.xml`, no real client.
- **Future implementation:** Add the Vertex AI Java SDK dependency, implement `VertexAiClient` against it, wire real project ID/region/model IDs via environment variables or Secret Manager per `docs/CONFIGURATION.md` Section 5.
- **Dependencies:** A real GCP project with Vertex AI enabled and the GenAI credits referenced in this project's brief.
- **Priority:** High — blocks Research, Lesson, Content Director, and Script.

### 3.2 Firestore Integration
- **Current status:** `FirestoreConfig` placeholder exists. `repositories/` interfaces are plain contracts, not Spring Data repositories. No dependency declared.
- **Future implementation:** Add Spring Data Firestore (or the native Firestore Java SDK), convert `repositories/` interfaces to extend real Spring Data contracts, add real mapping annotations to `entities/`.
- **Dependencies:** A real GCP project with Firestore provisioned.
- **Priority:** High — blocks Memory and Topic Selection.

### 3.3 Cloud Storage Integration
- **Current status:** `CloudStorageConfig` placeholder exists. No client.
- **Future implementation:** Add the Cloud Storage client, implement upload/read for the `media`, `assets`, and `temp` buckets every production-layer schema already references by URI.
- **Dependencies:** A real GCP project with the three buckets provisioned.
- **Priority:** Medium — needed once Voice Generation (1.8) starts producing real files.

### 3.4 Cloud Scheduler Integration
- **Current status:** `CloudSchedulerConfig` placeholder exists. No actual scheduled trigger.
- **Future implementation:** Wire a Cloud Scheduler job to call a (not-yet-built) pipeline-trigger endpoint on a cron schedule.
- **Dependencies:** A working end-to-end pipeline (everything in Section 1) to actually trigger.
- **Priority:** Low — automation is only useful once the manual path works.

### 3.5 API Surface / Controllers
- **Current status:** Only `HealthController` is real. No pipeline-stage endpoints exist.
- **Future implementation:** Add controllers per pipeline stage (or one orchestration-level controller) as services in Section 1 get real implementations. `TopicSelectionRequest`/`Response` and `PipelineRunRequest`/`Response` DTOs already establish the intended pattern.
- **Dependencies:** At least one real service implementation to expose.
- **Priority:** Low-Medium.

### 3.6 Authentication
- **Current status:** Does not exist anywhere in this project, by design for this pass.
- **Future implementation:** Add once the API surface (3.5) has endpoints worth protecting — likely a service-to-service auth scheme (e.g. Cloud Run's built-in IAM invocation control) rather than end-user auth, given this is an internal content pipeline, not a public product.
- **Dependencies:** 3.5.
- **Priority:** Low for now; revisit before any endpoint is exposed outside a trusted network.

### 3.7 Deployment Infrastructure
- **Current status:** Does not exist. No Dockerfile, no Kubernetes manifests, no CI/CD — explicitly excluded from this pass's rules.
- **Future implementation:** A Dockerfile (Cloud Run's expected deployment unit) and a CI pipeline (build, test, deploy) once the backend has real logic worth deploying continuously.
- **Dependencies:** At least one working pipeline stage.
- **Priority:** Low for now — deploying an empty scaffold has no value; revisit once Section 1's High-priority items are implemented.

### 3.8 Build Verification
- **Current status:** Maven is not available in the environment this scaffold was built in. The 75 dependency-free Java files (`models`, `shared`, `exceptions`, `services`, `entities`, `repositories`, and all domain packages) were verified with direct `javac` compilation — zero errors. The Spring-dependent files (`config`, `controllers`, `dto`, `BackendApplication`, the test class) were written to the same conventions but not independently compiled.
- **Future implementation:** Run `mvn compile` and `mvn test` as the very first action on this codebase, before adding any new code.
- **Dependencies:** A Maven installation.
- **Priority:** High, but trivial — this should be step zero of any further work.

---

## 4. Prompts

### 4.1 AI Prompt Templates
- **Current status:** `prompts/` is completely empty. `docs/ARCHITECTURE.md` Section 8 names five needed prompts (planner, research, teaching, review, storyboard) but none are drafted.
- **Future implementation:** Draft one narrowly-scoped prompt template per generative stage (Research, Lesson, Content Director, Script — Storyboard and Review may be more mechanical/less LLM-dependent per their specs' emphasis on deterministic logic). Each should be validated against its stage's schema's `required` fields to confirm the model can reliably produce schema-valid output.
- **Dependencies:** Vertex AI integration (3.1), so prompts can actually be tested against real model output rather than designed blind.
- **Priority:** High — blocks every generative stage in Section 1.

---

## 5. Assets

### 5.1 Asset Management Logic
- See 1.10.

### 5.2 Asset Catalog Population
- **Current status:** `assets/` is completely empty. `renderer/asset-management-spec.md` Section 4 describes the resolution logic in detail but explicitly states no real catalog exists yet.
- **Future implementation:** Populate real font files, a background music library (with license tracking — `AssetManifest.backgroundMusic.license` is already reserved for this), code syntax themes, and a brand watermark, organized so `AssetService` (1.10) can resolve `render_style` names against them.
- **Dependencies:** A decided brand identity (see 6.1, `brand_voice`) — visual and audio brand identity are likely decided together.
- **Priority:** Medium — blocks Asset Management (1.10) from doing anything real.

---

## 6. Known Design Gaps

These are gaps acknowledged *inside* the specs themselves, pulled forward here so they're tracked in one place rather than only discoverable by reading every spec.

### 6.1 `brand_voice` Has No Persisted Schema
- **Current status:** Referenced as an input by `script-spec.md`, `storyboard-spec.md`, and `voice-spec.md`, but no `brand_voice` schema or profile file exists anywhere in the repository. `VoiceResult.voiceProfile` gives the *audio* side a concrete shape (a real `voice_id`); the *narrative* side (tone descriptors, catchphrases, banned phrases) remains undefined.
- **Future implementation:** Design a `brand_voice` schema (likely `brain/brand-voice-schema.json` or similar) once an actual brand identity/persona is decided — this is a product decision as much as a technical one.
- **Dependencies:** A human decision about ForgeBrain's on-screen brand voice/persona.
- **Priority:** Medium — every content-generating stage already has a slot waiting for this.

### 6.2 Example Depth for Newer Components
- **Current status:** `brain/`'s six stages have deeply verified examples (programmatically generated and checked). `renderer/examples.md`, `reviewer/examples.md`, and `publishing/examples.md` are hand-authored and internally consistent by construction, not machine-verified against source data the way `brain/`'s are — stated explicitly in each file.
- **Future implementation:** Once real implementations exist for these stages (Section 1), regenerate their examples programmatically from real service output, following the same pattern as `brain/script-examples.md` and `brain/storyboard-examples.md`.
- **Dependencies:** Real implementations of the stages in question.
- **Priority:** Low — documentation polish, not a functional gap.

### 6.3 Thumbnail Selection Quality
- **Current status:** `render-spec.md` Section 8 flags that selecting a thumbnail frame from a storyboard's first `emphasis_point` timestamp risks landing mid-transition or motion-blurred.
- **Future implementation:** Sample a small window around the emphasis point and pick the sharpest frame, once rendering (1.11) exists to test this against.
- **Dependencies:** Renderer (1.11).
- **Priority:** Low.

---

## Suggested Order of Attack

1. **Section 3.8** (run `mvn compile`/`mvn test` — verify the scaffold as-is).
2. **Section 2.3, 3.1, 3.2** (`CurriculumLoader`, Vertex AI, Firestore — the three hard dependencies almost everything else needs).
3. **Section 4.1** (prompt templates, developed against a working Vertex AI connection).
4. **Section 1.1–1.2** (Topic Selection, Memory — the first real, testable slice of the pipeline).
5. **Section 1.3–1.7** (Research through Storyboard — the full decision layer, independently testable against `brain/`'s existing worked examples before any media is produced).
6. **Section 5.2, 6.1** (asset catalog and brand voice — product decisions that unblock the production layer).
7. **Section 1.8–1.11** (Voice through Renderer — the production layer; 1.11 is the largest individual effort in this entire list).
8. **Section 1.12–1.13** (Reviewer, Publishing Package — closing out one full reel).
9. Everything else (Sections 1.14, 1.15, 3.3–3.7, 6.2–6.3) only after step 8 produces one real, correct, manually-verified reel — consistent with `docs/ARCHITECTURE.md`'s founding Phase 1 success criterion.
