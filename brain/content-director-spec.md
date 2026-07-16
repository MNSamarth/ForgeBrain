# Content Director Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Content Director: the layer that decides **how** a lesson should be taught before any script is written. It sits between the [lesson stage](lesson-spec.md) and script generation, and corresponds to a refinement of the storytelling/engagement responsibility implied by the Script Generator in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) — made explicit here as its own stage because "what to teach" and "how to make someone want to watch it" are different decisions that deserve different attention.

## 1. Why the Content Director Exists

The lesson stage decides *what* gets taught: one concept, one example, one takeaway, in a defensible teaching order (see `lesson-spec.md` Section 4, the "One of Everything" rule). It does not decide how that content should be *presented* to hold attention for 30-45 seconds. Those are genuinely separate problems:

- The same lesson on Arrays could open with a slow explanation of what an array is, or open cold on the crash and rewind — same underlying concept, wildly different watch-through rate.
- Two topics with an identical `teaching_style` in their lesson blueprint (e.g. both "problem-first") can still call for different hooks, different pacing, and different emotional payoffs, because *what the audience needs to feel* to keep watching isn't determined by the concept alone.

Without a dedicated stage for this, engagement decisions get made implicitly and inconsistently — buried inside whichever prompt eventually writes the script, undocumented, and impossible to review or improve. The Content Director makes those decisions explicit, structured, and answerable to "why," the same way the topic selector's `reason` field and the research/lesson stages' `confidence_notes` made their own decisions auditable.

The Content Director generates a **content strategy** — a set of directorial decisions. It never writes a line of dialogue.

## 2. Position in the Pipeline

```
Topic Selector → Research → Lesson → Content Director → Script Generator → Reviewer → ...
(decision)        (brief)    (blueprint) (content strategy)  (script)
```

The Content Director consumes one lesson blueprint (`lesson-output-schema.json`) and produces exactly one content strategy. The Script Generator consumes that content strategy — plus the original lesson blueprint, for the actual content — and writes the words.

## 3. Relationship to the Lesson's Own `teaching_style` Field

The lesson blueprint already carries a provisional `teaching_style` (`direct-explanation`, `problem-first`, `story-driven`, `comparison-based`, or `myth-busting` — see `lesson-output-schema.json`), set during lesson planning as a rough signal for how the lesson stage sequenced its `step_by_step_explanation`.

The Content Director's own `teaching_style` decision (`explain-first`, `code-first`, `analogy-first`, `visual-first`, `question-first`) is a different, more specific axis: it's about **opening posture** — what the viewer sees or hears in the first few seconds — not the lesson's internal teaching sequence. The Content Director treats the lesson's `teaching_style` as an informative starting signal, not a constraint. A lesson tagged `problem-first` naturally tends toward a Content Director `teaching_style` of `code-first` or `question-first`, but the Content Director makes the authoritative, final call, informed by more than the lesson stage considered — see the mapping table in Section 4.2.

## 4. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `lesson` | Full lesson blueprint, matching `lesson-output-schema.json` | The committed concept, example, analogy, and mistake this reel is built around — the Content Director does not alter any of this, only decides how it's presented. |
| `memory_state` | The relevant slice of a memory state matching `memory/memory-schema.json`, if this topic has history | Tells the Content Director whether a prior version's strategy underperformed, so a revision can deliberately choose differently (see the Arrays example in `content-director-examples.md`). |
| `strategy_performance` *(reserved, not populated in Phase 1)* | A future aggregate of performance data grouped by strategy dimension (hook_type, teaching_style, etc.) rather than by topic | See Section 8 — this is what would let the Content Director learn "myth hooks underperform" independent of any single topic. |
| `target_duration_seconds` | Carried from `lesson.target_duration_seconds` unless overridden | Bounds `scene_pacing` and `estimated_watch_time`. |

## 5. Decision Areas

Each of the following is a distinct decision the Content Director makes, with a documented reason. None of them involve writing actual dialogue — they constrain and direct the Script Generator, they don't pre-empt it.

### 5.1 Hook Strategy (`hook_type`, `hook_reason`)

One of: `beginner-mistake`, `myth`, `question`, `challenge`, `interview-question`, `before-vs-after`, `hidden-feature`, `common-bug`, `productivity-tip`, `performance-comparison`.

