package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Script.CodeNarration;
import com.forgebrain.backend.models.Script.SceneTextEntry;
import com.forgebrain.backend.models.Script.ScriptBeat;
import com.forgebrain.backend.models.Script.SubtitleSegment;
import com.forgebrain.backend.models.Script.SubtitleSegment.SourceField;
import com.forgebrain.backend.models.Script.Tone;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Template-based implementation of {@link ScriptService}. Deterministically turns a
 * {@link Lesson} and a {@link ContentStrategy} into spoken narration, following the binding
 * hook_type / teaching_style mapping in brain/script-spec.md Section 3. Enforces the same
 * invariant the spec requires of any implementation: {@code fullSpokenScript} is exactly the
 * concatenation of every structured field, {@code wordCount} is the real word count of that
 * string, and {@code estimatedDurationSeconds} follows the 2.5 words/second formula from
 * brain/script-spec.md Section 8 — nothing here is asserted, all three are computed.
 *
 * <p>Not a Spring bean: {@link VertexAiScriptServiceImpl} is the {@link ScriptService} bean and
 * constructs this directly as its fallback, exactly as {@link ResearchServiceImpl}, {@link
 * LessonServiceImpl}, and {@link ContentDirectorServiceImpl} are used by their respective Vertex
 * AI implementations.
 */
public class ScriptServiceImpl implements ScriptService {

    private static final double WORDS_PER_SECOND = 2.5;
    private static final String SCRIPT_VERSION = "1.0.0-heuristic";
    private static final int MAX_MAIN_BEATS = 3;
    private static final String[] ORDINALS = {"First", "Next", "Finally"};

    @Override
    public Script generateScript(Lesson lesson, ContentStrategy contentStrategy, Script.Platform platform) {
        String hook = buildHook(contentStrategy.hookType(), lesson);
        String introLine = "By the end of this, you'll understand " + lowerFirst(lesson.lessonObjective()) + ".";
        List<ScriptBeat> mainScript = buildMainScript(lesson);
        CodeNarration codeNarration = buildCodeNarration(lesson);
        String recapLine = lesson.beginnerTakeaway();
        String ctaLine = buildCtaLine(contentStrategy.ctaStyle(), lesson.topicTitle());

        List<String> orderedPieces = new ArrayList<>();
        orderedPieces.add(hook);
        orderedPieces.add(introLine);
        mainScript.forEach(beat -> orderedPieces.add(beat.spokenLine()));
        orderedPieces.addAll(codeNarration.spokenLines());
        orderedPieces.add(recapLine);
        orderedPieces.add(ctaLine);

        String fullSpokenScript = String.join(" ", orderedPieces);
        int wordCount = countWords(fullSpokenScript);
        double estimatedDurationSeconds = round(wordCount / WORDS_PER_SECOND);

        List<SubtitleSegment> subtitleSegments = buildSubtitleSegments(hook, introLine, mainScript, codeNarration, recapLine, ctaLine);
        List<SceneTextEntry> sceneText = buildSceneText(lesson);
        Tone tone = mapTone(contentStrategy.emotionalGoal());

        ConfidenceNotes confidenceNotes = new ConfidenceNotes(
                contentStrategy.confidenceNotes().overallConfidence(),
                List.of("Narration generated from fixed templates parameterized by lesson content, not an LLM —"
                        + " see brain/script-spec.md for the intended future Vertex AI-backed implementation."),
                List.of()
        );

        return new Script(
                lesson.topicId(),
                lesson.topicTitle(),
                "primary",
                hook,
                introLine,
                mainScript,
                codeNarration,
                recapLine,
                ctaLine,
                fullSpokenScript,
                sceneText,
                subtitleSegments,
                wordCount,
                estimatedDurationSeconds,
                tone,
                contentStrategy.hookType(),
                contentStrategy.teachingStyle(),
                confidenceNotes,
                lesson.targetDurationSeconds(),
                lesson.audienceLevel(),
                platform,
                SCRIPT_VERSION,
                Instant.now(),
                lesson.lessonVersion(),
                contentStrategy.contentDirectorVersion()
        );
    }

