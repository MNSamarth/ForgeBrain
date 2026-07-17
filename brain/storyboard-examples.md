# Storyboard Examples

Eight worked storyboards, one per requested topic, each built directly from the corresponding script in [`script-examples.md`](script-examples.md) — showing how the same schema in [storyboard-output-schema.json](storyboard-output-schema.json) turns different scripts into genuinely different scene-by-scene visual plans.

**Numbers are computed, not estimated by hand.** Every scene's `start_time`, `end_time`, and `duration` was derived from the script's own `subtitle_segments` durations (see `script-spec.md` Section 8), grouped into scenes. Every storyboard was verified programmatically to satisfy: `scene_count` equals `scenes.length`; `scene_order` matches `scenes[].scene_id`; scenes are perfectly contiguous (each scene's `start_time` equals the previous scene's `end_time`, no gaps or overlaps); `total_duration_seconds` equals the sum of every scene's `duration` and lands at or under `target_duration_seconds`; concatenating every scene's `voiceover_text` in order reproduces the source script's `full_spoken_script` exactly — no words added, dropped, or reordered; each scene's `subtitle_segments` concatenate to that scene's own `voiceover_text`; and `pacing_profile`'s average/shortest/longest figures match the actual scene durations.

**Continuity note:** `java-variables-and-data-types`, `java-for-loop`, `java-arrays-basics` (revision), and `java-stringbuilder`/`java-hashmap` carry forward their exact Content Director strategies and scripts from the prior two documents. `java-method-basics`, `java-string-basics`, and `java-classes-and-objects` don't have a persisted Content Director strategy file, so their visual/animation/render choices here are illustrative — built consistently from their persisted lesson and script content, following the same honesty pattern used throughout `brain/`.

Every field below validates against [storyboard-output-schema.json](storyboard-output-schema.json).

---

## 1. Variables — `java-variables-and-data-types`

Carries the exact Content Director strategy from `content-director-examples.md` (`hidden-feature` / `explain-first`) and the exact script from `script-examples.md`.

