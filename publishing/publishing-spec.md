# Publishing Preparation Specification

Status: Design specification — no implementation yet.

This document describes ForgeBrain's Publishing Preparation stage: the layer that bundles an **approved** `VideoPackage` with everything a future upload step would need — title, description, tags, captions, thumbnail — into one `PublishingPackage`. It is the final stage in the pipeline for Phase 1.

## 1. Why This Stage Exists, and Why It Stops Short of Publishing

A reel isn't ready to hand to a platform's upload API just because it rendered successfully and passed review — it still needs a title, a description, tags, and a decision about which platform-specific conventions apply. Bundling that metadata is a distinct job from writing the reel's content (already done, upstream) and from actually posting it (deliberately **not** done here).

Per `docs/ARCHITECTURE.md` Section 10, auto-posting to social platforms is explicitly out of Phase 1 scope. Publishing Preparation exists specifically to do everything *up to* that boundary — so that whenever a real publishing/upload stage is eventually built, it has a complete, well-formed package to hand to a platform API, and adding that stage later doesn't require reopening any decision made here.

## 2. Position in the Pipeline

```
Reviewer → Publishing Package
```

Publishing Preparation only runs on a `ReviewResult` with `verdict: "approved"` — see Section 4. It is the last stage in the currently-defined pipeline (`docs/PIPELINE.md`).

## 3. Inputs

| Input | Source | Purpose |
| --- | --- | --- |
| `review_result` | Full `ReviewResult`, matching `reviewer-schema.json` | Must have `verdict: "approved"` — the hard precondition for this stage running at all. |
| `video_package` | Full `VideoPackage` | The video and thumbnail file references being packaged. |
| `subtitle_result` | Full `SubtitleResult` | The captions file reference being packaged. |
| `lesson` | Full lesson blueprint | `lesson_objective` and `beginner_takeaway` seed the description; `topic_title` seeds the title. |
| `script` | Full script object | `hook` and `retention_hook`-derived language often make for a stronger title than the plain topic title. |
| `curriculum_context` | The topic's entry in `curriculum/java-roadmap.json` | `next_topics` informs a "part of a series" note; `level` informs category/tagging. |
| `platform` | Carried through the pipeline | Different platforms have different title-length and hashtag conventions — see Section 5. |

## 4. The Approval Precondition

Publishing Preparation must refuse to run against any `review_result` whose `verdict` is not `"approved"`. This isn't a soft preference — a `PublishingPackage` existing at all should be provable evidence that the reel passed the Reviewer's hard gates and quality bar. Allowing a `needs_revision` or `rejected` reel to produce a publishing-ready bundle would defeat the entire point of having a review gate.

## 5. Output: PublishingPackage

Full field-level contract: [publishing-schema.json](publishing-schema.json). `PublishingPackage` bundles file references (video, thumbnail, captions) with one nested `PublishingMetadata` object (title, description, tags, hashtags, category, language_code) and a `scheduling` block that is deliberately inert in Phase 1 (`status` can only ever be `draft` or `ready`; nothing here triggers an actual post — see Section 1).

`platform_variants` allows the same underlying reel to carry slightly different title/description phrasing per platform (e.g. YouTube Shorts titles tend to be more descriptive; TikTok captions lean more casual) without duplicating the whole package — the default `metadata` is the fallback when no platform-specific variant exists.

## 6. Title and Description Generation

Titles and descriptions are not invented independently of everything upstream — they're assembled from material already decided:

- The title draws from `script.hook` or `script.retention_hook`, trimmed to platform length conventions, falling back to `lesson.topic_title` phrased as a question or claim if the hook doesn't read well standalone.
- The description opens with `lesson.lesson_objective`, restates `lesson.beginner_takeaway` as a closing line, and — when `next_topics` exists — adds a one-line "next up" teaser, mirroring the same continuity device the Content Director already uses for `cta_style: "next-lesson-teaser"`.

This keeps publishing metadata consistent with what the reel actually says, rather than an independently-written marketing description that could drift from the content.

## 7. Future Extensibility

- **Real publishing/upload integration** — `PublishingPackage` is designed to be the direct input to a future publishing service; nothing here needs to change shape when that service is built, only when it's called.
- **Scheduling** — `scheduling.scheduled_for` is already reserved and nullable; a future Cloud Scheduler-driven publishing pass (see `docs/CONFIGURATION.md`) would populate it without a schema change.
- **A/B testing titles/thumbnails** — mirrors `variant_id` in `script-output-schema.json`; multiple `PublishingMetadata` variants per package is a natural extension once that's worth building.
- **Platform-specific compliance checks** — `platform_variants` is the natural place a future per-platform content-policy check would attach its own notes.

## 8. Edge Cases

- **`review_result.verdict` is not `approved`.** Publishing Preparation does not run — see Section 4. This should be an enforced precondition, not a documented convention a caller might skip.
- **No strong hook line exists to build a title from** (e.g. an `explain-first` teaching_style with a plain, functional hook). The fallback (topic title phrased as a question or claim, Section 6) should still produce a usable title, not a truncated, awkward version of the spoken hook.
- **A topic has no `next_topics`** (a terminal curriculum node). The "next up" teaser is simply omitted from the description, not replaced with a generic placeholder.
