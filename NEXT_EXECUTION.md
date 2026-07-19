# NEXT_EXECUTION.md

Status: First executable vertical slice — implemented and verified.
Scope: What this session built on top of the Phase 1 architecture (`REPORT.md`) and the backend scaffold — real, running code, not more design.

---

## 1. What Was Implemented

**Every stage from topic selection through storyboard generation now has a real implementation**, not just an interface:

| Component | File | What it does |
| --- | --- | --- |
| Curriculum loading | `curriculum/CurriculumLoaderImpl.java` | Parses `curriculum/java-roadmap.json` from disk, validates every prerequisite/next-topic reference resolves, caches in memory. |
| Memory storage | `services/MemoryServiceImpl.java` | File-based JSON persistence (the simplest durable approach for this phase — see Section 5). Bootstraps a fresh empty state on first run; every update writes through to disk immediately. |
| Topic selection | `services/TopicSelectorImpl.java` | The real gate-then-score algorithm from `brain/topic-selector-spec.md`: three gates (readiness, cooldown, not-in-progress), six weighted factors, four decision modes, deterministic tie-breaking. |
| Research | `services/ResearchServiceImpl.java` | Heuristic, curriculum-sourced brief generation — distills the curriculum's own `learning_objective`/`common_mistakes`/`example_ideas` (already-curated data) rather than calling an LLM. See Section 4. |
| Lesson | `services/LessonServiceImpl.java` | Narrows a research brief into one single-concept lesson, enforcing the "One of Everything" rule by construction. |
| Content Director | `services/ContentDirectorServiceImpl.java` | Deterministic rule-based strategy decisions (hook type, teaching style, emotional goal, pacing, CTA) — same lesson always produces the same strategy. |
| Script | `services/ScriptServiceImpl.java` | Template-based narration generation, following the binding hook/teaching-style mapping from `brain/script-spec.md`. |
| Storyboard | `services/StoryboardServiceImpl.java` | Groups the script's own subtitle segments into scenes — reuses already-validated timing rather than recomputing it. |
| Orchestration | `pipeline/PipelineOrchestratorImpl.java` | `advance()` is the real state machine (inspects which fields are unset, runs the next stage); `runFullPipeline()` is `startRun` + looped `advance` until a storyboard exists. |
| Result persistence | `pipeline/PipelineResultStoreImpl.java` | Saves each completed run as one pretty-printed JSON file. |
| Local execution | `pipeline/PipelineCommandLineRunner.java` | Gated by `forgebrain.pipeline.run-on-startup` (default `false`); runs one full pipeline slice on startup when enabled. |

Also: a Maven Wrapper (`./mvnw`) was added so this builds without Maven pre-installed, a shared snake_case `ObjectMapper` bean (`config/JacksonConfig.java`) so every JSON file in the project uses one consistent convention, and `Topic`/`MemoryState` gained fields (`commonMistakes`, `exampleIdeas`, `recentlyUsedHooks`, `recentlyUsedExamples`) that existed in the schemas but not yet in the Java models.

**105 main source files, 10 test files, 39 tests, all passing.**

---

## 2. What Now Works End to End

Run it yourself:

```bash
cd backend
./mvnw test -Dtest=PipelineOrchestratorImplTest   # the end-to-end proof, isolated and repeatable
./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.pipeline.run-on-startup=true  # the "one command" path
```

Both were run for real while building this (not just asserted to work). The second one, run live against a clean local state, actually selected **"What Is Java"** (the only topic in the 81-topic curriculum with no prerequisites), and produced:

```
hook_type=COMMON_BUG, teaching_style=CODE_FIRST
script: 102 words, ~40.8s
storyboard: 8 scenes, 40.8s total
hook: "Watch what happens when you get what Is Java wrong."
```

The end-to-end test (`PipelineOrchestratorImplTest`) verifies, against the real curriculum and a real Spring context:

- Topic selection reads real memory + curriculum and picks a schema-valid decision.
- Research, Lesson, Content Strategy, Script, and Storyboard all carry the same `topicId` through every stage.
- **Full traceability chain**: each stage's `basedOn*Version` field matches the exact version string of the stage before it — not just present, but checked for equality.
- **Strategy fidelity**: the script's `hookType`/`teachingStyle` are checked to exactly equal what the Content Director decided, per the binding contract in `brain/script-spec.md`.
- The result is actually written to disk, not just returned in memory.
- Memory is actually updated (the topic is marked `IN_PROGRESS`), and this persists to a real file.
- **A second run correctly refuses** rather than reselecting the same topic or fabricating progress (see Section 3) — this is asserted explicitly, not just hoped for.