    private String buildHook(ContentStrategy.HookType hookType, Lesson lesson) {
        String title = lesson.topicTitle();
        return switch (hookType) {
            case BEGINNER_MISTAKE -> "Here's a mistake beginners make with " + title + ": "
                    + lowerFirst(lesson.commonMistakes().get(0)) + ".";
            case COMMON_BUG -> "Watch what happens when you get " + lowerFirst(title) + " wrong.";
            case MYTH -> "You've probably heard " + lowerFirst(title) + " works one way. It doesn't.";
            case CHALLENGE -> "Guess what this code does before you keep watching.";
            case INTERVIEW_QUESTION -> "This is one of the most asked questions about " + title + ".";
            case BEFORE_VS_AFTER -> "Here's " + title + ", before and after.";
            case HIDDEN_FEATURE -> "Java has a hidden feature most beginners miss about " + lowerFirst(title) + ".";
            case PRODUCTIVITY_TIP -> "This one habit with " + title + " will save you time.";
            case PERFORMANCE_COMPARISON -> "One approach here is much faster than the other.";
            case QUESTION -> "What is " + title + ", really?";
        };
    }

    private List<ScriptBeat> buildMainScript(Lesson lesson) {
        List<ScriptBeat> beats = new ArrayList<>();
        List<String> points = lesson.keyPoints().stream().limit(MAX_MAIN_BEATS).toList();
        for (int i = 0; i < points.size(); i++) {
            String ordinal = i < ORDINALS.length ? ORDINALS[i] : ORDINALS[ORDINALS.length - 1];
            beats.add(new ScriptBeat("point-" + (i + 1), ordinal + ", " + lowerFirst(points.get(i))));
        }
        return beats;
    }

    private CodeNarration buildCodeNarration(Lesson lesson) {
        String codeSketch = lesson.coreExample().codeSketch();
        String focusLine = codeSketch.lines().findFirst().orElse(codeSketch);
        return new CodeNarration(List.of(lesson.coreExample().focusNote()), codeSketch, focusLine);
    }

    private String buildCtaLine(ContentStrategy.CtaStyle ctaStyle, String topicTitle) {
        return switch (ctaStyle) {
            case FOLLOW -> "Follow for more Java, one idea at a time.";
            case SAVE -> "Save this if you want to come back to it.";
            case COMMENT -> "Let me know in the comments if this made sense.";
            case TRY_THIS_YOURSELF -> "Try this yourself in your own code.";
            case NEXT_LESSON_TEASER -> "More on " + topicTitle + " coming up next.";
        };
    }

    private Tone mapTone(ContentStrategy.EmotionalGoal emotionalGoal) {
        return switch (emotionalGoal) {
            case SURPRISE -> Tone.DIRECT_AND_PUNCHY;
            case RELIEF -> Tone.WARM_ENCOURAGING;
            case SATISFACTION -> Tone.ENERGETIC;
            case CURIOSITY, CONFIDENCE -> Tone.CALM_CONFIDENT;
        };
    }

    private List<SubtitleSegment> buildSubtitleSegments(String hook, String introLine, List<ScriptBeat> mainScript,
            CodeNarration codeNarration, String recapLine, String ctaLine) {
        List<SubtitleSegment> segments = new ArrayList<>();
        int order = 1;
        segments.add(subtitleSegment(order++, hook, SourceField.HOOK));
        segments.add(subtitleSegment(order++, introLine, SourceField.INTRO_LINE));
        for (ScriptBeat beat : mainScript) {
            segments.add(subtitleSegment(order++, beat.spokenLine(), SourceField.MAIN_SCRIPT));
        }
        for (String line : codeNarration.spokenLines()) {
            segments.add(subtitleSegment(order++, line, SourceField.CODE_NARRATION));
        }
        segments.add(subtitleSegment(order++, recapLine, SourceField.RECAP_LINE));
        segments.add(subtitleSegment(order, ctaLine, SourceField.CTA_LINE));
        return segments;
    }

    private SubtitleSegment subtitleSegment(int order, String text, SourceField sourceField) {
        return new SubtitleSegment(order, text, sourceField, round(countWords(text) / WORDS_PER_SECOND), List.of());
    }

    private List<SceneTextEntry> buildSceneText(Lesson lesson) {
        return List.of(
                new SceneTextEntry("hook", lesson.topicTitle().toUpperCase()),
                new SceneTextEntry("body", "KEY POINTS"),
                new SceneTextEntry("example", "CODE EXAMPLE"),
                new SceneTextEntry("recap", "TAKEAWAY")
        );
    }

    private static int countWords(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
