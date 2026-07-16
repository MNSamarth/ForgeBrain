# Script Examples

Eight worked scripts, one per requested topic, each built from a lesson blueprint and a Content Director strategy, showing how the same schema in [script-output-schema.json](script-output-schema.json) produces genuinely different scripts depending on `hook_type` and `teaching_style`.

**Continuity note:** `java-variables-and-data-types`, `java-for-loop`, `java-method-basics`, `java-arrays-basics` (revision), `java-string-basics`, and `java-classes-and-objects` are built from the persisted lesson blueprints in [`lesson-examples/`](lesson-examples/). Variables, Loops, and Arrays also carry forward the exact Content Director strategies from [`content-director-examples.md`](content-director-examples.md). `java-stringbuilder` and `java-hashmap` reuse their Content Director strategies from that same document; Methods, Strings, and Classes and Objects don't have a persisted Content Director strategy file, so their `hook_type`/`teaching_style`/etc. here are illustrative — chosen consistently with their lesson content, following the same honesty pattern used throughout `brain/`.

**Numbers are computed, not estimated by hand.** Every `word_count`, `estimated_duration_seconds`, and per-segment `subtitle_segments[].estimated_duration_seconds` below was generated programmatically from the actual script text at a 2.5 words/second baseline rate (see `script-spec.md` Section 6), then verified to match `full_spoken_script` exactly. `full_spoken_script` is confirmed to equal the concatenation of `hook` + `intro_line` + every `main_script[].spoken_line` + every `code_narration.spoken_lines[]` + `recap_line` + `cta_line`, in that order.

Every field below validates against [script-output-schema.json](script-output-schema.json).

---

## 1. Variables — `java-variables-and-data-types`

Strategy: `hook_type: hidden-feature`, `teaching_style: explain-first` (carried from `content-director-examples.md`).

