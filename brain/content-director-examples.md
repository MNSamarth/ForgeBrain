# Content Director Examples

Six worked content strategies, one per requested topic, each built from a lesson blueprint and showing a genuinely different set of decisions — proving the schema doesn't collapse into one default strategy regardless of input.

**Continuity note:** `java-variables-and-data-types`, `java-for-loop`, and `java-arrays-basics` (revision) are built from the persisted lesson blueprints in [`lesson-examples/`](lesson-examples/). `java-stringbuilder`, `java-hashmap`, and `java-exceptions-basics` don't have a persisted lesson blueprint file — no lesson-stage example was written for them in the prior round — so those three are illustrative: built consistently against their real `curriculum/java-roadmap.json` entries, but without an intermediate lesson JSON committed to disk. This mirrors the same honesty pattern used in `research-examples/README.md` and `lesson-examples/README.md`.

Every field below validates against [content-director-schema.json](content-director-schema.json).

---

## 1. Variables — `java-variables-and-data-types`

**Signal used:** the lesson's core tension is a *compile-time* mismatch beginners don't expect — that's a "hidden feature" of the language, not a mistake the viewer is likely to have already made themselves, which rules out `beginner-mistake` in favor of framing it as something surprising the language already does for you.

```json
{
  "topic_id": "java-variables-and-data-types",
  "topic_title": "Variables and Data Types",
  "hook_type": "hidden-feature",
  "hook_reason": "The lesson's core_example hinges on the compiler rejecting a type mismatch before the program ever runs — most beginners don't expect Java to catch this at compile time, making it a 'hidden feature' hook rather than a mistake-driven one (the viewer hasn't necessarily made this mistake themselves yet).",
  "teaching_style": "explain-first",
  "teaching_style_reason": "The lesson's own teaching_style is direct-explanation and nothing in its content overrides that — the storage-bin analogy and the three-part explanation (declare, mismatch, overflow) read cleanly in a straightforward explain-first posture with no need for a code-first cold open.",
  "emotional_goal": "confidence",
  "emotional_goal_reason": "A hidden-feature hook that resolves into 'the language already protects you from this' lands as confidence in the tool, not surprise at a personal mistake.",
  "pacing": "medium",
  "pacing_reason": "Four distinct beats (bin analogy, three declarations, compiler rejection, overflow contrast) each need a few seconds to register — too many for a slow tier, but each beat is simple enough not to need fast-tier brevity.",
  "scene_pacing": [
    { "scene": "Hook — the hidden compiler check most beginners don't expect", "duration_seconds": 8 },
    { "scene": "Declare an int, a double, and a boolean side by side", "duration_seconds": 12 },
    { "scene": "Compiler rejects a String-into-int mismatch, live", "duration_seconds": 10 },
    { "scene": "Contrast: int overflow compiles fine but silently misbehaves — takeaway", "duration_seconds": 10 }
  ],
  "visual_style": "full-screen-typography",
  "supporting_visuals": ["Storage-bin graphic behind the three declarations"],
  "visual_style_reason": "The lesson's analogy and takeaway are conceptual, not visually complex — large, clear typography for the code and the compiler error keeps focus on the moment of rejection rather than competing with animation.",
  "code_style": "minimal-example",
  "code_style_reason": "The lesson's core_example is three short declarations plus one deliberately broken line — already minimal by design, no reframing needed.",
  "cta_style": "next-lesson-teaser",
  "cta_reason": "This is an early-curriculum topic with an obvious, concrete next step (Operators) already established by the topic selector's next_topic decision — a natural teaser rather than a save/comment prompt.",
  "retention_goal": "The viewer should stay to see whether the compiler actually stops the mismatched line, not just hear it described.",
  "estimated_watch_time": 38,
  "confidence": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 45,
  "content_director_version": "1.0.0",
  "generated_at": "2026-06-27T11:00:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## 2. Loops — `java-for-loop`

**Signal used:** the lesson is explicitly `problem-first` and opens on visibly duplicated code — the strongest hook is showing that duplication and the one-line fix, not explaining loop syntax abstractly.

```json
{
  "topic_id": "java-for-loop",
  "topic_title": "For Loops",
  "hook_type": "before-vs-after",
  "hook_reason": "The lesson's own hook (five duplicated print lines rewritten as one loop) is already a before/after structure — the strongest hook here is showing the transformation, not explaining loop mechanics first.",
  "teaching_style": "code-first",
  "teaching_style_reason": "The lesson's teaching_style is problem-first with a code-heavy walkthrough; opening directly on the duplicated code (rather than explaining what a loop is first) matches that and gets to the payoff faster.",
  "emotional_goal": "relief",
  "emotional_goal_reason": "A before/after hook resolving a visibly tedious problem lands as relief ('finally, a better way') rather than surprise or curiosity.",
  "pacing": "fast",
  "pacing_reason": "The lesson has five distinct step_by_step_explanation beats to cover in a 40-second reel — that density calls for short scenes to avoid feeling rushed at any single beat.",
  "scene_pacing": [
    { "scene": "Hook — five duplicated print lines", "duration_seconds": 6 },
    { "scene": "Rewrite as a single for loop", "duration_seconds": 8 },
    { "scene": "Break down the loop's three parts", "duration_seconds": 8 },
    { "scene": "Off-by-one trap: swap <= for <", "duration_seconds": 8 },
    { "scene": "Takeaway and CTA", "duration_seconds": 6 }
  ],
  "visual_style": "code-animation",
  "supporting_visuals": ["Loop header split into three labeled parts on screen"],
  "visual_style_reason": "The core transformation (five lines collapsing into one loop) is inherently a motion story — code-animation shows the collapse happening, which a static screen can't.",
  "code_style": "comparison-example",
  "code_style_reason": "The lesson's core_example is explicitly a duplicated-vs-loop comparison, and the off-by-one trap is itself a second, smaller comparison (<= vs <) — comparison framing carries both.",
  "cta_style": "try-this-yourself",
  "cta_reason": "Repeated code is something every beginner viewer almost certainly has sitting in their own project right now — a direct, actionable CTA fits better than a passive follow/save prompt.",
  "retention_goal": "The viewer should stay through the off-by-one trap, expecting the lesson to end at the clean rewrite and getting one more twist instead.",
  "estimated_watch_time": 34,
  "confidence": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 40,
  "content_director_version": "1.0.0",
  "generated_at": "2026-07-01T11:00:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## 3. Arrays — `java-arrays-basics` (revision)

**Signal used:** `memory_state` shows this topic previously underperformed (`performance_score: 0.41`) specifically because the hook didn't establish stakes early enough. The Content Director responds by resequencing the reveal itself — not just trusting that the lesson stage's reordering of `step_by_step_explanation` alone will fix retention.

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "hook_type": "common-bug",
  "hook_reason": "Memory shows the prior version's hook didn't establish stakes early enough (performance_score 0.41, retention_score 0.33). Leading directly with the ArrayIndexOutOfBoundsException crash — rather than the lesson's own setup-first beat order — makes the stakes immediate and visceral instead of explained.",
  "teaching_style": "question-first",
  "teaching_style_reason": "The lesson's teaching_style is problem-first; this strategy sharpens that further into an explicit question posed by the cold-open crash ('what just happened, and why?') rather than a flat problem statement, to differentiate this revision from whatever weaker opening the prior version used.",
  "emotional_goal": "surprise",
  "emotional_goal_reason": "A cold-open crash is designed to be jarring first and explained second — surprise is the honest target, not curiosity, since the viewer sees the failure before they have context for it.",
  "pacing": "fast",
  "pacing_reason": "Short scenes keep the cold-open crash from losing its impact through a slow lead-in, and match the lesson's own front-loaded restructuring of its beats.",
  "scene_pacing": [
    { "scene": "Cold open — array index crash mid-program", "duration_seconds": 5 },
    { "scene": "Rewind — five separate score variables, the problem", "duration_seconds": 8 },
    { "scene": "Declare and fill the array, indices from 0", "duration_seconds": 10 },
    { "scene": "Return to the crash with the locker analogy resolved", "duration_seconds": 10 },
    { "scene": "Takeaway and comment CTA", "duration_seconds": 8 }
  ],
  "visual_style": "highlight-animation",
  "supporting_visuals": ["Numbered-locker diagram reappearing at the resolved crash"],
  "visual_style_reason": "The exception message itself is the single most important visual moment in this reel — highlight-animation draws the eye to that exact line the instant it fails, both at the cold open and again when it's resolved.",
  "code_style": "bug-example",
  "code_style_reason": "The lesson's core_example is explicitly built around triggering ArrayIndexOutOfBoundsException — bug-example framing matches it directly rather than softening it into a generic minimal example.",
  "cta_style": "comment",
  "cta_reason": "A common, relatable crash invites viewers to share their own 'I got this exact exception' story — comment engagement fits a shared-mistake hook better than a save or follow prompt.",
  "retention_goal": "The viewer should stay past the cold-open crash specifically to find out why it happened, since the lesson deliberately withholds the explanation for the first beat.",
  "estimated_watch_time": 36,
  "confidence": {
    "overall_confidence": "medium",
    "flagged_uncertainties": [
      "This strategy goes further than the lesson stage's own revision (which only reordered its teaching beats) by resequencing presentation into a cold-open-then-rewind structure. That's a bigger bet than a pacing fix alone, and its effect on retention can only be confirmed once this version is posted and measured against the 0.33 retention_score baseline."
    ],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 45,
  "content_director_version": "1.0.0",
  "generated_at": "2026-07-16T12:00:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## 4. StringBuilder — `java-stringbuilder`

**Signal used:** the curriculum's own `example_ideas` for this topic are explicitly a speed comparison — the strongest hook and visual are both performance-driven, not conceptual.

```json
{
  "topic_id": "java-stringbuilder",
  "topic_title": "StringBuilder",
  "hook_type": "performance-comparison",
  "hook_reason": "The topic's defining tension is speed: repeated String concatenation vs StringBuilder in a loop. A performance-comparison hook uses that tension directly rather than starting from syntax.",
  "teaching_style": "visual-first",
  "teaching_style_reason": "The comparison is best felt, not explained — a visual race between two approaches building the same string communicates the point faster than a verbal walkthrough of why String concatenation is slow.",
  "emotional_goal": "satisfaction",
  "emotional_goal_reason": "A clean, visible speed win (StringBuilder finishing first) delivers a satisfying resolution rather than surprise or relief.",
  "pacing": "medium",
  "pacing_reason": "Four beats (hook, the slow approach, the race, the fix) each need enough time to actually show the comparison landing, ruling out a fast tier that would cut the race scene too short to register.",
  "scene_pacing": [
    { "scene": "Hook — performance comparison teaser", "duration_seconds": 8 },
    { "scene": "Show string concatenation with + inside a loop", "duration_seconds": 8 },
    { "scene": "Split-screen race: + concatenation vs StringBuilder", "duration_seconds": 12 },
    { "scene": "Chained .append() calls and takeaway", "duration_seconds": 8 }
  ],
  "visual_style": "split-screen",
  "supporting_visuals": ["Progress/timer bar racing on each side"],
  "visual_style_reason": "Two approaches building the same result at different speeds is the textbook case for split-screen — showing both simultaneously makes the performance gap visible instead of stated.",
  "code_style": "optimization-example",
  "code_style_reason": "This topic exists specifically to fix a performance problem in existing code, which is exactly what optimization-example framing is for — the code isn't buggy or wrong, it's just not the best approach.",
  "cta_style": "save",
  "cta_reason": "A concrete, applicable optimization tip is exactly the kind of content viewers bookmark to apply to their own code later — save fits better than a discussion-driving comment prompt.",
  "retention_goal": "The viewer should stay through the split-screen race specifically to see which side actually finishes first, even though the outcome is telegraphed by the hook.",
  "estimated_watch_time": 33,
  "confidence": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 40,
  "content_director_version": "1.0.0",
  "generated_at": "2026-07-18T09:00:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## 5. HashMap — `java-hashmap`

**Signal used:** HashMap is one of the most commonly asked data-structure topics in real Java interviews, and the curriculum's `example_ideas` (word-frequency counter, `getOrDefault`) are compact enough to support a slower, analogy-led pace instead of a fast comparison.

```json
{
  "topic_id": "java-hashmap",
  "topic_title": "HashMap",
  "hook_type": "interview-question",
  "hook_reason": "HashMap is one of the most frequently asked data-structure topics in real Java interviews; framing the hook around that real-world stake gives the topic weight beyond 'here's a useful class'.",
  "teaching_style": "analogy-first",
  "teaching_style_reason": "Key-value lookup is an abstract enough idea that a concrete analogy (a coat check: give a key, get a value back instantly) grounds it faster than diving into map syntax cold.",
  "emotional_goal": "curiosity",
  "emotional_goal_reason": "An interview-question hook naturally opens a gap ('would I actually know how to answer this?') that curiosity, not surprise or relief, is the right target to hold open until the resolution.",
  "pacing": "slow",
  "pacing_reason": "The analogy needs room to fully land before the code appears, and the topic has only three real beats — stretching each one slightly avoids a rushed feel that would undercut the analogy-first approach.",
  "scene_pacing": [
    { "scene": "Hook — framed as a classic interview question", "duration_seconds": 12 },
    { "scene": "Coat-check analogy: key in, value out, instantly", "duration_seconds": 15 },
    { "scene": "Minimal word-frequency example and takeaway", "duration_seconds": 15 }
  ],
  "visual_style": "diagram",
  "supporting_visuals": ["Key-value pairs shown as labeled boxes filling in as the example runs"],
  "visual_style_reason": "The key-value relationship is inherently structural — a diagram makes the mapping visible in a way code alone doesn't, especially while the analogy is still being established.",
  "code_style": "interview-example",
  "code_style_reason": "The hook explicitly frames this as interview-relevant content, so the code example should read like something worth remembering for an actual interview, not just a generic demo.",
  "cta_style": "follow",
  "cta_reason": "A satisfying, practically useful topic with clear interview relevance is strong evidence of ongoing value — a follow prompt suits a viewer who now expects more interview-relevant content.",
  "retention_goal": "The viewer should stay to find out whether they actually know the answer to the interview framing posed in the hook.",
  "estimated_watch_time": 37,
  "confidence": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 45,
  "content_director_version": "1.0.0",
  "generated_at": "2026-07-18T09:20:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## 6. Exceptions — `java-exceptions-basics`

**Signal used:** the curriculum's `example_ideas` explicitly call for "an uncaught NullPointerException crashing a program, shown live" — the strongest hook is watching it happen, not being told about it.

```json
{
  "topic_id": "java-exceptions-basics",
  "topic_title": "Exceptions Basics",
  "hook_type": "challenge",
  "hook_reason": "Posing the crash as a predict-what-happens-next moment (a challenge) before revealing the outcome uses the curriculum's 'shown live' example idea as an active moment rather than a passive demonstration.",
  "teaching_style": "code-first",
  "teaching_style_reason": "The topic's whole point is best shown, not explained first — opening on the running code that's about to crash puts the viewer in the moment before any concept is named.",
  "emotional_goal": "relief",
  "emotional_goal_reason": "Once the viewer understands why the crash happened and how to prevent it, the resolution reads as relief ('oh, that's all it was') rather than lingering surprise.",
  "pacing": "fast",
  "pacing_reason": "A short, punchy structure (challenge, crash, explanation, takeaway) keeps the live-crash moment from being diluted by a slow build-up.",
  "scene_pacing": [
    { "scene": "Hook — cursor moves down toward a risky line", "duration_seconds": 6 },
    { "scene": "Uncaught exception crashes the program live", "duration_seconds": 8 },
    { "scene": "Explain what an exception object actually represents", "duration_seconds": 10 },
    { "scene": "Takeaway and try-this-yourself CTA", "duration_seconds": 8 }
  ],
  "visual_style": "cursor-movement",
  "supporting_visuals": ["Stack trace highlighted as it prints"],
  "visual_style_reason": "Following the cursor down toward the line that's about to fail builds the challenge's tension visually before the crash lands, which a static code screenshot wouldn't create.",
  "code_style": "bug-example",
  "code_style_reason": "The lesson centers on an uncaught exception crashing a program — a direct bug-example framing, matching the curriculum's own 'shown live' example idea.",
  "cta_style": "try-this-yourself",
  "cta_reason": "Seeing an uncaught crash live naturally invites 'try triggering this yourself' — a hands-on CTA fits the challenge framing better than a passive follow or save.",
  "retention_goal": "The viewer should stay past the challenge to see whether their prediction about what happens next was right.",
  "estimated_watch_time": 29,
  "confidence": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 35,
  "content_director_version": "1.0.0",
  "generated_at": "2026-07-18T09:40:00Z",
  "based_on_lesson_version": "1.0.0"
}
```

---

## Strategy Diversity at a Glance

| Topic | Hook | Teaching Style | Emotional Goal | Pacing | Visual | Code Style | CTA |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Variables | hidden-feature | explain-first | confidence | medium | full-screen-typography | minimal-example | next-lesson-teaser |
| Loops | before-vs-after | code-first | relief | fast | code-animation | comparison-example | try-this-yourself |
| Arrays (revision) | common-bug | question-first | surprise | fast | highlight-animation | bug-example | comment |
| StringBuilder | performance-comparison | visual-first | satisfaction | medium | split-screen | optimization-example | save |
| HashMap | interview-question | analogy-first | curiosity | slow | diagram | interview-example | follow |
| Exceptions | challenge | code-first | relief | fast | cursor-movement | bug-example | try-this-yourself |

No two topics land on the same full combination, and every one of the 5 `teaching_style` values, all 3 `pacing` tiers, and all 5 `code_style` values are represented at least once across just six examples — evidence the schema doesn't default to one safe strategy regardless of input.
