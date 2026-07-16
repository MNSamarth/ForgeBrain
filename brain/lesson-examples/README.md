# Lesson Examples

Six worked lesson blueprints, one per topic category described in [../lesson-spec.md](../lesson-spec.md) Section 6, showing how the same schema in [../lesson-output-schema.json](../lesson-output-schema.json) flexes across different kinds of Java topics.

| File | Topic | Focus lens | Teaching style |
| --- | --- | --- | --- |
| `java-variables-and-data-types.json` | Variables | Storage and type clarity | `direct-explanation` |
| `java-for-loop.json` | Loops | Repetition and control flow | `problem-first` |
| `java-method-basics.json` | Methods | Reuse and structure | `comparison-based` |
| `java-arrays-basics-revision.json` | Arrays | Fixed-size, ordered data | `problem-first` |
| `java-string-basics.json` | Strings | Immutability and text handling | `myth-busting` |
| `java-classes-and-objects.json` | Classes and Objects | Modeling real-world entities | `story-driven` |

## Continuity with earlier stages

- **`java-variables-and-data-types.json`** is built from [`../research-examples/java-variables-and-data-types.json`](../research-examples/java-variables-and-data-types.json) — the standard fresh-topic research brief.
- **`java-arrays-basics-revision.json`** is built from [`../research-examples/java-arrays-basics-revision.json`](../research-examples/java-arrays-basics-revision.json), and its `confidence_notes` explicitly carries forward the same prior-underperformance context that shaped the research brief's reordering, per `lesson-spec.md` Section 9.
- **`java-for-loop.json`**, **`java-method-basics.json`**, **`java-string-basics.json`**, and **`java-classes-and-objects.json`** do not have a corresponding persisted file under `../research-examples/` — only three research briefs are committed there. These four lessons are illustrative: they assume research equivalent to `research-spec.md`'s process was already performed (drawing on the same `curriculum/java-roadmap.json` entries these topics already have), even though no intermediate research-brief JSON was written to disk for them. `based_on_research_version` still reflects the research schema version (`1.0.0`) they'd be consistent with.

## Demonstrating the "one of everything" rule

Every file here has exactly one `lesson_objective`, one `analogy`, one `core_example`, one `beginner_takeaway`, and treats the first entry of `common_mistakes` and `visual_notes` as its single memorable contrast and single visual cue, respectively — per `lesson-spec.md` Section 4. `key_points` and `step_by_step_explanation` are still short ordered lists, but every entry in each one serves that single objective rather than introducing a competing concept.
