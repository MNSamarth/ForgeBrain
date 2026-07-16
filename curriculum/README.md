# Curriculum

This folder is ForgeBrain's knowledge map. It defines *what* Java topics exist, in what order they should be taught, and how they relate to each other. It exists so the system can decide what to teach next without a human choosing a topic by hand.

## Why ForgeBrain needs this

An AI content pipeline that just picks "an interesting Java topic" every run will drift — it will repeat itself, teach advanced material before the prerequisite has been covered, or produce a list of disconnected reels that don't add up to a coherent learning path.

The curriculum solves this by making topic selection a lookup against structured data instead of an open-ended decision. Every topic has explicit prerequisites, a difficulty tier, and a place in a level (e.g. Foundations, Control Flow, OOP). That structure is what lets automated topic selection be correct instead of merely plausible.

## How this drives topic selection

The Curriculum Engine (see [ARCHITECTURE.md](../docs/ARCHITECTURE.md)) reads `java-roadmap.json` together with Topic Memory's record of what has already been covered, and selects the next topic using simple rules:

1. Skip any topic whose `status` is not `not_covered`.
2. Only consider a topic once every id listed in its `prerequisites` has already been covered.
3. Among eligible topics, prefer the earliest position in the roadmap (levels are ordered beginner → advanced, and topics within a level are ordered teachably).
4. A topic's `next_topics` are the topics it unlocks — useful for previewing the path forward without re-scanning the whole roadmap.

This keeps selection deterministic and explainable: at any point, ForgeBrain can state exactly why a given topic was chosen next.

## File overview

- `java-roadmap.json` — the full first-pass Java learning roadmap: levels, topics, prerequisites, and per-topic teaching metadata.

A `topics/` subfolder is intentionally not used yet. The roadmap is small enough to live in a single file for now; splitting one topic per file is a reasonable future step if the roadmap grows large enough that a single JSON file becomes hard to review or edit.

## Extensibility

Each topic entry already reserves fields for data that doesn't exist yet — `difficulty_score`, `performance_score`, `audience_feedback`, `revision_count`, `aliases`, and `related_topics`. They default to empty/null values in this first pass. This is so that later work (analytics, feedback loops, content revision tracking) can populate these fields without changing the shape of the data every downstream system already reads.
