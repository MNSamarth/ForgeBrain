# Reviewer Layer Examples

Two worked examples continuing the `java-arrays-basics` thread from [`renderer/examples.md`](../renderer/examples.md), showing both possible non-`rejected` outcomes.

## 1. Quality Score

```json
{
  "score_id": "qscore-java-arrays-basics-20260725",
  "topic_id": "java-arrays-basics",
  "based_on_video_package_id": "pkg-java-arrays-basics-20260725",
  "dimensions": {
    "technical_accuracy": 0.98,
    "pacing_fit": 0.9,
    "hook_strength": 0.8,
    "educational_clarity": 0.95,
    "production_polish": 0.97,
    "brand_consistency": 1.0
  },
  "overall_score": 0.93,
  "scoring_weights_used": {
    "technical_accuracy": 0.3,
    "pacing_fit": 0.15,
    "hook_strength": 0.2,
    "educational_clarity": 0.2,
    "production_polish": 0.1,
    "brand_consistency": 0.05
  },
  "dimension_notes": [
    { "dimension": "hook_strength", "note": "Scored via neutral-leaning AI judgment; no prior A/B data exists yet to confirm the cold-open crash actually outperforms the previous version's hook. See lesson/content-director/script/storyboard confidence_notes for this topic, all of which flag the same open question." }
  ],
  "scoring_version": "1.0.0",
  "generated_at": "2026-07-25T09:15:00Z"
}
```

## 2. Review Result — `approved`

```json
{
  "review_id": "review-java-arrays-basics-20260725",
  "topic_id": "java-arrays-basics",
  "based_on_video_package_id": "pkg-java-arrays-basics-20260725",
  "based_on_quality_score_id": "qscore-java-arrays-basics-20260725",
  "verdict": "approved",
  "hard_gate_violations": [],
  "issues": [
    {
      "severity": "minor",
      "description": "hook_strength (0.80) is the lowest-scoring dimension, consistent with this being an unproven revision strategy (cold-open-then-rewind) rather than a validated one. Not blocking, but worth monitoring once real audience data exists.",
      "suggested_stage_to_revisit": "none"
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [
      "This is the fourth pipeline stage in a row to flag the same underlying uncertainty carried from memory_state: whether the more aggressive cold-open revision strategy actually improves retention over the original 0.33 retention_score. Approval here reflects 'no violations and strong measured quality,' not confirmation the strategy works — that requires posting and measuring."
    ],
    "unresolved_conflicts": []
  },
  "reviewer_version": "1.0.0",
  "reviewed_at": "2026-07-25T09:16:00Z"
}
```

## 3. Review Result — `rejected` (counter-example)

A hypothetical for the same topic, illustrating a hard gate violation rather than a quality shortfall — shown to demonstrate that `rejected` is reserved specifically for this case, not just "scored poorly":

```json
{
  "review_id": "review-java-arrays-basics-counterexample",
  "topic_id": "java-arrays-basics",
  "based_on_video_package_id": "pkg-java-arrays-basics-counterexample",
  "based_on_quality_score_id": "qscore-java-arrays-basics-counterexample",
  "verdict": "rejected",
  "hard_gate_violations": [
    "Script line implies out-of-bounds array access is 'undefined behavior' — lesson.safety_notes explicitly requires stating Java always throws a well-defined ArrayIndexOutOfBoundsException."
  ],
  "issues": [
    {
      "severity": "blocking",
      "description": "The narration in code_narration.spoken_lines contradicts a specific, named safety_notes entry from the research stage. This is a factual-accuracy failure, not a quality-polish one.",
      "suggested_stage_to_revisit": "script"
    }
  ],
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "reviewer_version": "1.0.0",
  "reviewed_at": "2026-07-25T09:20:00Z"
}
```

Even though nothing else about this hypothetical reel need be flawed, a single hard gate violation is sufficient for `rejected` — per `reviewer-spec.md` Section 3, hard gates are never averaged against quality scores.