```json
{
  "topic_id": "java-variables-and-data-types",
  "topic_title": "Variables and Data Types",
  "variant_id": "primary",
  "hook": "Java just stopped your program from breaking, before it even ran.",
  "intro_line": "Every variable has to say what kind of value it holds.",
  "main_script": [
    { "beat": "declare_three", "spoken_line": "Here's an int, a double, and a boolean, each holding exactly what it says." },
    { "beat": "type_locked", "spoken_line": "That type gets locked in the moment you declare it." },
    { "beat": "not_unbreakable", "spoken_line": "Locked in doesn't mean unbreakable, though." }
  ],
  "code_narration": {
    "spoken_lines": [
      "Try to put text where a number goes, and Java won't even compile it.",
      "Push a number past its limit, and it compiles fine, with the wrong answer."
    ],
    "code_snippet": "int total = \"25\"; // won't compile\nint max = Integer.MAX_VALUE + 1; // compiles, wraps silently",
    "focus_line": "int total = \"25\";"
  },
  "recap_line": "If it compiles, the type is right, but that's not the same as safe.",
  "cta_line": "Next up, what Java's operators actually do with these types.",
  "full_spoken_script": "Java just stopped your program from breaking, before it even ran. Every variable has to say what kind of value it holds. Here's an int, a double, and a boolean, each holding exactly what it says. That type gets locked in the moment you declare it. Locked in doesn't mean unbreakable, though. Try to put text where a number goes, and Java won't even compile it. Push a number past its limit, and it compiles fine, with the wrong answer. If it compiles, the type is right, but that's not the same as safe. Next up, what Java's operators actually do with these types.",
  "scene_text": [
    { "scene_reference": "Hook — the hidden compiler check most beginners don't expect", "text": "TYPE LOCKED AT DECLARATION" },
    { "scene_reference": "Declare an int, a double, and a boolean side by side", "text": "int / double / boolean" },
    { "scene_reference": "Compiler rejects a String-into-int mismatch, live", "text": "DOES NOT COMPILE" },
    { "scene_reference": "Contrast: int overflow compiles fine but silently misbehaves — takeaway", "text": "COMPILES. WRONG ANSWER." }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "Java just stopped your program from breaking, before it even ran.", "source_field": "hook", "estimated_duration_seconds": 4.4, "emphasis_words": ["stopped", "before"] },
    { "order": 2, "text": "Every variable has to say what kind of value it holds.", "source_field": "intro_line", "estimated_duration_seconds": 4.4, "emphasis_words": [] },
    { "order": 3, "text": "Here's an int, a double, and a boolean, each holding exactly what it says.", "source_field": "main_script", "estimated_duration_seconds": 5.6, "emphasis_words": [] },
    { "order": 4, "text": "That type gets locked in the moment you declare it.", "source_field": "main_script", "estimated_duration_seconds": 4, "emphasis_words": [] },
    { "order": 5, "text": "Locked in doesn't mean unbreakable, though.", "source_field": "main_script", "estimated_duration_seconds": 2.4, "emphasis_words": [] },
    { "order": 6, "text": "Try to put text where a number goes, and Java won't even compile it.", "source_field": "code_narration", "estimated_duration_seconds": 5.6, "emphasis_words": ["won't", "compile"] },
    { "order": 7, "text": "Push a number past its limit, and it compiles fine, with the wrong answer.", "source_field": "code_narration", "estimated_duration_seconds": 5.6, "emphasis_words": ["wrong answer"] },
    { "order": 8, "text": "If it compiles, the type is right, but that's not the same as safe.", "source_field": "recap_line", "estimated_duration_seconds": 5.6, "emphasis_words": ["compiles", "safe"] },
    { "order": 9, "text": "Next up, what Java's operators actually do with these types.", "source_field": "cta_line", "estimated_duration_seconds": 4, "emphasis_words": [] }
  ],
  "word_count": 104,
  "estimated_duration_seconds": 41.6,
  "tone": "calm-confident",
  "hook_type": "hidden-feature",
  "teaching_style": "explain-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 45,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-06-27T12:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 2. Loops — `java-for-loop`

Strategy: `hook_type: before-vs-after`, `teaching_style: code-first`.

```json
{
  "topic_id": "java-for-loop",
  "topic_title": "For Loops",
  "variant_id": "primary",
  "hook": "Five lines of duplicate code. Watch this become one.",
  "intro_line": "You've probably typed the same line five times in a row.",
  "main_script": [
    { "beat": "the_fix", "spoken_line": "A for loop replaces all five with one controlled repeat." },
    { "beat": "three_parts", "spoken_line": "It runs, prints, and stops, using just three parts: start, condition, step." },
    { "beat": "the_twist", "spoken_line": "Change one symbol in that condition, though, and everything shifts." }
  ],
  "code_narration": {
    "spoken_lines": ["Swap less-than for less-than-or-equal, and you get one extra loop you didn't ask for."],
    "code_snippet": "for (int i = 1; i <= 5; i++) {\n    System.out.println(i);\n}",
    "focus_line": "i <= 5"
  },
  "recap_line": "A for loop runs exactly as many times as its condition allows, read it carefully.",
  "cta_line": "Try rewriting your own repeated code as a loop, right now.",
  "full_spoken_script": "Five lines of duplicate code. Watch this become one. You've probably typed the same line five times in a row. A for loop replaces all five with one controlled repeat. It runs, prints, and stops, using just three parts: start, condition, step. Change one symbol in that condition, though, and everything shifts. Swap less-than for less-than-or-equal, and you get one extra loop you didn't ask for. A for loop runs exactly as many times as its condition allows, read it carefully. Try rewriting your own repeated code as a loop, right now.",
  "scene_text": [
    { "scene_reference": "Hook — five duplicated print lines", "text": "5 LINES → 1 LOOP" },
    { "scene_reference": "Rewrite as a single for loop", "text": "start; condition; step" },
    { "scene_reference": "Off-by-one trap: swap <= for <", "text": "<=  vs  <" },
    { "scene_reference": "Takeaway and CTA", "text": "READ THE CONDITION" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "Five lines of duplicate code. Watch this become one.", "source_field": "hook", "estimated_duration_seconds": 3.6, "emphasis_words": ["five", "one"] },
    { "order": 2, "text": "You've probably typed the same line five times in a row.", "source_field": "intro_line", "estimated_duration_seconds": 4.4, "emphasis_words": [] },
    { "order": 3, "text": "A for loop replaces all five with one controlled repeat.", "source_field": "main_script", "estimated_duration_seconds": 4, "emphasis_words": [] },
    { "order": 4, "text": "It runs, prints, and stops, using just three parts: start, condition, step.", "source_field": "main_script", "estimated_duration_seconds": 4.8, "emphasis_words": [] },
    { "order": 5, "text": "Change one symbol in that condition, though, and everything shifts.", "source_field": "main_script", "estimated_duration_seconds": 4, "emphasis_words": ["one"] },
    { "order": 6, "text": "Swap less-than for less-than-or-equal, and you get one extra loop you didn't ask for.", "source_field": "code_narration", "estimated_duration_seconds": 5.6, "emphasis_words": ["three parts"] },
    { "order": 7, "text": "A for loop runs exactly as many times as its condition allows, read it carefully.", "source_field": "recap_line", "estimated_duration_seconds": 6, "emphasis_words": ["one extra"] },
    { "order": 8, "text": "Try rewriting your own repeated code as a loop, right now.", "source_field": "cta_line", "estimated_duration_seconds": 4.4, "emphasis_words": ["exactly"] }
  ],
  "word_count": 92,
  "estimated_duration_seconds": 36.8,
  "tone": "energetic",
  "hook_type": "before-vs-after",
  "teaching_style": "code-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 40,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-01T12:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 3. Methods — `java-method-basics`

Strategy: `hook_type: beginner-mistake`, `teaching_style: code-first` (illustrative — no persisted Content Director file for this topic). Demonstrates the task's own worked example: a mistake hook opens with the mistake itself, not a description of it.

```json
{
  "topic_id": "java-method-basics",
  "topic_title": "Method Basics",
  "variant_id": "primary",
  "hook": "You just wrote the same three lines for the third time. That's the mistake.",
  "intro_line": "Duplicated logic means duplicated bugs.",
  "main_script": [
    { "beat": "the_fix", "spoken_line": "A method bundles that logic under one name you can call anywhere." },
    { "beat": "reuse", "spoken_line": "Write it once, call it three times, get the same result every time." }
  ],
  "code_narration": {
    "spoken_lines": ["The signature says it all: a name, what it takes in, what it hands back."],
    "code_snippet": "static int square(int n) {\n    return n * n;\n}\n\nint a = square(4);\nint b = square(7);",
    "focus_line": "static int square(int n)"
  },
  "recap_line": "If you're about to type the same logic twice, that's the method calling your name.",
  "cta_line": "Save this if you've got duplicated code sitting in your project right now.",
  "full_spoken_script": "You just wrote the same three lines for the third time. That's the mistake. Duplicated logic means duplicated bugs. A method bundles that logic under one name you can call anywhere. Write it once, call it three times, get the same result every time. The signature says it all: a name, what it takes in, what it hands back. If you're about to type the same logic twice, that's the method calling your name. Save this if you've got duplicated code sitting in your project right now.",
  "scene_text": [
    { "scene_reference": "Show duplicated calculation in three places", "text": "TYPED 3 TIMES" },
    { "scene_reference": "Extract into one method", "text": "ONE NAME, MANY CALLS" },
    { "scene_reference": "Label the method signature", "text": "name / params / return type" },
    { "scene_reference": "Takeaway", "text": "DON'T REPEAT YOURSELF" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "You just wrote the same three lines for the third time. That's the mistake.", "source_field": "hook", "estimated_duration_seconds": 5.6, "emphasis_words": ["third time", "mistake"] },
    { "order": 2, "text": "Duplicated logic means duplicated bugs.", "source_field": "intro_line", "estimated_duration_seconds": 2, "emphasis_words": [] },
    { "order": 3, "text": "A method bundles that logic under one name you can call anywhere.", "source_field": "main_script", "estimated_duration_seconds": 4.8, "emphasis_words": ["one name"] },
    { "order": 4, "text": "Write it once, call it three times, get the same result every time.", "source_field": "main_script", "estimated_duration_seconds": 5.2, "emphasis_words": ["once", "three times"] },
    { "order": 5, "text": "The signature says it all: a name, what it takes in, what it hands back.", "source_field": "code_narration", "estimated_duration_seconds": 6, "emphasis_words": ["signature"] },
    { "order": 6, "text": "If you're about to type the same logic twice, that's the method calling your name.", "source_field": "recap_line", "estimated_duration_seconds": 6, "emphasis_words": ["twice"] },
    { "order": 7, "text": "Save this if you've got duplicated code sitting in your project right now.", "source_field": "cta_line", "estimated_duration_seconds": 5.2, "emphasis_words": [] }
  ],
  "word_count": 87,
  "estimated_duration_seconds": 34.8,
  "tone": "direct-and-punchy",
  "hook_type": "beginner-mistake",
  "teaching_style": "code-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 45,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-08T12:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 4. Arrays — `java-arrays-basics` (revision)

Strategy: `hook_type: common-bug`, `teaching_style: question-first` (carried from `content-director-examples.md`). Note `confidence.overall_confidence: medium` — this script commits to the same cold-open-before-setup gamble the Content Director flagged, and the flag is carried through here rather than smoothed over.

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "variant_id": "primary",
  "hook": "This line just crashed the entire program.",
  "intro_line": "Five scores. Five separate variables. Already a mess.",
  "main_script": [
    { "beat": "the_fix", "spoken_line": "One array replaces all five, each score reachable by its position, starting at zero." },
    { "beat": "the_setup", "spoken_line": "But ask for a position that doesn't exist," }
  ],
  "code_narration": {
    "spoken_lines": ["and Java doesn't warn you, it crashes, right at that line."],
    "code_snippet": "int[] scores = {88, 92, 79, 95, 84};\nSystem.out.println(scores[5]); // crashes",
    "focus_line": "scores[5]"
  },
  "recap_line": "An array's size is fixed the moment it's created, know your bounds before you ask.",
  "cta_line": "Ever hit this exact crash? Tell me in the comments.",
  "full_spoken_script": "This line just crashed the entire program. Five scores. Five separate variables. Already a mess. One array replaces all five, each score reachable by its position, starting at zero. But ask for a position that doesn't exist, and Java doesn't warn you, it crashes, right at that line. An array's size is fixed the moment it's created, know your bounds before you ask. Ever hit this exact crash? Tell me in the comments.",
  "scene_text": [
    { "scene_reference": "Cold open — array index crash mid-program", "text": "CRASH" },
    { "scene_reference": "Rewind — five separate score variables, the problem", "text": "5 VARIABLES = MESS" },
    { "scene_reference": "Declare and fill the array, indices from 0", "text": "INDEX STARTS AT 0" },
    { "scene_reference": "Return to the crash with the locker analogy resolved", "text": "ArrayIndexOutOfBoundsException" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "This line just crashed the entire program.", "source_field": "hook", "estimated_duration_seconds": 2.8, "emphasis_words": ["crashed"] },
    { "order": 2, "text": "Five scores. Five separate variables. Already a mess.", "source_field": "intro_line", "estimated_duration_seconds": 3.2, "emphasis_words": [] },
    { "order": 3, "text": "One array replaces all five, each score reachable by its position, starting at zero.", "source_field": "main_script", "estimated_duration_seconds": 5.6, "emphasis_words": ["one array", "zero"] },
    { "order": 4, "text": "But ask for a position that doesn't exist,", "source_field": "main_script", "estimated_duration_seconds": 3.2, "emphasis_words": [] },
    { "order": 5, "text": "and Java doesn't warn you, it crashes, right at that line.", "source_field": "code_narration", "estimated_duration_seconds": 4.4, "emphasis_words": ["crashes"] },
    { "order": 6, "text": "An array's size is fixed the moment it's created, know your bounds before you ask.", "source_field": "recap_line", "estimated_duration_seconds": 6, "emphasis_words": ["fixed", "bounds"] },
    { "order": 7, "text": "Ever hit this exact crash? Tell me in the comments.", "source_field": "cta_line", "estimated_duration_seconds": 4, "emphasis_words": [] }
  ],
  "word_count": 73,
  "estimated_duration_seconds": 29.2,
  "tone": "direct-and-punchy",
  "hook_type": "common-bug",
  "teaching_style": "question-first",
  "confidence_notes": {
    "overall_confidence": "medium",
    "flagged_uncertainties": [
      "This revision leads with the crash before any setup, per the Content Director's cold-open strategy — a sharper break from the prior underperforming version than a pacing fix alone. Whether this improves retention can only be confirmed once posted and measured."
    ],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 45,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-16T13:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 5. Strings — `java-string-basics`

Strategy: `hook_type: myth`, `teaching_style: explain-first` (illustrative). Demonstrates the myth-hook pattern the lesson stage's `myth-busting` teaching_style called for.

```json
{
  "topic_id": "java-string-basics",
  "topic_title": "String Basics",
  "variant_id": "primary",
  "hook": "Two strings that look exactly the same. Java says they're not equal.",
  "intro_line": "That's not a bug, that's how Java strings actually work.",
  "main_script": [
    { "beat": "object_not_primitive", "spoken_line": "A String isn't a primitive, it's an object." },
    { "beat": "equals_vs_identity", "spoken_line": "Double-equals checks if it's the same object, not the same text." }
  ],
  "code_narration": {
    "spoken_lines": ["Use dot-equals instead, and it finally compares what's actually written."],
    "code_snippet": "String a = \"java\";\nString b = new String(\"java\");\na == b;       // false\na.equals(b);  // true",
    "focus_line": "a.equals(b);"
  },
  "recap_line": "Strings don't change in place either, every method hands you a brand new one.",
  "cta_line": "Follow for more Java gotchas that actually show up in real code.",
  "full_spoken_script": "Two strings that look exactly the same. Java says they're not equal. That's not a bug, that's how Java strings actually work. A String isn't a primitive, it's an object. Double-equals checks if it's the same object, not the same text. Use dot-equals instead, and it finally compares what's actually written. Strings don't change in place either, every method hands you a brand new one. Follow for more Java gotchas that actually show up in real code.",
  "scene_text": [
    { "scene_reference": "Two identical-looking Strings compared with ==", "text": "== vs .equals()" },
    { "scene_reference": "Introduce .equals() as the fix", "text": "SAME OBJECT ≠ SAME TEXT" },
    { "scene_reference": "toUpperCase() result ignored, then captured", "text": "STRINGS DON'T CHANGE" },
    { "scene_reference": "Takeaway", "text": "NEW STRING EVERY TIME" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "Two strings that look exactly the same. Java says they're not equal.", "source_field": "hook", "estimated_duration_seconds": 4.8, "emphasis_words": ["not equal"] },
    { "order": 2, "text": "That's not a bug, that's how Java strings actually work.", "source_field": "intro_line", "estimated_duration_seconds": 4, "emphasis_words": [] },
    { "order": 3, "text": "A String isn't a primitive, it's an object.", "source_field": "main_script", "estimated_duration_seconds": 3.2, "emphasis_words": ["object"] },
    { "order": 4, "text": "Double-equals checks if it's the same object, not the same text.", "source_field": "main_script", "estimated_duration_seconds": 4.4, "emphasis_words": ["same object", "same text"] },
    { "order": 5, "text": "Use dot-equals instead, and it finally compares what's actually written.", "source_field": "code_narration", "estimated_duration_seconds": 4, "emphasis_words": ["dot-equals"] },
    { "order": 6, "text": "Strings don't change in place either, every method hands you a brand new one.", "source_field": "recap_line", "estimated_duration_seconds": 5.6, "emphasis_words": ["brand new"] },
    { "order": 7, "text": "Follow for more Java gotchas that actually show up in real code.", "source_field": "cta_line", "estimated_duration_seconds": 4.8, "emphasis_words": [] }
  ],
  "word_count": 77,
  "estimated_duration_seconds": 30.8,
  "tone": "playful",
  "hook_type": "myth",
  "teaching_style": "explain-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 50,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-10T12:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 6. Classes and Objects — `java-classes-and-objects`

Strategy: `hook_type: question`, `teaching_style: analogy-first` (illustrative). Demonstrates the task's own worked example: a question hook opens with the question itself.

```json
{
  "topic_id": "java-classes-and-objects",
  "topic_title": "Classes and Objects",
  "variant_id": "primary",
  "hook": "What's the difference between a class and an object?",
  "intro_line": "Think of a class as a cookie cutter.",
  "main_script": [
    { "beat": "shape_vs_instance", "spoken_line": "The cutter defines the shape, but every cookie that comes out is its own cookie." },
    { "beat": "car_example", "spoken_line": "A Car class works the same way, one blueprint, endless cars." }
  ],
  "code_narration": {
    "spoken_lines": ["Three separate Car objects, same class, completely independent data."],
    "code_snippet": "class Car {\n    String color;\n    int speed;\n}\n\nCar car1 = new Car();\nCar car2 = new Car();",
    "focus_line": "new Car();"
  },
  "recap_line": "The class is the shape, the object is the thing, and you can build more than one.",
  "cta_line": "Next up, how constructors set up every one of those objects.",
  "full_spoken_script": "What's the difference between a class and an object? Think of a class as a cookie cutter. The cutter defines the shape, but every cookie that comes out is its own cookie. A Car class works the same way, one blueprint, endless cars. Three separate Car objects, same class, completely independent data. The class is the shape, the object is the thing, and you can build more than one. Next up, how constructors set up every one of those objects.",
  "scene_text": [
    { "scene_reference": "Cookie-cutter analogy", "text": "1 CUTTER, MANY COOKIES" },
    { "scene_reference": "Define the Car class", "text": "class Car { }" },
    { "scene_reference": "Create three Car objects", "text": "3 OBJECTS, 1 CLASS" },
    { "scene_reference": "Takeaway", "text": "SHAPE vs THING" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "What's the difference between a class and an object?", "source_field": "hook", "estimated_duration_seconds": 3.6, "emphasis_words": ["class", "object"] },
    { "order": 2, "text": "Think of a class as a cookie cutter.", "source_field": "intro_line", "estimated_duration_seconds": 3.2, "emphasis_words": [] },
    { "order": 3, "text": "The cutter defines the shape, but every cookie that comes out is its own cookie.", "source_field": "main_script", "estimated_duration_seconds": 6, "emphasis_words": ["shape", "own cookie"] },
    { "order": 4, "text": "A Car class works the same way, one blueprint, endless cars.", "source_field": "main_script", "estimated_duration_seconds": 4.4, "emphasis_words": ["blueprint"] },
    { "order": 5, "text": "Three separate Car objects, same class, completely independent data.", "source_field": "code_narration", "estimated_duration_seconds": 3.6, "emphasis_words": ["independent"] },
    { "order": 6, "text": "The class is the shape, the object is the thing, and you can build more than one.", "source_field": "recap_line", "estimated_duration_seconds": 6.8, "emphasis_words": ["shape", "thing"] },
    { "order": 7, "text": "Next up, how constructors set up every one of those objects.", "source_field": "cta_line", "estimated_duration_seconds": 4.4, "emphasis_words": [] }
  ],
  "word_count": 80,
  "estimated_duration_seconds": 32,
  "tone": "warm-encouraging",
  "hook_type": "question",
  "teaching_style": "analogy-first",
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [
      "Assumes java-static-vs-instance has already been taught, per the lesson blueprint's own note — this script doesn't re-explain it."
    ],
    "unresolved_conflicts": []
  },
  "target_duration_seconds": 50,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-16T13:30:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 7. StringBuilder — `java-stringbuilder`

Strategy: `hook_type: performance-comparison`, `teaching_style: visual-first` (carried from `content-director-examples.md`).

```json
{
  "topic_id": "java-stringbuilder",
  "topic_title": "StringBuilder",
  "variant_id": "primary",
  "hook": "One of these builds a string in milliseconds. The other takes seconds.",
  "intro_line": "Both do the exact same job.",
  "main_script": [
    { "beat": "the_slow_way", "spoken_line": "String plus-concatenation inside a loop creates a brand new string every single time." },
    { "beat": "the_fast_way", "spoken_line": "StringBuilder just keeps adding to the same one." }
  ],
  "code_narration": {
    "spoken_lines": ["Chain a few dot-append calls, and you build the whole thing without the overhead."],
    "code_snippet": "StringBuilder sb = new StringBuilder();\nsb.append(\"Hello\").append(\", \").append(\"world\");",
    "focus_line": "sb.append(\"Hello\").append(\", \").append(\"world\");"
  },
  "recap_line": "Same result, wildly different cost, StringBuilder wins every time inside a loop.",
  "cta_line": "Save this one, you'll want it the next time you're building strings in a loop.",
  "full_spoken_script": "One of these builds a string in milliseconds. The other takes seconds. Both do the exact same job. String plus-concatenation inside a loop creates a brand new string every single time. StringBuilder just keeps adding to the same one. Chain a few dot-append calls, and you build the whole thing without the overhead. Same result, wildly different cost, StringBuilder wins every time inside a loop. Save this one, you'll want it the next time you're building strings in a loop.",
  "scene_text": [
    { "scene_reference": "Hook — performance comparison teaser", "text": "MILLISECONDS vs SECONDS" },
    { "scene_reference": "Show string concatenation with + inside a loop", "text": "+ = NEW STRING EACH TIME" },
    { "scene_reference": "Split-screen race", "text": "StringBuilder WINS" },
    { "scene_reference": "Chained .append() calls and takeaway", "text": ".append().append().append()" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "One of these builds a string in milliseconds. The other takes seconds.", "source_field": "hook", "estimated_duration_seconds": 4.8, "emphasis_words": ["milliseconds", "seconds"] },
    { "order": 2, "text": "Both do the exact same job.", "source_field": "intro_line", "estimated_duration_seconds": 2.4, "emphasis_words": [] },
    { "order": 3, "text": "String plus-concatenation inside a loop creates a brand new string every single time.", "source_field": "main_script", "estimated_duration_seconds": 5.2, "emphasis_words": ["brand new", "every single time"] },
    { "order": 4, "text": "StringBuilder just keeps adding to the same one.", "source_field": "main_script", "estimated_duration_seconds": 3.2, "emphasis_words": ["same one"] },
    { "order": 5, "text": "Chain a few dot-append calls, and you build the whole thing without the overhead.", "source_field": "code_narration", "estimated_duration_seconds": 5.6, "emphasis_words": ["overhead"] },
    { "order": 6, "text": "Same result, wildly different cost, StringBuilder wins every time inside a loop.", "source_field": "recap_line", "estimated_duration_seconds": 4.8, "emphasis_words": ["wildly different"] },
    { "order": 7, "text": "Save this one, you'll want it the next time you're building strings in a loop.", "source_field": "cta_line", "estimated_duration_seconds": 6, "emphasis_words": [] }
  ],
  "word_count": 80,
  "estimated_duration_seconds": 32,
  "tone": "energetic",
  "hook_type": "performance-comparison",
  "teaching_style": "visual-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 40,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-18T10:00:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## 8. HashMap — `java-hashmap`

Strategy: `hook_type: interview-question`, `teaching_style: analogy-first` (carried from `content-director-examples.md`).

```json
{
  "topic_id": "java-hashmap",
  "topic_title": "HashMap",
  "variant_id": "primary",
  "hook": "This is one of the most asked questions in Java interviews.",
  "intro_line": "A HashMap is basically a coat check.",
  "main_script": [
    { "beat": "the_analogy", "spoken_line": "Hand over a key, get your value back, instantly, no matter how many are stored." },
    { "beat": "no_searching", "spoken_line": "No searching through everything one at a time." }
  ],
  "code_narration": {
    "spoken_lines": ["A five-line word counter shows exactly why that matters."],
    "code_snippet": "Map<String, Integer> counts = new HashMap<>();\ncounts.put(word, counts.getOrDefault(word, 0) + 1);",
    "focus_line": "counts.getOrDefault(word, 0) + 1"
  },
  "recap_line": "Key in, value out, instantly, that's the entire idea behind a HashMap.",
  "cta_line": "Follow along, more real interview-relevant Java is coming.",
  "full_spoken_script": "This is one of the most asked questions in Java interviews. A HashMap is basically a coat check. Hand over a key, get your value back, instantly, no matter how many are stored. No searching through everything one at a time. A five-line word counter shows exactly why that matters. Key in, value out, instantly, that's the entire idea behind a HashMap. Follow along, more real interview-relevant Java is coming.",
  "scene_text": [
    { "scene_reference": "Hook — framed as a classic interview question", "text": "ASKED IN EVERY INTERVIEW" },
    { "scene_reference": "Coat-check analogy", "text": "KEY IN → VALUE OUT" },
    { "scene_reference": "Minimal word-frequency example", "text": "getOrDefault(word, 0) + 1" },
    { "scene_reference": "Takeaway", "text": "INSTANT LOOKUP" }
  ],
  "subtitle_segments": [
    { "order": 1, "text": "This is one of the most asked questions in Java interviews.", "source_field": "hook", "estimated_duration_seconds": 4.4, "emphasis_words": ["interviews"] },
    { "order": 2, "text": "A HashMap is basically a coat check.", "source_field": "intro_line", "estimated_duration_seconds": 2.8, "emphasis_words": [] },
    { "order": 3, "text": "Hand over a key, get your value back, instantly, no matter how many are stored.", "source_field": "main_script", "estimated_duration_seconds": 6, "emphasis_words": ["instantly"] },
    { "order": 4, "text": "No searching through everything one at a time.", "source_field": "main_script", "estimated_duration_seconds": 3.2, "emphasis_words": ["no searching"] },
    { "order": 5, "text": "A five-line word counter shows exactly why that matters.", "source_field": "code_narration", "estimated_duration_seconds": 3.6, "emphasis_words": [] },
    { "order": 6, "text": "Key in, value out, instantly, that's the entire idea behind a HashMap.", "source_field": "recap_line", "estimated_duration_seconds": 4.8, "emphasis_words": ["instantly"] },
    { "order": 7, "text": "Follow along, more real interview-relevant Java is coming.", "source_field": "cta_line", "estimated_duration_seconds": 3.2, "emphasis_words": [] }
  ],
  "word_count": 70,
  "estimated_duration_seconds": 28,
  "tone": "calm-confident",
  "hook_type": "interview-question",
  "teaching_style": "analogy-first",
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "target_duration_seconds": 45,
  "audience_level": "beginner",
  "platform": "generic-vertical-short",
  "script_version": "1.0.0",
  "generated_at": "2026-07-18T10:20:00Z",
  "based_on_lesson_version": "1.0.0",
  "based_on_content_director_version": "1.0.0"
}
```

---

## Script Diversity at a Glance

| Topic | Hook Type | Teaching Style | Tone | Word Count | Est. Duration | Target |
| --- | --- | --- | --- | --- | --- | --- |
| Variables | hidden-feature | explain-first | calm-confident | 104 | 41.6s | 45s |
| Loops | before-vs-after | code-first | energetic | 92 | 36.8s | 40s |
| Methods | beginner-mistake | code-first | direct-and-punchy | 87 | 34.8s | 45s |
| Arrays (revision) | common-bug | question-first | direct-and-punchy | 73 | 29.2s | 45s |
| Strings | myth | explain-first | playful | 77 | 30.8s | 50s |
| Classes and Objects | question | analogy-first | warm-encouraging | 80 | 32s | 50s |
| StringBuilder | performance-comparison | visual-first | energetic | 80 | 32s | 40s |
| HashMap | interview-question | analogy-first | calm-confident | 70 | 28s | 45s |

Eight distinct hook types across eight examples, all five `teaching_style` values represented, and every `estimated_duration_seconds` lands comfortably at or under its `target_duration_seconds` — leaving room for natural pauses and transitions rather than cutting it exactly to the limit.
