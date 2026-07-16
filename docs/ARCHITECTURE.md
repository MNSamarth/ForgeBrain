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

At a high level, ForgeBrain is a pipeline of specialized components, each with one job:

- **Curriculum Engine** — Owns the structured map of what Java topics exist, their order, and their dependencies. Decides what "the next topic" means in context.
- **Topic Memory** — Tracks what has already been covered, when, and how it performed. Prevents repetition and informs the Curriculum Engine's next choice.
- **Research Agent** — Gathers and verifies the factual/technical grounding for a chosen topic before any teaching content is written.
- **Lesson Generator** — Turns a topic plus research into a structured lesson: the core explanation, examples, and teaching sequence.
- **Script Generator** — Converts a lesson into a short-form video script: narration lines, timing beats, and on-screen text cues.
- **Reviewer** — Checks the script and lesson for technical accuracy, clarity, and pacing before anything is rendered. Can reject or request revision.
- **Storyboard Generator** — Breaks the reviewed script into discrete visual scenes/shots: what appears on screen for each narration segment.
- **Renderer** — Turns the storyboard, narration audio, and visual assets into a finished vertical video file.
- **Publisher Preparation** — Packages the rendered reel with the metadata a future publishing step will need (title, description, thumbnail frame). Does not post anywhere in Phase 1.
- **Analytics and Feedback Loop** — Captures performance data on published reels and feeds it back into Topic Memory to influence future topic and content decisions. Not active in Phase 1.

## 5. Data Flow

The pipeline runs as a linear sequence per reel, with the Reviewer able to send work backward for revision:

1. **Topic selection** — Curriculum Engine consults Topic Memory and selects the next Java topic.
2. **Research** — Research Agent gathers accurate technical grounding for that topic.
3. **Lesson creation** — Lesson Generator produces a structured lesson from the topic + research.
4. **Script generation** — Script Generator converts the lesson into a narration/timing script.
5. **Review** — Reviewer evaluates the script and lesson; approves or sends back for revision.
6. **Storyboard creation** — Storyboard Generator maps the approved script into discrete visual scenes.
7. **Audio generation** — Narration audio is generated from the approved script.
8. **Rendering** — Renderer combines storyboard, audio, and visual templates into a video file.
9. **Export** — Finished reel and its metadata are packaged as output artifacts.
10. **Analytics capture** — (Post-Phase 1) Performance data is collected once a reel is published.
11. **Memory update** — Topic Memory is updated with what was covered and (once available) how it performed, closing the loop for the next topic-selection cycle.

## 6. Repository Structure

```text
forgebrain/
├── backend/       # Orchestration logic that runs the pipeline stages in sequence
├── curriculum/     # Curriculum structure: topic definitions, ordering, dependencies
├── prompts/        # Individual prompt templates, one per pipeline stage
├── memory/         # Topic memory and content memory state
├── renderer/        # Video rendering pipeline: templates, scene composition, export
├── assets/         # Static assets: fonts, brand elements, background music, code themes
├── docs/          # Design and planning documentation
├── scripts/        # Developer and automation scripts
└── .github/        # Repository configuration (workflows, templates)
```

Each folder maps to a stage or supporting concern in the architecture above — no folder exists without a corresponding component in Section 4.

## 7. Data Models

These are the core entities the system operates on. Shown as simplified JSON examples for clarity, not as final schemas.

**Topic**
```json
{
  "id": "java-generics-basics",
  "title": "Introduction to Generics",
  "domain": "java",
  "dependsOn": ["java-classes-basics"],
  "status": "not_covered"
}
```

**Lesson**
```json
{
  "id": "lesson-001",
  "topicId": "java-generics-basics",
  "summary": "What generics are and why they exist",
  "keyPoints": ["type safety", "generic classes", "generic methods"],
  "examples": ["List<T> usage example"]
}
```

**Script**
```json
{
  "id": "script-001",
  "lessonId": "lesson-001",
  "lines": [
    { "time": "0:00", "narration": "...", "onScreenText": "..." }
  ]
}
```

**Review**
```json
{
  "scriptId": "script-001",
  "verdict": "revise",
  "issues": ["pacing too fast in middle third"]
}
```

**Reel**
```json
{
  "id": "reel-001",
  "scriptId": "script-001",
  "videoFile": "reel-001.mp4",
  "status": "rendered"
}
```

**Asset**
```json
{
  "id": "asset-code-theme-dark",
  "type": "visual-template",
  "path": "assets/templates/code-dark.json"
}
```

**Performance Metrics** *(post-Phase 1)*
```json
{
  "reelId": "reel-001",
  "views": 0,
  "retentionCurve": [],
  "completionRate": 0
}
```

**Memory State**
```json
{
  "coveredTopics": ["java-classes-basics"],
  "lastUpdated": "2026-07-16"
}
```

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
- This architecture document.
- Curriculum structure for Java (topic definitions and ordering).
- Memory model (structure for tracking covered topics).
- Topic selection logic (Curriculum Engine + Topic Memory working together).
- One complete reel generation path: topic → research → lesson → script → review → storyboard → render → export, for a single topic end to end.

**Explicitly not included in Phase 1:**

- Auto-posting to social media platforms.
- Analytics dashboards.
- Multi-language support (any language other than Java).
- Advanced A/B testing of content variants.
- Scaling infrastructure for high-volume (multi-reel-per-day) production.

Phase 1 succeeds when one topic can move through the entire pipeline and produce a finished, correctly rendered reel without manual intervention at any stage.

## 11. Risks and Tradeoffs

- **Repetitive content** — Without strict topic memory, the system may re-teach the same concept in different words. Mitigated by the Curriculum Engine + Topic Memory pairing, but the memory model must be enforced consistently.
- **Hallucinated technical explanations** — AI-generated teaching content about Java can be confidently wrong. The Research Agent and Reviewer stages exist specifically to catch this, but neither is a guarantee.
- **Weak retention** — Short-form educational content can be technically correct but boring or hard to follow, leading to low watch-through. Rendering and script pacing decisions directly affect this and are hard to validate without real audience data.
- **Render quality issues** — Automated rendering can produce output that is technically complete but visually inconsistent (bad timing, cramped text, clashing themes). Modular templates reduce but do not eliminate this risk.
- **Over-automation before validation** — Building out topic selection, analytics, and scaling before a single reel's quality is proven wastes effort on infrastructure for a pipeline that may still need fundamental rework. Phase 1's scope boundary exists specifically to prevent this.

## 12. Next Implementation Steps

1. Define the Java curriculum structure in `curriculum/` (topic list, ordering, dependency rules).
2. Define the memory model in `memory/` (what "covered" means, what state is persisted between runs).
3. Implement topic selection logic (Curriculum Engine reading from Topic Memory).
4. Draft the first-stage prompts (planner, research, teaching) in `prompts/` and validate their structured output format.
5. Build the single end-to-end path for one topic through to a rendered reel, without review-loop revision handling yet.
6. Add the Reviewer stage and revision loop once the base path is proven.
7. Revisit scope for Phase 2 only after one reel has been produced correctly through the full pipeline.