Signal the Content Director should weigh: the lesson's `common_mistakes[0]` (its designated "one memorable mistake," per `lesson-spec.md` Section 4) is often the strongest hook material directly — a concrete, surprising, checkable mistake maps naturally to `beginner-mistake` or `common-bug`. A lesson whose `core_example` contrasts two behaviors (e.g. `int` vs `double` division, `String +` vs `StringBuilder`) maps naturally to `before-vs-after` or `performance-comparison`. A topic with strong real-world interview relevance (HashMap, exceptions, collections) can justify `interview-question` even when the lesson content itself doesn't change. `hook_reason` must name which signal drove the choice — never just restate the hook type.

### 5.2 Teaching Strategy (`teaching_style`, `teaching_style_reason`)

One of: `explain-first`, `code-first`, `analogy-first`, `visual-first`, `question-first`.

Starting-point mapping from the lesson's own `teaching_style` (a signal, not a rule — see Section 3):

| Lesson `teaching_style` | Typical Content Director `teaching_style` |
| --- | --- |
| `direct-explanation` | `explain-first` |
| `problem-first` | `code-first` or `question-first` |
| `analogy`-heavy / `story-driven` | `analogy-first` |
| `comparison-based` | `code-first` or `visual-first` |
| `myth-busting` | `question-first` or `explain-first` |

`teaching_style_reason` should explain why this specific lesson's content (not just its category) justified the choice — e.g. an unusually strong `analogy` field might override a `problem-first` lesson toward `analogy-first` if the analogy is more memorable than the code.

### 5.3 Emotional Goal (`emotional_goal`, `emotional_goal_reason`)

One of: `surprise`, `curiosity`, `confidence`, `relief`, `satisfaction`.

This is the feeling the reel is engineered to produce by its final beat, chosen to match the hook: a `common-bug` or `myth` hook that gets resolved tends to land as `surprise` or `relief`; a `question` hook tends to resolve as `curiosity` satisfied or `confidence`; a `performance-comparison` tends to land as `satisfaction`. The emotional goal is not decoration — it's the criterion `retention_goal` (Section 5.8) is written against.

### 5.4 Visual Strategy (`visual_style`, `supporting_visuals`, `visual_style_reason`)

`visual_style` (primary, one of): `full-screen-typography`, `code-animation`, `diagram`, `cursor-movement`, `highlight-animation`, `split-screen`, `timeline`. `supporting_visuals` (0-2 secondary cues) may draw from the same list or from the lesson's own `visual_notes`.

The primary visual should be chosen to make the lesson's `core_example.focus_note` land visually, not to be visually interesting in the abstract — e.g. a comparison-heavy lesson (`String +` vs `StringBuilder`) suits `split-screen`; a single-line failure (an exception, an out-of-bounds crash) suits `highlight-animation` or `cursor-movement`.

### 5.5 Pacing Strategy (`pacing`, `pacing_reason`, `scene_pacing`)

`pacing` is one of `fast`, `medium`, `slow`:

