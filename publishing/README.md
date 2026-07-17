# Publishing — Package Preparation

This folder holds ForgeBrain's final pipeline stage for Phase 1: bundling an **approved** reel (per [`reviewer/`](../reviewer/)) with everything a future publishing/upload step would need.

| File | Purpose |
| --- | --- |
| [publishing-spec.md](publishing-spec.md) | Purpose, inputs, the approval precondition, title/description generation, and future extensibility. |
| [publishing-schema.json](publishing-schema.json) | The `PublishingPackage` and nested `PublishingMetadata` contract. |
| [examples.md](examples.md) | A worked example, including a platform-specific title/description variant. |

## The one thing this stage never does

It does not publish anything. Per `docs/ARCHITECTURE.md`'s Phase 1 scope, auto-posting to social platforms is explicitly excluded — `publishing-schema.json`'s `scheduling.status` can only ever reach `draft` or `ready` in this phase, never an actually-posted state. This stage exists to make sure that whenever a real upload integration is built, it has a complete, well-formed package waiting for it — not to do the posting itself.

## Hard precondition

This stage refuses to run against anything but a `review_result.verdict: "approved"`. A `PublishingPackage` existing at all should be provable evidence the reel passed every gate before it — see `publishing-spec.md` Section 4.