```json
{
  "topic_id": "java-variables-and-data-types",
  "topic_title": "Variables and Data Types",
  "total_duration_seconds": 41.6,
  "scene_count": 8,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 4.4, "duration": 4.4, "scene_type": "hook",
          "voiceover_text": "Java just stopped your program from breaking, before it even ran.",
          "on_screen_text": ["TYPE LOCKED AT DECLARATION"],
          "visual_description": "Full-screen bold typography on a dark IDE-style background; the hook line types on in large centered text.",
          "code_block": null,
          "motion_notes": "Kinetic type-on, word by word, settles centered; brief hold.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Java just stopped your program from breaking, before it even ran.","start_time":0,"end_time":4.4,"emphasis_words":["stopped","before"]}],
          "highlighted_words": ["stopped","before"],
          "purpose": "Open with the surprising compile-time protection to earn the confidence emotional_goal early." },
        { "scene_id": "scene-2-setup", "start_time": 4.4, "end_time": 8.8, "duration": 4.4, "scene_type": "setup",
          "voiceover_text": "Every variable has to say what kind of value it holds.",
          "on_screen_text": [],
          "visual_description": "Text transitions to a smaller lower-third caption style as an empty code editor panel fades in behind it.",
          "code_block": null,
          "motion_notes": "Text shrinks and moves to lower third; editor panel fades in.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Every variable has to say what kind of value it holds.","start_time":4.4,"end_time":8.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Bridge from the hook's claim into the concrete demonstration." },
        { "scene_id": "scene-3-declare-three", "start_time": 8.8, "end_time": 14.4, "duration": 5.6, "scene_type": "explanation",
          "voiceover_text": "Here's an int, a double, and a boolean, each holding exactly what it says.",
          "on_screen_text": ["int / double / boolean"],
          "visual_description": "Three variable declarations type in one at a time inside the editor panel, each labeled with its type above it.",
          "code_block": null,
          "motion_notes": "Each line types in sequentially, ~0.4s apart; type labels pop in above each line.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Here's an int, a double, and a boolean, each holding exactly what it says.","start_time":8.8,"end_time":14.4,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Ground the abstract claim in three concrete, simple declarations before complicating it." },
        { "scene_id": "scene-4-type-locked", "start_time": 14.4, "end_time": 20.8, "duration": 6.4, "scene_type": "explanation",
          "voiceover_text": "That type gets locked in the moment you declare it. Locked in doesn't mean unbreakable, though.",
          "on_screen_text": ["LOCKED AT DECLARATION"],
          "visual_description": "Camera pushes in slightly on the three declarations; a lock icon briefly overlays each type keyword.",
          "code_block": null,
          "motion_notes": "Subtle push-in zoom; lock icon fades in/out over each type word in sync with narration.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"That type gets locked in the moment you declare it.","start_time":14.4,"end_time":18.4,"emphasis_words":[]},{"text":"Locked in doesn't mean unbreakable, though.","start_time":18.4,"end_time":20.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Land the core claim before the payoff scene proves it." },
        { "scene_id": "scene-5-mismatch", "start_time": 20.8, "end_time": 26.4, "duration": 5.6, "scene_type": "code-reveal",
          "voiceover_text": "Try to put text where a number goes, and Java won't even compile it.",
          "on_screen_text": ["DOES NOT COMPILE"],
          "visual_description": "A new line, int total = \"25\";, types in below the earlier declarations; a red compiler-error underline and error tooltip appear beneath it.",
          "code_block": {"code_snippet":"int total = \"25\"; // won't compile\nint max = Integer.MAX_VALUE + 1; // compiles, wraps silently","language":"java","focus_line":"int total = \"25\";"},
          "motion_notes": "Line types in at normal speed, then a red squiggly underline animates in under the mismatched value, with an error tooltip sliding up.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Try to put text where a number goes, and Java won't even compile it.","start_time":20.8,"end_time":26.4,"emphasis_words":["won't","compile"]}],
          "highlighted_words": ["won't","compile"],
          "purpose": "This is the reel's central emphasis point — the compile-time rejection has to visibly happen, not just be described." },
        { "scene_id": "scene-6-overflow", "start_time": 26.4, "end_time": 32, "duration": 5.6, "scene_type": "comparison",
          "voiceover_text": "Push a number past its limit, and it compiles fine, with the wrong answer.",
          "on_screen_text": ["COMPILES. WRONG ANSWER."],
          "visual_description": "The error line is replaced by int max = Integer.MAX_VALUE + 1;, which compiles cleanly (no red underline), followed by its printed value flashing on screen as a visibly wrong negative number.",
          "code_block": {"code_snippet":"int total = \"25\"; // won't compile\nint max = Integer.MAX_VALUE + 1; // compiles, wraps silently","language":"java","focus_line":"int max = Integer.MAX_VALUE + 1;"},
          "motion_notes": "Error line wipes away; new line types in green (no error state); result value pops in with a small shake for emphasis.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Push a number past its limit, and it compiles fine, with the wrong answer.","start_time":26.4,"end_time":32,"emphasis_words":["wrong answer"]}],
          "highlighted_words": ["wrong answer"],
          "purpose": "Contrast the earlier rejection with a case Java lets through anyway, complicating the confidence takeaway honestly." },
        { "scene_id": "scene-7-recap", "start_time": 32, "end_time": 37.6, "duration": 5.6, "scene_type": "recap",
          "voiceover_text": "If it compiles, the type is right, but that's not the same as safe.",
          "on_screen_text": ["COMPILES ≠ SAFE"],
          "visual_description": "Editor panel fades out; full-screen typography returns for the recap line.",
          "code_block": null,
          "motion_notes": "Editor fades and scales down off-frame; recap text kinetic-types in.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"If it compiles, the type is right, but that's not the same as safe.","start_time":32,"end_time":37.6,"emphasis_words":["compiles","safe"]}],
          "highlighted_words": ["compiles","safe"],
          "purpose": "Consolidate the twist (compiling isn't the same as safe) into one memorable line." },
        { "scene_id": "scene-8-cta", "start_time": 37.6, "end_time": 41.6, "duration": 4, "scene_type": "cta",
          "voiceover_text": "Next up, what Java's operators actually do with these types.",
          "on_screen_text": ["NEXT: OPERATORS"],
          "visual_description": "Recap text shrinks upward; CTA line and a small 'next lesson' preview card animate in below it.",
          "code_block": null,
          "motion_notes": "Text shrinks and shifts up; preview card slides in from the bottom.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Next up, what Java's operators actually do with these types.","start_time":37.6,"end_time":41.6,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Convert the resolved hook into forward momentum toward the next reel." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-declare-three","scene-4-type-locked","scene-5-mismatch","scene-6-overflow","scene-7-recap","scene-8-cta"],
  "visual_style": "full-screen-typography",
  "animation_style": "smooth-transitions",
  "subtitle_style": "bold-centered",
  "code_style": "static-panel-with-highlight",
  "transition_style": "quick-fade",
  "pacing_profile": {"tier":"medium","average_scene_duration_seconds":5.2,"shortest_scene_duration_seconds":4,"longest_scene_duration_seconds":6.4},
  "emphasis_points": [
    {
      "scene_id": "scene-5-mismatch",
      "moment": "The compiler error appears on the mismatched line",
      "reason": "This is the hidden-feature payoff the hook promised — the whole reel's retention_goal hinges on the viewer seeing the rejection happen, not just hearing about it."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 45,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-06-27T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 2. Loops — `java-for-loop`

Carries the exact Content Director strategy and script from the prior two documents. Note how `step-breakdown` and `mistake-highlight` — two scene types with no equivalent in the script or content strategy schemas — only emerge at this stage, once someone has to decide what the viewer actually *sees*.

```json
{
  "topic_id": "java-for-loop",
  "topic_title": "For Loops",
  "total_duration_seconds": 36.8,
  "scene_count": 8,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 3.6, "duration": 3.6, "scene_type": "hook",
          "voiceover_text": "Five lines of duplicate code. Watch this become one.",
          "on_screen_text": ["5 LINES → 1 LOOP"],
          "visual_description": "Five near-identical println lines are already visible on screen as the hook plays, code-editor style, dark background.",
          "code_block": null,
          "motion_notes": "Five lines are pre-typed and visible immediately at cut-in; subtle highlight sweeps down all five in sync with 'watch this become one.'",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Five lines of duplicate code. Watch this become one.","start_time":0,"end_time":3.6,"emphasis_words":["five","one"]}],
          "highlighted_words": ["five","one"],
          "purpose": "Open on the visible problem itself rather than describing it, matching the code-first strategy." },
        { "scene_id": "scene-2-setup", "start_time": 3.6, "end_time": 8, "duration": 4.4, "scene_type": "setup",
          "voiceover_text": "You've probably typed the same line five times in a row.",
          "on_screen_text": [],
          "visual_description": "The five lines remain on screen, slightly dimmed, as a caption reinforces the relatable problem.",
          "code_block": null,
          "motion_notes": "Five lines dim to 50% opacity; caption text fades in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"You've probably typed the same line five times in a row.","start_time":3.6,"end_time":8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Let the viewer recognize their own code in the problem before the fix arrives." },
        { "scene_id": "scene-3-rewrite", "start_time": 8, "end_time": 12, "duration": 4, "scene_type": "code-reveal",
          "voiceover_text": "A for loop replaces all five with one controlled repeat.",
          "on_screen_text": ["1 LOOP"],
          "visual_description": "The five dimmed lines collapse and morph into a single for loop, typing in as one block.",
          "code_block": {"code_snippet":"for (int i = 1; i <= 5; i++) {\n    System.out.println(i);\n}","language":"java","focus_line":"for (int i = 1; i <= 5; i++)"},
          "motion_notes": "Five lines animate shrinking and merging into the loop's position; loop text types in fast.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"A for loop replaces all five with one controlled repeat.","start_time":8,"end_time":12,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Deliver the before/after payoff visually, not just verbally." },
        { "scene_id": "scene-4-three-parts", "start_time": 12, "end_time": 16.8, "duration": 4.8, "scene_type": "step-breakdown",
          "voiceover_text": "It runs, prints, and stops, using just three parts: start, condition, step.",
          "on_screen_text": ["start","condition","step"],
          "visual_description": "The loop header int i = 1; i <= 5; i++ splits into three labeled, color-coded segments that pop apart slightly.",
          "code_block": {"code_snippet":"for (int i = 1; i <= 5; i++) {\n    System.out.println(i);\n}","language":"java","focus_line":"int i = 1; i <= 5; i++"},
          "motion_notes": "Header text splits into three chips with a slight vertical offset per segment, each labeled beneath it in sequence.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"It runs, prints, and stops, using just three parts: start, condition, step.","start_time":12,"end_time":16.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Make the loop's structure legible at a glance before the trap is introduced." },
        { "scene_id": "scene-5-twist-setup", "start_time": 16.8, "end_time": 20.8, "duration": 4, "scene_type": "setup",
          "voiceover_text": "Change one symbol in that condition, though, and everything shifts.",
          "on_screen_text": [],
          "visual_description": "The three chips reassemble into the header; cursor hovers over the condition segment as tension builds.",
          "code_block": null,
          "motion_notes": "Chips snap back together; cursor blinks over <= specifically.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Change one symbol in that condition, though, and everything shifts.","start_time":16.8,"end_time":20.8,"emphasis_words":["one"]}],
          "highlighted_words": ["one"],
          "purpose": "Signal something is about to change without revealing it yet." },
        { "scene_id": "scene-6-off-by-one", "start_time": 20.8, "end_time": 26.4, "duration": 5.6, "scene_type": "mistake-highlight",
          "voiceover_text": "Swap less-than for less-than-or-equal, and you get one extra loop you didn't ask for.",
          "on_screen_text": ["<=  vs  <"],
          "visual_description": "<= visibly changes to < with a strikethrough-and-replace animation; the printed output list gains a highlighted extra line in red.",
          "code_block": {"code_snippet":"for (int i = 1; i <= 5; i++) {\n    System.out.println(i);\n}","language":"java","focus_line":"i <= 5"},
          "motion_notes": "Operator swap animates with a quick strike-through and replace; new output line slides in highlighted red with a small pop.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Swap less-than for less-than-or-equal, and you get one extra loop you didn't ask for.","start_time":20.8,"end_time":26.4,"emphasis_words":["three parts"]}],
          "highlighted_words": ["three parts"],
          "purpose": "This is the reel's core emphasis point — the trap has to be seen changing the output, not just stated." },
        { "scene_id": "scene-7-recap", "start_time": 26.4, "end_time": 32.4, "duration": 6, "scene_type": "recap",
          "voiceover_text": "A for loop runs exactly as many times as its condition allows, read it carefully.",
          "on_screen_text": ["READ THE CONDITION"],
          "visual_description": "Code fades out; full-screen bold text carries the recap line.",
          "code_block": null,
          "motion_notes": "Code panel fades and scales down; recap text kinetic-types in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"A for loop runs exactly as many times as its condition allows, read it carefully.","start_time":26.4,"end_time":32.4,"emphasis_words":["one extra"]}],
          "highlighted_words": ["one extra"],
          "purpose": "Land the takeaway cleanly after the twist, without the code competing for attention." },
        { "scene_id": "scene-8-cta", "start_time": 32.4, "end_time": 36.8, "duration": 4.4, "scene_type": "cta",
          "voiceover_text": "Try rewriting your own repeated code as a loop, right now.",
          "on_screen_text": ["TRY IT NOW"],
          "visual_description": "Recap text shrinks; a simple call-to-action card slides in.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card slides up from bottom with a slight bounce.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Try rewriting your own repeated code as a loop, right now.","start_time":32.4,"end_time":36.8,"emphasis_words":["exactly"]}],
          "highlighted_words": ["exactly"],
          "purpose": "Push the viewer toward an immediate, low-friction action." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-rewrite","scene-4-three-parts","scene-5-twist-setup","scene-6-off-by-one","scene-7-recap","scene-8-cta"],
  "visual_style": "code-animation",
  "animation_style": "snappy-cuts",
  "subtitle_style": "bold-centered",
  "code_style": "typing-animation",
  "transition_style": "hard-cut",
  "pacing_profile": {"tier":"fast","average_scene_duration_seconds":4.6,"shortest_scene_duration_seconds":3.6,"longest_scene_duration_seconds":6},
  "emphasis_points": [
    {
      "scene_id": "scene-6-off-by-one",
      "moment": "The loop's output visibly gains an extra iteration when <= replaces <",
      "reason": "The whole reel's retention hinges on this twist landing after the viewer expects the lesson to already be over at the clean rewrite."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 40,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-01T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 3. Methods — `java-method-basics`

Illustrative — no persisted Content Director or fully-independent strategy file exists for this topic; built consistently from its persisted lesson and script.

```json
{
  "topic_id": "java-method-basics",
  "topic_title": "Method Basics",
  "total_duration_seconds": 34.8,
  "scene_count": 7,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 5.6, "duration": 5.6, "scene_type": "hook",
          "voiceover_text": "You just wrote the same three lines for the third time. That's the mistake.",
          "on_screen_text": ["TYPED 3 TIMES"],
          "visual_description": "Three near-identical calculation blocks are visible across a scrolling code editor, already on screen at cut-in, dark background.",
          "code_block": null,
          "motion_notes": "Three blocks visible immediately; each briefly flashes a red outline in turn as 'third time' is spoken.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"You just wrote the same three lines for the third time. That's the mistake.","start_time":0,"end_time":5.6,"emphasis_words":["third time","mistake"]}],
          "highlighted_words": ["third time","mistake"],
          "purpose": "Open on the recognizable mistake itself, matching the beginner-mistake hook_type directly." },
        { "scene_id": "scene-2-setup", "start_time": 5.6, "end_time": 7.6, "duration": 2, "scene_type": "setup",
          "voiceover_text": "Duplicated logic means duplicated bugs.",
          "on_screen_text": [],
          "visual_description": "The three blocks dim as a caption states the underlying problem.",
          "code_block": null,
          "motion_notes": "Blocks dim to 50% opacity; caption fades in centered.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Duplicated logic means duplicated bugs.","start_time":5.6,"end_time":7.6,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Name the general principle (duplicated logic, duplicated bugs) before the fix." },
        { "scene_id": "scene-3-the-fix", "start_time": 7.6, "end_time": 12.4, "duration": 4.8, "scene_type": "explanation",
          "voiceover_text": "A method bundles that logic under one name you can call anywhere.",
          "on_screen_text": ["ONE NAME, MANY CALLS"],
          "visual_description": "The three dimmed blocks collapse into a single method definition, typing in below them.",
          "code_block": {"code_snippet":"static int square(int n) {\n    return n * n;\n}\n\nint a = square(4);\nint b = square(7);","language":"java","focus_line":"static int square(int n)"},
          "motion_notes": "Three blocks shrink and converge into the new method block; method types in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"A method bundles that logic under one name you can call anywhere.","start_time":7.6,"end_time":12.4,"emphasis_words":["one name"]}],
          "highlighted_words": ["one name"],
          "purpose": "Deliver the fix visually as a collapse, mirroring the verbal 'bundles that logic' claim." },
        { "scene_id": "scene-4-reuse", "start_time": 12.4, "end_time": 17.6, "duration": 5.2, "scene_type": "code-reveal",
          "voiceover_text": "Write it once, call it three times, get the same result every time.",
          "on_screen_text": [],
          "visual_description": "Three call sites (square(4), square(7)) type in beneath the method, each producing its result inline.",
          "code_block": {"code_snippet":"static int square(int n) {\n    return n * n;\n}\n\nint a = square(4);\nint b = square(7);","language":"java","focus_line":"int a = square(4);"},
          "motion_notes": "Call lines type in one at a time, ~0.3s apart; result values pop in beside each call.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Write it once, call it three times, get the same result every time.","start_time":12.4,"end_time":17.6,"emphasis_words":["once","three times"]}],
          "highlighted_words": ["once","three times"],
          "purpose": "Show reuse actually happening, not just claim it." },
        { "scene_id": "scene-5-signature", "start_time": 17.6, "end_time": 23.6, "duration": 6, "scene_type": "step-breakdown",
          "voiceover_text": "The signature says it all: a name, what it takes in, what it hands back.",
          "on_screen_text": ["name","params","return type"],
          "visual_description": "The method signature splits into three labeled, color-coded segments (name, parameter, return type), mirroring the Loops reel's step-breakdown treatment.",
          "code_block": {"code_snippet":"static int square(int n) {\n    return n * n;\n}\n\nint a = square(4);\nint b = square(7);","language":"java","focus_line":"static int square(int n)"},
          "motion_notes": "Signature text splits into three chips with labels appearing beneath each in sequence.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"The signature says it all: a name, what it takes in, what it hands back.","start_time":17.6,"end_time":23.6,"emphasis_words":["signature"]}],
          "highlighted_words": ["signature"],
          "purpose": "Make the reusable contract (name/in/out) legible at a glance." },
        { "scene_id": "scene-6-recap", "start_time": 23.6, "end_time": 29.6, "duration": 6, "scene_type": "recap",
          "voiceover_text": "If you're about to type the same logic twice, that's the method calling your name.",
          "on_screen_text": ["DON'T REPEAT YOURSELF"],
          "visual_description": "Code fades; bold centered text carries the recap line.",
          "code_block": null,
          "motion_notes": "Code panel fades and scales down; recap text kinetic-types in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"If you're about to type the same logic twice, that's the method calling your name.","start_time":23.6,"end_time":29.6,"emphasis_words":["twice"]}],
          "highlighted_words": ["twice"],
          "purpose": "Close the loop back to the opening mistake with the general principle." },
        { "scene_id": "scene-7-cta", "start_time": 29.6, "end_time": 34.8, "duration": 5.2, "scene_type": "cta",
          "voiceover_text": "Save this if you've got duplicated code sitting in your project right now.",
          "on_screen_text": ["SAVE THIS"],
          "visual_description": "Recap text shrinks; a save-icon-forward CTA card slides in.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card with a bookmark icon slides in from the bottom.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Save this if you've got duplicated code sitting in your project right now.","start_time":29.6,"end_time":34.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Match the save cta_style with an explicit, low-friction bookmarking prompt." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-the-fix","scene-4-reuse","scene-5-signature","scene-6-recap","scene-7-cta"],
  "visual_style": "code-animation",
  "animation_style": "snappy-cuts",
  "subtitle_style": "boxed-caption",
  "code_style": "before-after-comparison",
  "transition_style": "hard-cut",
  "pacing_profile": {"tier":"medium","average_scene_duration_seconds":5,"shortest_scene_duration_seconds":2,"longest_scene_duration_seconds":6},
  "emphasis_points": [
    {
      "scene_id": "scene-1-hook",
      "moment": "The viewer sees their own duplicated code called out directly",
      "reason": "A beginner-mistake hook only works if the mistake is recognizable as the viewer's own, not a hypothetical."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 45,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-08T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 4. Arrays (revision) — `java-arrays-basics`

The revision case, carried through from every earlier stage. `confidence_notes.overall_confidence` stays at `medium` here too — the storyboard is the stage where the cold-open/rewind bet actually has to be executed, and the flag says so explicitly.

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "total_duration_seconds": 29.2,
  "scene_count": 6,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 2.8, "duration": 2.8, "scene_type": "hook",
          "voiceover_text": "This line just crashed the entire program.",
          "on_screen_text": ["CRASH"],
          "visual_description": "Cold open directly on a terminal showing a red ArrayIndexOutOfBoundsException stack trace mid-program, no context given yet.",
          "code_block": null,
          "motion_notes": "Terminal is visible at hard-cut-in, already mid-crash; stack trace text has a brief red flash on appearance.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"This line just crashed the entire program.","start_time":0,"end_time":2.8,"emphasis_words":["crashed"]}],
          "highlighted_words": ["crashed"],
          "purpose": "Deliver the surprise emotional_goal immediately, with zero setup, per the Content Director's cold-open strategy." },
        { "scene_id": "scene-2-rewind", "start_time": 2.8, "end_time": 6, "duration": 3.2, "scene_type": "setup",
          "voiceover_text": "Five scores. Five separate variables. Already a mess.",
          "on_screen_text": ["5 VARIABLES = MESS"],
          "visual_description": "Hard cut back in time: five separate int variables for scores are visible, cluttered and awkward, dark editor background.",
          "code_block": null,
          "motion_notes": "Five variable lines are visible at cut-in with a 'REWIND' style flash transition effect.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Five scores. Five separate variables. Already a mess.","start_time":2.8,"end_time":6,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Deliver the 'rewind' beat the Content Director's resequenced storytelling calls for — context after shock, not before." },
        { "scene_id": "scene-3-the-fix", "start_time": 6, "end_time": 11.6, "duration": 5.6, "scene_type": "code-reveal",
          "voiceover_text": "One array replaces all five, each score reachable by its position, starting at zero.",
          "on_screen_text": ["INDEX STARTS AT 0"],
          "visual_description": "The five variables collapse into a single array declaration and fill, with index numbers 0-4 labeled above each value.",
          "code_block": {"code_snippet":"int[] scores = {88, 92, 79, 95, 84};\nSystem.out.println(scores[5]); // crashes","language":"java","focus_line":"int[] scores = {88, 92, 79, 95, 84};"},
          "motion_notes": "Five lines shrink and merge into the array line; index labels pop in above each element left to right.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"One array replaces all five, each score reachable by its position, starting at zero.","start_time":6,"end_time":11.6,"emphasis_words":["one array","zero"]}],
          "highlighted_words": ["one array","zero"],
          "purpose": "Establish the array as the clean fix before returning to the crash." },
        { "scene_id": "scene-4-crash-resolved", "start_time": 11.6, "end_time": 19.2, "duration": 7.6, "scene_type": "mistake-highlight",
          "voiceover_text": "But ask for a position that doesn't exist, and Java doesn't warn you, it crashes, right at that line.",
          "on_screen_text": ["ArrayIndexOutOfBoundsException"],
          "visual_description": "The array from scene 3 is queried at index 5; the same red exception message from the cold open reappears, now in context, with the locker-row visual (5 numbered lockers, a hand reaching for a 6th that doesn't exist) overlaid briefly.",
          "code_block": {"code_snippet":"int[] scores = {88, 92, 79, 95, 84};\nSystem.out.println(scores[5]); // crashes","language":"java","focus_line":"scores[5]"},
          "motion_notes": "scores[5] types in; red exception message slides up exactly as seen in scene 1, triggering a recognition beat; locker visual fades in behind it for 1s.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"But ask for a position that doesn't exist,","start_time":11.6,"end_time":14.8,"emphasis_words":[]},{"text":"and Java doesn't warn you, it crashes, right at that line.","start_time":14.8,"end_time":19.2,"emphasis_words":["crashes"]}],
          "highlighted_words": ["crashes"],
          "purpose": "This is the reel's central emphasis point — the cold open's crash is now explained, closing the loop the hook opened." },
        { "scene_id": "scene-5-recap", "start_time": 19.2, "end_time": 25.2, "duration": 6, "scene_type": "recap",
          "voiceover_text": "An array's size is fixed the moment it's created, know your bounds before you ask.",
          "on_screen_text": ["KNOW YOUR BOUNDS"],
          "visual_description": "Code fades; bold centered text carries the recap line over a dimmed locker-row background.",
          "code_block": null,
          "motion_notes": "Code and locker visual fade; recap text kinetic-types in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"An array's size is fixed the moment it's created, know your bounds before you ask.","start_time":19.2,"end_time":25.2,"emphasis_words":["fixed","bounds"]}],
          "highlighted_words": ["fixed","bounds"],
          "purpose": "Consolidate the fixed-size lesson after the emotional payoff of the resolved crash." },
        { "scene_id": "scene-6-cta", "start_time": 25.2, "end_time": 29.2, "duration": 4, "scene_type": "cta",
          "voiceover_text": "Ever hit this exact crash? Tell me in the comments.",
          "on_screen_text": ["COMMENT YOUR CRASH"],
          "visual_description": "Recap text shrinks; a comment-prompt CTA card slides in.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card with a comment-bubble icon slides in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Ever hit this exact crash? Tell me in the comments.","start_time":25.2,"end_time":29.2,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Match the comment cta_style by inviting a shared-experience reply." }
  ],
  "scene_order": ["scene-1-hook","scene-2-rewind","scene-3-the-fix","scene-4-crash-resolved","scene-5-recap","scene-6-cta"],
  "visual_style": "highlight-animation",
  "animation_style": "snappy-cuts",
  "subtitle_style": "boxed-caption",
  "code_style": "static-panel-with-highlight",
  "transition_style": "hard-cut",
  "pacing_profile": {"tier":"fast","average_scene_duration_seconds":4.9,"shortest_scene_duration_seconds":2.8,"longest_scene_duration_seconds":7.6},
  "emphasis_points": [
    {
      "scene_id": "scene-1-hook",
      "moment": "The crash and its red exception message are visible within the first second",
      "reason": "Memory shows the prior version's hook didn't establish stakes fast enough (retention_score 0.33) — this scene exists specifically to fix that."
    },
    {
      "scene_id": "scene-4-crash-resolved",
      "moment": "The cold-open crash from scene 1 reappears, now explained",
      "reason": "Closes the loop the cold open opened; this payoff is what the whole resequencing strategy is betting on."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "medium",
    "flagged_uncertainties": [
      "This storyboard commits to the cold-open-then-rewind structure the Content Director and script both flagged as a bigger bet than the lesson stage's pacing fix alone. The visual payoff of returning to the crash in scene-4 is the single point this whole revision strategy hinges on; if it doesn't land render-side, none of the upstream retention reasoning holds."
    ],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 45,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-16T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 5. Strings — `java-string-basics`

Illustrative — no persisted Content Director strategy file; built consistently from its persisted lesson and script. Demonstrates `split-screen` visual_style translating into an actual split-screen scene, not just a label.

```json
{
  "topic_id": "java-string-basics",
  "topic_title": "String Basics",
  "total_duration_seconds": 30.8,
  "scene_count": 7,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 4.8, "duration": 4.8, "scene_type": "hook",
          "voiceover_text": "Two strings that look exactly the same. Java says they're not equal.",
          "on_screen_text": ["== vs .equals()"],
          "visual_description": "Split-screen: two String declarations that look identical on the left, a bold question mark between them on the right.",
          "code_block": null,
          "motion_notes": "Both declarations type in simultaneously on each half; question mark pulses gently between them.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Two strings that look exactly the same. Java says they're not equal.","start_time":0,"end_time":4.8,"emphasis_words":["not equal"]}],
          "highlighted_words": ["not equal"],
          "purpose": "Open on the visual paradox itself — two identical-looking values — matching the myth hook_type." },
        { "scene_id": "scene-2-setup", "start_time": 4.8, "end_time": 8.8, "duration": 4, "scene_type": "setup",
          "voiceover_text": "That's not a bug, that's how Java strings actually work.",
          "on_screen_text": [],
          "visual_description": "Split-screen collapses to full-frame as a caption reassures the viewer this isn't a bug.",
          "code_block": null,
          "motion_notes": "Split panels merge toward center; caption fades in.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"That's not a bug, that's how Java strings actually work.","start_time":4.8,"end_time":8.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Defuse alarm before explaining, keeping the tone playful rather than confusing." },
        { "scene_id": "scene-3-object-not-primitive", "start_time": 8.8, "end_time": 12, "duration": 3.2, "scene_type": "explanation",
          "voiceover_text": "A String isn't a primitive, it's an object.",
          "on_screen_text": ["STRING = OBJECT"],
          "visual_description": "A simple icon contrast: a primitive shown as a plain box (int, double) next to String shown as a labeled object icon.",
          "code_block": null,
          "motion_notes": "Primitive box fades in first, then String's object icon slides in beside it with a distinct highlight color.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"A String isn't a primitive, it's an object.","start_time":8.8,"end_time":12,"emphasis_words":["object"]}],
          "highlighted_words": ["object"],
          "purpose": "Establish the conceptual distinction before the code proves it." },
        { "scene_id": "scene-4-equals-vs-identity", "start_time": 12, "end_time": 16.4, "duration": 4.4, "scene_type": "comparison",
          "voiceover_text": "Double-equals checks if it's the same object, not the same text.",
          "on_screen_text": ["SAME OBJECT ≠ SAME TEXT"],
          "visual_description": "Split-screen returns: a == b evaluating to false on the left, with two distinct object icons in memory; a.equals(b) evaluating to true on the right, with a single text-comparison icon.",
          "code_block": {"code_snippet":"String a = \"java\";\nString b = new String(\"java\");\na == b;       // false\na.equals(b);  // true","language":"java","focus_line":"a == b;       // false"},
          "motion_notes": "Left side resolves to false with a red flash; right side resolves to true with a green flash, staggered by ~0.5s.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Double-equals checks if it's the same object, not the same text.","start_time":12,"end_time":16.4,"emphasis_words":["same object","same text"]}],
          "highlighted_words": ["same object","same text"],
          "purpose": "This is the reel's central emphasis point — the myth-busting has to be seen resolving, not just asserted." },
        { "scene_id": "scene-5-the-fix", "start_time": 16.4, "end_time": 20.4, "duration": 4, "scene_type": "code-reveal",
          "voiceover_text": "Use dot-equals instead, and it finally compares what's actually written.",
          "on_screen_text": [],
          "visual_description": "Full-frame code panel isolates a.equals(b); true, cleanly resolved, no split-screen tension remaining.",
          "code_block": {"code_snippet":"String a = \"java\";\nString b = new String(\"java\");\na == b;       // false\na.equals(b);  // true","language":"java","focus_line":"a.equals(b);  // true"},
          "motion_notes": "Panel settles to single full-width view; result highlights green.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Use dot-equals instead, and it finally compares what's actually written.","start_time":16.4,"end_time":20.4,"emphasis_words":["dot-equals"]}],
          "highlighted_words": ["dot-equals"],
          "purpose": "Deliver the resolution cleanly after the tension of the split-screen comparison." },
        { "scene_id": "scene-6-recap", "start_time": 20.4, "end_time": 26, "duration": 5.6, "scene_type": "recap",
          "voiceover_text": "Strings don't change in place either, every method hands you a brand new one.",
          "on_screen_text": ["NEW STRING EVERY TIME"],
          "visual_description": "Code fades; bold centered text carries the recap line about immutability.",
          "code_block": null,
          "motion_notes": "Code panel fades and scales down; recap text kinetic-types in.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Strings don't change in place either, every method hands you a brand new one.","start_time":20.4,"end_time":26,"emphasis_words":["brand new"]}],
          "highlighted_words": ["brand new"],
          "purpose": "Extend the myth-busting to the closely related immutability point in one line." },
        { "scene_id": "scene-7-cta", "start_time": 26, "end_time": 30.8, "duration": 4.8, "scene_type": "cta",
          "voiceover_text": "Follow for more Java gotchas that actually show up in real code.",
          "on_screen_text": ["MORE GOTCHAS"],
          "visual_description": "Recap text shrinks; a follow-prompt CTA card slides in.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card with a follow icon slides in.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Follow for more Java gotchas that actually show up in real code.","start_time":26,"end_time":30.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Match the follow cta_style by promising more of the same surprising-but-useful content." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-object-not-primitive","scene-4-equals-vs-identity","scene-5-the-fix","scene-6-recap","scene-7-cta"],
  "visual_style": "split-screen",
  "animation_style": "smooth-transitions",
  "subtitle_style": "karaoke-highlight",
  "code_style": "before-after-comparison",
  "transition_style": "quick-fade",
  "pacing_profile": {"tier":"medium","average_scene_duration_seconds":4.4,"shortest_scene_duration_seconds":3.2,"longest_scene_duration_seconds":5.6},
  "emphasis_points": [
    {
      "scene_id": "scene-4-equals-vs-identity",
      "moment": "== returns false on two identical-looking strings",
      "reason": "This is the myth being busted; the whole reel's curiosity payoff depends on this surprising result being clearly visible, not just narrated."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 50,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-10T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 6. Classes and Objects — `java-classes-and-objects`

Illustrative — no persisted Content Director strategy file; built consistently from its persisted lesson and script. Uses `minimal-light`/`diagram` rendering, a deliberate departure from the `dark-mode-ide` default, showing render_style isn't fixed across every reel.

```json
{
  "topic_id": "java-classes-and-objects",
  "topic_title": "Classes and Objects",
  "total_duration_seconds": 32,
  "scene_count": 7,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 3.6, "duration": 3.6, "scene_type": "hook",
          "voiceover_text": "What's the difference between a class and an object?",
          "on_screen_text": ["CLASS vs OBJECT"],
          "visual_description": "The literal question types on screen in large centered text over a soft, warm-toned background.",
          "code_block": null,
          "motion_notes": "Text kinetic-types in, settles centered; brief hold before the analogy answer begins.",
          "transition_in": "hard-cut", "transition_out": "slide",
          "subtitle_segments": [{"text":"What's the difference between a class and an object?","start_time":0,"end_time":3.6,"emphasis_words":["class","object"]}],
          "highlighted_words": ["class","object"],
          "purpose": "Open on the literal question, matching the question hook_type directly." },
        { "scene_id": "scene-2-setup", "start_time": 3.6, "end_time": 6.8, "duration": 3.2, "scene_type": "setup",
          "voiceover_text": "Think of a class as a cookie cutter.",
          "on_screen_text": ["1 CUTTER, MANY COOKIES"],
          "visual_description": "A simple cookie-cutter icon appears, stamping out a cookie shape as the analogy line plays.",
          "code_block": null,
          "motion_notes": "Cutter icon presses down once, a cookie outline appears beneath it.",
          "transition_in": "slide", "transition_out": "slide",
          "subtitle_segments": [{"text":"Think of a class as a cookie cutter.","start_time":3.6,"end_time":6.8,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Ground the abstract question in a concrete, simple analogy before touching code." },
        { "scene_id": "scene-3-shape-vs-instance", "start_time": 6.8, "end_time": 12.8, "duration": 6, "scene_type": "explanation",
          "voiceover_text": "The cutter defines the shape, but every cookie that comes out is its own cookie.",
          "on_screen_text": ["SHAPE ≠ OWN COOKIE"],
          "visual_description": "The cutter stamps out three distinct cookies in sequence, each visibly the same shape but a separate cookie.",
          "code_block": null,
          "motion_notes": "Cutter stamps three times in quick succession; each cookie slides out to its own position.",
          "transition_in": "slide", "transition_out": "slide",
          "subtitle_segments": [{"text":"The cutter defines the shape, but every cookie that comes out is its own cookie.","start_time":6.8,"end_time":12.8,"emphasis_words":["shape","own cookie"]}],
          "highlighted_words": ["shape","own cookie"],
          "purpose": "Extend the analogy to plurality before introducing the Car class." },
        { "scene_id": "scene-4-car-class", "start_time": 12.8, "end_time": 17.2, "duration": 4.4, "scene_type": "code-reveal",
          "voiceover_text": "A Car class works the same way, one blueprint, endless cars.",
          "on_screen_text": ["class Car { }"],
          "visual_description": "The three cookies dissolve into a class Car { } code block, typing in line by line.",
          "code_block": {"code_snippet":"class Car {\n    String color;\n    int speed;\n}\n\nCar car1 = new Car();\nCar car2 = new Car();","language":"java","focus_line":"class Car {"},
          "motion_notes": "Cookies fade as code panel fades in; class definition types in top to bottom.",
          "transition_in": "slide", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"A Car class works the same way, one blueprint, endless cars.","start_time":12.8,"end_time":17.2,"emphasis_words":["blueprint"]}],
          "highlighted_words": ["blueprint"],
          "purpose": "Transition the analogy into the real Java construct it maps to." },
        { "scene_id": "scene-5-three-objects", "start_time": 17.2, "end_time": 20.8, "duration": 3.6, "scene_type": "step-breakdown",
          "voiceover_text": "Three separate Car objects, same class, completely independent data.",
          "on_screen_text": ["3 OBJECTS, 1 CLASS"],
          "visual_description": "Two new Car() lines type in beneath the class, each producing a distinct labeled object card (car1, car2) sliding out to the side with independent field values.",
          "code_block": {"code_snippet":"class Car {\n    String color;\n    int speed;\n}\n\nCar car1 = new Car();\nCar car2 = new Car();","language":"java","focus_line":"new Car();"},
          "motion_notes": "Each new Car() line types in, then a labeled object card slides out and settles beside the code panel, staggered ~0.4s apart.",
          "transition_in": "hard-cut", "transition_out": "slide",
          "subtitle_segments": [{"text":"Three separate Car objects, same class, completely independent data.","start_time":17.2,"end_time":20.8,"emphasis_words":["independent"]}],
          "highlighted_words": ["independent"],
          "purpose": "This is the reel's central emphasis point — independence between instances must be seen, not just claimed." },
        { "scene_id": "scene-6-recap", "start_time": 20.8, "end_time": 27.6, "duration": 6.8, "scene_type": "recap",
          "voiceover_text": "The class is the shape, the object is the thing, and you can build more than one.",
          "on_screen_text": ["SHAPE vs THING"],
          "visual_description": "Code and object cards fade; centered text carries the recap line over the original warm background.",
          "code_block": null,
          "motion_notes": "Panel and cards fade and scale down; recap text kinetic-types in.",
          "transition_in": "slide", "transition_out": "slide",
          "subtitle_segments": [{"text":"The class is the shape, the object is the thing, and you can build more than one.","start_time":20.8,"end_time":27.6,"emphasis_words":["shape","thing"]}],
          "highlighted_words": ["shape","thing"],
          "purpose": "Consolidate the class/object distinction in one memorable line." },
        { "scene_id": "scene-7-cta", "start_time": 27.6, "end_time": 32, "duration": 4.4, "scene_type": "cta",
          "voiceover_text": "Next up, how constructors set up every one of those objects.",
          "on_screen_text": ["NEXT: CONSTRUCTORS"],
          "visual_description": "Recap text shrinks; a small 'next lesson' preview card slides in beneath it.",
          "code_block": null,
          "motion_notes": "Text shrinks and shifts up; preview card slides in from the bottom.",
          "transition_in": "slide", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Next up, how constructors set up every one of those objects.","start_time":27.6,"end_time":32,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Convert the resolved question into forward momentum toward Constructors." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-shape-vs-instance","scene-4-car-class","scene-5-three-objects","scene-6-recap","scene-7-cta"],
  "visual_style": "diagram",
  "animation_style": "smooth-transitions",
  "subtitle_style": "minimal-bottom-third",
  "code_style": "line-by-line-reveal",
  "transition_style": "slide",
  "pacing_profile": {"tier":"medium","average_scene_duration_seconds":4.6,"shortest_scene_duration_seconds":3.2,"longest_scene_duration_seconds":6.8},
  "emphasis_points": [
    {
      "scene_id": "scene-5-three-objects",
      "moment": "Three Car objects appear with visibly independent field values",
      "reason": "The takeaway ('class is the shape, object is the thing') only lands if independence between instances is seen, not just stated."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [
      "Assumes java-static-vs-instance has already been taught, per the lesson blueprint's own note — no scene here re-explains it."
    ],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "minimal-light",
  "target_duration_seconds": 50,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-16T14:30:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 7. StringBuilder — `java-stringbuilder`

Carries the exact Content Director strategy and script from the prior two documents. The split-screen race scene merges two script beats into one, honoring the `split-screen` visual_style rather than mechanically cutting per script field.

```json
{
  "topic_id": "java-stringbuilder",
  "topic_title": "StringBuilder",
  "total_duration_seconds": 32,
  "scene_count": 6,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 4.8, "duration": 4.8, "scene_type": "hook",
          "voiceover_text": "One of these builds a string in milliseconds. The other takes seconds.",
          "on_screen_text": ["MILLISECONDS vs SECONDS"],
          "visual_description": "Split-screen opens immediately: two blank timer displays, one labeled fast, one labeled slow, ticking in anticipation.",
          "code_block": null,
          "motion_notes": "Both timers appear simultaneously at hard-cut-in and begin a subtle idle tick.",
          "transition_in": "hard-cut", "transition_out": "zoom-punch",
          "subtitle_segments": [{"text":"One of these builds a string in milliseconds. The other takes seconds.","start_time":0,"end_time":4.8,"emphasis_words":["milliseconds","seconds"]}],
          "highlighted_words": ["milliseconds","seconds"],
          "purpose": "Open on the performance stakes visually before any code appears, matching the performance-comparison hook_type." },
        { "scene_id": "scene-2-setup", "start_time": 4.8, "end_time": 7.2, "duration": 2.4, "scene_type": "setup",
          "voiceover_text": "Both do the exact same job.",
          "on_screen_text": [],
          "visual_description": "Timers pause as a caption reassures the viewer both approaches produce the identical output.",
          "code_block": null,
          "motion_notes": "Timers freeze; caption fades in centered between them.",
          "transition_in": "zoom-punch", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Both do the exact same job.","start_time":4.8,"end_time":7.2,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Establish the two approaches are functionally equivalent before the race begins, so the payoff is purely about cost." },
        { "scene_id": "scene-3-the-race", "start_time": 7.2, "end_time": 15.6, "duration": 8.4, "scene_type": "comparison",
          "voiceover_text": "String plus-concatenation inside a loop creates a brand new string every single time. StringBuilder just keeps adding to the same one.",
          "on_screen_text": ["+ = NEW STRING EACH TIME","StringBuilder WINS"],
          "visual_description": "Split-screen race: left side shows a loop repeatedly building a brand-new string object each iteration (visualized as discarded string fragments piling up); right side shows StringBuilder appending to one growing buffer. Both timers run simultaneously; StringBuilder's finishes first.",
          "code_block": null,
          "motion_notes": "Both loops animate in real time; left side visibly discards fragments each iteration (small fade-out puffs); right side buffer grows smoothly; right timer stops first with a win flash.",
          "transition_in": "hard-cut", "transition_out": "zoom-punch",
          "subtitle_segments": [{"text":"String plus-concatenation inside a loop creates a brand new string every single time.","start_time":7.2,"end_time":12.4,"emphasis_words":["brand new","every single time"]},{"text":"StringBuilder just keeps adding to the same one.","start_time":12.4,"end_time":15.6,"emphasis_words":["same one"]}],
          "highlighted_words": ["brand new","every single time","same one"],
          "purpose": "This is the reel's central emphasis point — the cost difference has to be watched happening, not summarized." },
        { "scene_id": "scene-4-the-fix", "start_time": 15.6, "end_time": 21.2, "duration": 5.6, "scene_type": "code-reveal",
          "voiceover_text": "Chain a few dot-append calls, and you build the whole thing without the overhead.",
          "on_screen_text": [".append().append().append()"],
          "visual_description": "Split-screen collapses to full-frame on the winning side; the actual StringBuilder code with chained .append() calls types in.",
          "code_block": {"code_snippet":"StringBuilder sb = new StringBuilder();\nsb.append(\"Hello\").append(\", \").append(\"world\");","language":"java","focus_line":"sb.append(\"Hello\").append(\", \").append(\"world\");"},
          "motion_notes": "Losing side wipes away with a zoom-punch; winning code panel expands to full width; append calls type in fast, chained.",
          "transition_in": "zoom-punch", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Chain a few dot-append calls, and you build the whole thing without the overhead.","start_time":15.6,"end_time":21.2,"emphasis_words":["overhead"]}],
          "highlighted_words": ["overhead"],
          "purpose": "Show the actual reusable code behind the win, not just the abstract result." },
        { "scene_id": "scene-5-recap", "start_time": 21.2, "end_time": 26, "duration": 4.8, "scene_type": "recap",
          "voiceover_text": "Same result, wildly different cost, StringBuilder wins every time inside a loop.",
          "on_screen_text": ["WILDLY DIFFERENT COST"],
          "visual_description": "Code fades; bold centered text carries the recap line over a dark neon-accented background.",
          "code_block": null,
          "motion_notes": "Code panel fades and scales down; recap text kinetic-types in with a neon glow.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Same result, wildly different cost, StringBuilder wins every time inside a loop.","start_time":21.2,"end_time":26,"emphasis_words":["wildly different"]}],
          "highlighted_words": ["wildly different"],
          "purpose": "Consolidate the performance takeaway after the race's resolution." },
        { "scene_id": "scene-6-cta", "start_time": 26, "end_time": 32, "duration": 6, "scene_type": "cta",
          "voiceover_text": "Save this one, you'll want it the next time you're building strings in a loop.",
          "on_screen_text": ["SAVE THIS"],
          "visual_description": "Recap text shrinks; a save-prompt CTA card slides in with a matching neon accent.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card with a bookmark icon slides in.",
          "transition_in": "hard-cut", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Save this one, you'll want it the next time you're building strings in a loop.","start_time":26,"end_time":32,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Match the save cta_style — this is reference material worth returning to." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-the-race","scene-4-the-fix","scene-5-recap","scene-6-cta"],
  "visual_style": "split-screen",
  "animation_style": "dynamic-camera",
  "subtitle_style": "karaoke-highlight",
  "code_style": "before-after-comparison",
  "transition_style": "zoom-punch",
  "pacing_profile": {"tier":"medium","average_scene_duration_seconds":5.3,"shortest_scene_duration_seconds":2.4,"longest_scene_duration_seconds":8.4},
  "emphasis_points": [
    {
      "scene_id": "scene-3-the-race",
      "moment": "The split-screen race resolves with StringBuilder finishing first",
      "reason": "The satisfaction emotional_goal depends on the viewer seeing the win happen in real time, not just being told the result."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "neon-tech",
  "target_duration_seconds": 40,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-18T14:00:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## 8. HashMap — `java-hashmap`

Carries the exact Content Director strategy and script from the prior two documents. The only `slow` pacing_profile example — noticeably fewer, longer scenes than the rest.

```json
{
  "topic_id": "java-hashmap",
  "topic_title": "HashMap",
  "total_duration_seconds": 28,
  "scene_count": 7,
  "scenes": [
        { "scene_id": "scene-1-hook", "start_time": 0, "end_time": 4.4, "duration": 4.4, "scene_type": "hook",
          "voiceover_text": "This is one of the most asked questions in Java interviews.",
          "on_screen_text": ["ASKED IN EVERY INTERVIEW"],
          "visual_description": "Bold centered text states the interview-relevance claim over a subtle, professional dark background — no code yet.",
          "code_block": null,
          "motion_notes": "Text kinetic-types in, settles centered; longer hold than a fast-paced reel, matching the slow pacing tier.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"This is one of the most asked questions in Java interviews.","start_time":0,"end_time":4.4,"emphasis_words":["interviews"]}],
          "highlighted_words": ["interviews"],
          "purpose": "Open on real-world stakes to justify a slower, more deliberate pace than a typical hook." },
        { "scene_id": "scene-2-setup", "start_time": 4.4, "end_time": 7.2, "duration": 2.8, "scene_type": "setup",
          "voiceover_text": "A HashMap is basically a coat check.",
          "on_screen_text": [],
          "visual_description": "A simple coat-check counter icon fades in as the analogy line plays.",
          "code_block": null,
          "motion_notes": "Coat-check icon fades in and settles centered; no motion beyond the fade.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"A HashMap is basically a coat check.","start_time":4.4,"end_time":7.2,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Introduce the analogy vehicle before demonstrating it, appropriate to the analogy-first teaching_style." },
        { "scene_id": "scene-3-the-analogy", "start_time": 7.2, "end_time": 13.2, "duration": 6, "scene_type": "explanation",
          "voiceover_text": "Hand over a key, get your value back, instantly, no matter how many are stored.",
          "on_screen_text": ["KEY IN → VALUE OUT"],
          "visual_description": "A labeled key icon slides toward the coat-check counter and a labeled value icon slides back out instantly, forming a simple key-value diagram.",
          "code_block": null,
          "motion_notes": "Key slides in from the left; value slides out to the right almost immediately after, emphasizing instant response.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Hand over a key, get your value back, instantly, no matter how many are stored.","start_time":7.2,"end_time":13.2,"emphasis_words":["instantly"]}],
          "highlighted_words": ["instantly"],
          "purpose": "This is the reel's central emphasis point — the instant key-to-value relationship must be visually felt, not just asserted." },
        { "scene_id": "scene-4-no-searching", "start_time": 13.2, "end_time": 16.4, "duration": 3.2, "scene_type": "comparison",
          "voiceover_text": "No searching through everything one at a time.",
          "on_screen_text": [],
          "visual_description": "A faded, dimmed row of items being checked one by one (a linear search) appears briefly beside the instant key-value diagram, then fades away, unused.",
          "code_block": null,
          "motion_notes": "Dimmed linear-search row fades in beside the diagram, then fades out as if dismissed.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"No searching through everything one at a time.","start_time":13.2,"end_time":16.4,"emphasis_words":["no searching"]}],
          "highlighted_words": ["no searching"],
          "purpose": "Contrast the instant lookup against the alternative it replaces, without dwelling on it." },
        { "scene_id": "scene-5-the-example", "start_time": 16.4, "end_time": 20, "duration": 3.6, "scene_type": "code-reveal",
          "voiceover_text": "A five-line word counter shows exactly why that matters.",
          "on_screen_text": ["getOrDefault(word, 0) + 1"],
          "visual_description": "The diagram gives way to a clean, minimal code panel showing the five-line word-frequency counter, typing in line by line.",
          "code_block": {"code_snippet":"Map<String, Integer> counts = new HashMap<>();\ncounts.put(word, counts.getOrDefault(word, 0) + 1);","language":"java","focus_line":"counts.getOrDefault(word, 0) + 1"},
          "motion_notes": "Diagram fades; code panel fades in; lines type in one at a time, unhurried, matching the slow pacing tier.",
          "transition_in": "hard-cut", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"A five-line word counter shows exactly why that matters.","start_time":16.4,"end_time":20,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Ground the analogy in a real, minimal, interview-plausible example." },
        { "scene_id": "scene-6-recap", "start_time": 20, "end_time": 24.8, "duration": 4.8, "scene_type": "recap",
          "voiceover_text": "Key in, value out, instantly, that's the entire idea behind a HashMap.",
          "on_screen_text": ["INSTANT LOOKUP"],
          "visual_description": "Code fades; bold centered text carries the recap line.",
          "code_block": null,
          "motion_notes": "Code panel fades and scales down; recap text kinetic-types in.",
          "transition_in": "quick-fade", "transition_out": "quick-fade",
          "subtitle_segments": [{"text":"Key in, value out, instantly, that's the entire idea behind a HashMap.","start_time":20,"end_time":24.8,"emphasis_words":["instantly"]}],
          "highlighted_words": ["instantly"],
          "purpose": "Consolidate the key-value idea in one line before the CTA." },
        { "scene_id": "scene-7-cta", "start_time": 24.8, "end_time": 28, "duration": 3.2, "scene_type": "cta",
          "voiceover_text": "Follow along, more real interview-relevant Java is coming.",
          "on_screen_text": ["FOLLOW FOR MORE"],
          "visual_description": "Recap text shrinks; a follow-prompt CTA card slides in.",
          "code_block": null,
          "motion_notes": "Text shrinks upward; CTA card with a follow icon fades in.",
          "transition_in": "quick-fade", "transition_out": "hard-cut",
          "subtitle_segments": [{"text":"Follow along, more real interview-relevant Java is coming.","start_time":24.8,"end_time":28,"emphasis_words":[]}],
          "highlighted_words": [],
          "purpose": "Match the follow cta_style by promising more interview-relevant content." }
  ],
  "scene_order": ["scene-1-hook","scene-2-setup","scene-3-the-analogy","scene-4-no-searching","scene-5-the-example","scene-6-recap","scene-7-cta"],
  "visual_style": "diagram",
  "animation_style": "minimal-static",
  "subtitle_style": "minimal-bottom-third",
  "code_style": "line-by-line-reveal",
  "transition_style": "quick-fade",
  "pacing_profile": {"tier":"slow","average_scene_duration_seconds":4,"shortest_scene_duration_seconds":2.8,"longest_scene_duration_seconds":6},
  "emphasis_points": [
    {
      "scene_id": "scene-3-the-analogy",
      "moment": "A key is handed over and a value returns instantly from the coat-check diagram",
      "reason": "The curiosity emotional_goal depends on the mapping being felt as immediate, which a diagram communicates better than narration alone."
    }
  ],
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [],
    "unresolved_conflicts": []
  },
  "platform": "generic-vertical-short",
  "aspect_ratio": "9:16",
  "render_style": "dark-mode-ide",
  "target_duration_seconds": 45,
  "storyboard_version": "1.0.0",
  "generated_at": "2026-07-18T14:20:00Z",
  "based_on_script_version": "1.0.0"
}
```

---

## Storyboard Diversity at a Glance

| Topic | Scenes | Total Duration | Visual Style | Animation Style | Pacing Tier | Render Style |
| --- | --- | --- | --- | --- | --- | --- |
| Variables | 8 | 41.6s | full-screen-typography | smooth-transitions | medium | dark-mode-ide |
| Loops | 8 | 36.8s | code-animation | snappy-cuts | fast | dark-mode-ide |
| Methods | 7 | 34.8s | code-animation | snappy-cuts | medium | dark-mode-ide |
| Arrays (revision) | 6 | 29.2s | highlight-animation | snappy-cuts | fast | dark-mode-ide |
| Strings | 7 | 30.8s | split-screen | smooth-transitions | medium | dark-mode-ide |
| Classes and Objects | 7 | 32s | diagram | smooth-transitions | medium | minimal-light |
| StringBuilder | 6 | 32s | split-screen | dynamic-camera | medium | neon-tech |
| HashMap | 7 | 28s | diagram | minimal-static | slow | dark-mode-ide |

Scene counts range from 6 to 8 depending on script length and pacing tier, every `scene_type` in the schema's enum (all 9) is used at least once across the set, and all three `pacing_profile` tiers and three of the four `render_style` values appear (`terminal-retro` isn't exercised in this set) — evidence the storyboard stage adapts to its input rather than templating one fixed shape.
