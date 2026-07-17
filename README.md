# ForgeBrain

ForgeBrain is an AI content operating system for generating premium, faceless short-form educational videos.

## Phase 1 Focus

The initial focus is Java short-form educational content — bite-sized reels that teach Java concepts clearly and consistently.

## Long-Term Goal

ForgeBrain is designed to grow into a full content pipeline that can:

- **Plan** a structured learning curriculum
- **Generate** individual lesson scripts and content from that curriculum
- **Render** the generated content into finished, faceless video reels

## Project Status

The full Phase 1 **architecture** is designed: a fourteen-stage content pipeline (curriculum through publishing package), a JSON Schema contract for every stage, and a Spring Boot backend project structure (interfaces, domain models, configuration placeholders) mirroring those contracts. See [`docs/PIPELINE.md`](docs/PIPELINE.md) for the authoritative, current pipeline description and [`REPORT.md`](REPORT.md) for a full summary of what exists and what's next.

**No pipeline business logic, AI provider calls, or rendering code are implemented yet** — every service in `backend/` is a contract (an interface) with no implementation. See [`TODO.md`](TODO.md) for the tracked path to a working Phase 1 reel.

## Repository Structure

| Folder | Purpose |
| --- | --- |
| `brain/` | The decision/planning layer: topic selection, research, lesson, content strategy, script, storyboard. See `brain/README.md`. |
| `renderer/` | The production layer: voice generation, subtitles, asset resolution, and rendering. See `renderer/README.md`. |
| `reviewer/` | The pipeline's final quality gate: quality scoring and review verdicts. See `reviewer/README.md`. |
| `publishing/` | Bundles an approved reel for a future publishing step. Does not publish anything. See `publishing/README.md`. |
| `analytics/` | The performance feedback loop's design. Not active in Phase 1. See `analytics/README.md`. |
| `backend/` | Spring Boot project structure: interfaces, domain models, configuration placeholders. No business logic yet. See `backend/README.md`. |
| `curriculum/` | The Java learning roadmap that drives topic selection. See `curriculum/README.md`. |
| `memory/` | Persistent state: what's been taught, queued, or flagged for revision. See `memory/README.md`. |
| `prompts/` | AI prompt templates (not yet populated — see `TODO.md`). |
| `assets/` | Static assets: fonts, brand elements, music, code themes (not yet populated — see `TODO.md`). |
| `docs/` | Architecture, pipeline, and configuration documentation. |
| `scripts/` | Developer and automation scripts (not yet populated). |
| `.github/` | GitHub configuration (workflows, templates). |