Every individual stage also has its own unit tests verifying real behavior against real data — not mocks. Notably: `TopicSelectorImplTest` proves the fresh-start selection, prerequisite-chain advancement, cooldown blocking, and revision mode all work against the actual 81-topic curriculum; `ScriptServiceImplTest` proves `fullSpokenScript` is byte-for-byte the concatenation of every structured field and that `estimatedDurationSeconds` follows the exact 2.5-words/second formula from the spec; `StoryboardServiceImplTest` proves scenes are perfectly contiguous and every scene's voiceover text reconstructs the source script exactly.

A real bug was found and fixed during this work: the first version of the end-to-end test had two `@Test` methods sharing one Spring-managed singleton `MemoryServiceImpl` with undefined JUnit execution order, causing one test's `IN_PROGRESS` state to leak into the other. Fixed by merging the sequentially-dependent assertions into one test and adding `@DirtiesContext` for defense in depth. This is exactly the kind of thing a real end-to-end test is supposed to catch.

---

## 3. What Remains Blocked

- **Voice, Subtitles, Assets, Renderer, Reviewer, Publishing, Analytics** — explicitly out of scope for this pass per the task's constraints. Their interfaces exist (`services/`); none have implementations.
- **No topic can ever reach `POSTED` status in this slice.** That transition belongs to Renderer → Reviewer → Publishing, none of which exist yet. Practical consequence, proven by a real test: **a second `runFullPipeline()` call in the same memory state throws `InvalidTopicException`**, because the one topic with no prerequisites is now `IN_PROGRESS` (not `POSTED`), and nothing else's prerequisites can become satisfied. The pipeline can currently produce exactly one real result per fresh memory state — see Section 4, step 1, for the direct next step this blocks.
- **Research and Lesson now call Vertex AI; Content Director and Script are still heuristic.** `VertexAiClientImpl` (`vertex/`) is a real, compiled implementation calling Google Vertex AI via ADC, shared by `VertexAiResearchServiceImpl` (the `ResearchService` bean — see prior entry) and `VertexAiLessonServiceImpl` (the `LessonService` bean). The lesson service generates `lessonObjective`/`lessonSummary`/`keyPoints`/`stepByStepExplanation`/`coreExample`/`analogy`/`commonMistakes`/`whatToAvoidSaying`/`beginnerTakeaway`/`retentionHook`/`visualNotes`/`confidenceNotes` from a research-brief-grounded prompt, falling back to the original `LessonServiceImpl` heuristic narrowing whenever Vertex AI is unavailable (no credentials in this sandbox, so the fallback is what actually runs here — see `backend/README.md`). Content Director and Script still use deterministic templates/rules over curriculum/lesson data — genuinely structured and internally consistent, but not the quality a real LLM-backed implementation would produce.
- **`CurriculumLoaderImpl` reads a relative filesystem path** (`../curriculum/java-roadmap.json`), which only resolves correctly when run from `backend/`. Fine for this local-dev phase; would break in a packaged jar or a container with a different working directory.
- **One known content-quality rough edge**: heuristic title-casing in hook templates looks slightly awkward for multi-word curriculum titles that themselves start with a capitalized common word (e.g. "What Is Java" → "...you get what Is Java wrong" instead of clean casing). Cosmetic, not a correctness issue — visible in the live run output in Section 2.

---

## 4. Next 3 Implementation Steps

1. **Decide how a topic reaches `POSTED`.** Right now nothing ever does, which caps this slice at one real result per memory state (Section 3). The honest options are: (a) build far enough into Renderer/Reviewer/Publishing that a real review can approve a topic, or (b) add a deliberately simple, explicitly-provisional "mark posted" operation now so the pipeline can be exercised repeatedly while Renderer is being built. Either is a real design decision, not a bug fix — make it consciously.
2. **Extend the real `VertexAiClient` to Script.** Research and Lesson are done (`VertexAiResearchServiceImpl`, `VertexAiLessonServiceImpl`, see above) — the same pattern (prompt builder + JSON parsing + fallback composition around the existing heuristic `*ServiceImpl`) can be repeated for `ScriptServiceImpl` behind its unchanged interface.
3. **Implement Voice Generation** next (not Renderer directly) — it's the smallest of the remaining production-layer stages, and per `renderer/voice-spec.md` Section 4, it's the point where every duration estimate in this pipeline finally gets checked against something real. That reconciliation is worth proving before taking on Renderer itself, which `TODO.md` already flags as the largest remaining individual effort in the whole backlog.