| Pacing | Scene count (approx.) | Duration per scene (approx.) | Best suited to |
| --- | --- | --- | --- |
| `fast` | 4-6 | 5-8s | Hooks built on a quick crash/reveal; lessons with several short `step_by_step_explanation` beats. |
| `medium` | 3-4 | 8-12s | The default; most direct-explanation and comparison lessons. |
| `slow` | 2-3 | 12-18s | A single dense idea (an analogy that needs room to land, e.g. HashMap's key-value model) that would feel rushed if cut quickly. |

`scene_pacing` is a required, ordered array of `{ scene, duration_seconds }` breaking the reel into its actual beats with approximate timing. It should sum to slightly less than `target_duration_seconds`, leaving room for transitions. `scene_pacing` may **resequence** the lesson's `step_by_step_explanation` for storytelling effect (e.g. a cold-open crash followed by a "rewind" to context, as in the Arrays revision example) — resequencing presentation order is a Content Director decision; it must still cover every concept the lesson commits to, just not necessarily in the lesson's own beat order.

### 5.6 Code Strategy (`code_style`, `code_style_reason`)

One of: `minimal-example`, `comparison-example`, `bug-example`, `optimization-example`, `interview-example`. This should follow directly from the lesson's own `core_example.description` — the Content Director is choosing *how to frame* the example the lesson already committed to, not choosing a different example.

### 5.7 CTA Strategy (`cta_style`, `cta_reason`)

One of: `follow`, `save`, `comment`, `try-this-yourself`, `next-lesson-teaser`. The CTA should match the emotional payoff: content that lands as `satisfaction` (a clean optimization reveal) suits `save`; content that invites a strong opinion or shared bad experience (a common bug) suits `comment`; an early-curriculum topic with an obvious next step suits `next-lesson-teaser`.

### 5.8 Retention Goal and Estimated Watch Time (`retention_goal`, `estimated_watch_time`)

`retention_goal` is a single sentence describing the specific mechanism keeping someone watching to the end — e.g. "the curiosity gap opened by the hook question must not resolve until the final beat." This is not a metric; it's the design intent the Reviewer checks the script against. `estimated_watch_time` is a plain-language prediction, in seconds, of how long an average viewer is expected to stay — typically at or slightly below `target_duration_seconds`, reflecting realistic confidence rather than assuming full completion by default.

## 6. Output: The Content Strategy

Full field-level contract: [content-director-schema.json](content-director-schema.json). The output is consumed directly by the Script Generator as its primary directive — the lesson blueprint supplies *what*, the content strategy supplies *how*, and the Script Generator's only remaining job is choosing the exact words within both constraints.

## 7. Boundary: Content Director vs. Script Generator

| The Content Director decides | The Script Generator decides |
| --- | --- |
| Which hook type, and why | The exact hook line |
| Opening posture (`teaching_style`) | The specific narration for each beat |
| The emotional arc | The words that create that arc |
| Scene count and approximate durations | Precise timing and on-screen text formatting |
| Which visual technique | The exact animation/transition details |
| How the example is framed (`code_style`) | The final, formatted on-screen code |
| Which CTA type | The exact CTA phrasing |

If a decision requires choosing specific words, it does not belong to the Content Director — it belongs to script generation. The Content Director's output should read as a set of directions a script writer could follow without needing to make any of these seven decisions themselves.

## 8. Future Extensibility: Analytics-Informed Strategy

Phase 1 has no strategy-level analytics. `memory/memory-schema.json` tracks performance **per topic** (`performance_score`, `retention_score`), which is enough for the topic selector but not enough to answer "do myth hooks underperform in general" — that question requires aggregating performance **across topics, grouped by strategy dimension** (every reel that used `hook_type: myth`, regardless of topic).

The design assumes a future `strategy_performance` input (Section 4) shaped roughly as an aggregate table keyed by strategy dimension and value (e.g. `hook_type: myth → average performance_score across all reels that used it`), analogous to how `topic-ranking.json` uses weighted factors for topic selection. When that exists, the Content Director's decision heuristics in Section 5 shift from the static starting tables shown there to weighted recommendations informed by real outcomes — for example, if `myth` hooks consistently underperform `beginner-mistake` hooks for comparable lessons, the heuristic table in 5.1 would be adjusted to prefer `beginner-mistake` going forward, the same way `topic-ranking.json`'s weights are the tunable part of the topic selector rather than the gates.

Nothing in the schema needs to change to support this — `hook_reason` already has room to say "based on prior performance of myth hooks" once that data exists; it just can't yet.

## 9. Edge Cases

- **A lesson's content doesn't clearly favor any hook type.** The Content Director still must choose one; `confidence.flagged_uncertainties` should say the choice was close and name the runner-up, the same pattern used in `lesson-spec.md` Section 9 for narrowing decisions.
- **`scene_pacing` doesn't sum cleanly to `target_duration_seconds`.** This is expected — scene durations are approximate, and the Script Generator has final say on actual timing. The Content Director's job is to propose a reasonable shape, not a frame-accurate cut list.
- **A revision case where the prior reel's strategy is known to have underperformed.** The Content Director should choose visibly differently and say so in `confidence.flagged_uncertainties`, mirroring how the research and lesson stages respond to `memory_state` feedback — see the Arrays example in `content-director-examples.md`.
- **The lesson's `teaching_style` and its strongest content signal disagree** (e.g. tagged `direct-explanation` but the `analogy` is unusually strong). The Content Director may override the lesson's signal — see Section 3 — but should note the override in `teaching_style_reason`.
