# Quality Scoring Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Quality Scoring component: the layer that evaluates a finished `VideoPackage` across several independent quality dimensions and produces a structured `QualityScore`. It is not a pipeline stage of its own — it is a scoring function the [Reviewer](reviewer-spec.md) consumes as one input to its pass/fail decision.

## 1. Why Scoring Is Separate from the Review Decision

"How good is this reel" and "should this reel be published" are related but different questions. A reel can score well on every quality dimension and still fail review over a single hard safety violation (a factual error the script should never have contained); conversely, a reel with a merely mediocre hook-strength score might still be approved if nothing else is wrong. Keeping scoring and the approve/reject decision as separate components means the *scoring criteria* can evolve (new dimensions, retuned weights, eventually informed by real audience data) without changing what counts as an automatic disqualification, and vice versa — see `reviewer-spec.md` Section 3 for how the two combine.

## 2. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `video_package` | Full `VideoPackage`, matching `render-schema.json` | Technical metadata (duration, resolution, file size) for the production_polish dimension. |
| `storyboard` | Full storyboard object | The planned pacing and emphasis points, compared against what was actually produced. |
| `voice_result` | Full `VoiceResult` | Realized timing and drift — feeds the pacing dimension directly. |
| `script` | Full script object | The words actually said, checked against the lesson's safety/accuracy material for the technical_accuracy dimension. |
| `lesson` | Full lesson blueprint | `safety_notes` and `beginner_takeaway` — the ground truth technical_accuracy and educational_clarity are scored against. |
| `content_director_output` | Full content strategy | `retention_goal` and `emotional_goal` — the intent hook_strength is scored against. |

## 3. The Six Scoring Dimensions

| Dimension | What it measures | Primary signal |
| --- | --- | --- |
| `technical_accuracy` | Whether the script avoids every statement the lesson's `safety_notes` and `what_to_avoid_saying` explicitly flagged as incorrect. | Text-level comparison between the script and `lesson.safety_notes`/`script.what_to_avoid_saying`. This is the dimension most directly connected to a hard gate — see `reviewer-spec.md` Section 3. |
| `pacing_fit` | Whether the realized video's actual timing stayed reasonably close to the storyboard's planned `pacing_profile`. | `voice_result.total_duration_drift_seconds` relative to `voice_result.drift_threshold_seconds`. |
| `hook_strength` | Whether the opening scene plausibly delivers the `content_director_output.emotional_goal` it was designed for. | This is the most subjective dimension in Phase 1 and the one most likely to need AI-assisted judgment (a Vertex AI model evaluating the hook against its stated strategy) rather than a mechanical check — flagged explicitly as such, not disguised as objective. |
| `educational_clarity` | Whether the single committed concept (`lesson.lesson_objective`) is identifiable from the script alone, without outside context. | Structural check: does `script.recap_line` restate `lesson.beginner_takeaway` in substance. |
| `production_polish` | Whether the rendered artifact meets basic technical standards — correct resolution, codec, non-trivial file size, duration within an acceptable band of the target. | `video_package` technical fields checked against `render-spec.md` Section 6's standards. |
| `brand_consistency` | Whether the resolved assets actually matched the storyboard's requested `render_style`. | Cross-check between `video_package` and the `AssetManifest` used to produce it. |

Each dimension is scored `0.0`–`1.0`. `overall_score` is a weighted average — weights are configuration, not fixed in this schema, the same way `topic-ranking.json` keeps its weights external to the algorithm description (see `brain/topic-selector-spec.md` Section 3).

## 4. Output

Full field-level contract: [quality-scoring-schema.json](quality-scoring-schema.json). A `QualityScore` is always produced, even when the Reviewer will reject the reel outright over a hard gate violation — a low-confidence or clearly-flawed reel still gets scored, both for audit trail and because a future analytics pass (`analytics/analytics-spec.md`) benefits from having scores for every reel produced, not just the ones that passed.

## 5. Future Extensibility

- **Analytics-informed weighting** — mirrors the Content Director's `strategy_performance` extension point (`brain/content-director-spec.md` Section 8): once real audience retention data exists per topic (via `analytics/analytics-spec.md`), dimension weights (and eventually the dimensions' own scoring logic) become tunable against real outcomes instead of fixed defaults.
- **Per-dimension explanations** — `dimension_notes` already reserves a place for a short rationale per dimension; a future version could require this to be populated for any dimension scoring below a threshold.
- **AI-assisted subjective scoring** — `hook_strength` (Section 3) is explicitly flagged as the dimension most likely to need a Vertex AI judgment call rather than a mechanical rule; the schema doesn't assume how that judgment is produced, only what shape its output takes.

## 6. Edge Cases

- **A dimension can't be meaningfully scored from the available inputs** (e.g. `hook_strength` with no AI judgment step wired up yet in Phase 1). The dimension should be scored `null`-equivalent is not permitted by the schema (every dimension is required); instead, Phase 1 implementations should use a neutral default (`0.5`) and note the limitation in `dimension_notes`, rather than omitting the dimension or fabricating false confidence.
- **`overall_score` is high but `technical_accuracy` is low.** The Reviewer, not Quality Scoring, decides what to do with this — Quality Scoring's job stops at reporting the numbers accurately, never at deciding they average out to "good enough."
