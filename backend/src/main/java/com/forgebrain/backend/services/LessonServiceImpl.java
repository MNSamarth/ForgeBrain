package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic implementation of {@link LessonService}: narrows a {@link ResearchResult} into one
 * single-concept {@link Lesson}, enforcing the "One of Everything" rule from
 * brain/lesson-spec.md Section 4 by construction — one {@code coreExample}, one
 * {@code analogy}, one {@code beginnerTakeaway}, one {@code retentionHook}.
 *
 * <p>Not a Spring bean: {@link VertexAiLessonServiceImpl} is the {@link LessonService} bean
 * wired into the pipeline and holds one instance of this class as its deterministic fallback,
 * used whenever Vertex AI is unavailable.
 */
public class LessonServiceImpl implements LessonService {

    private static final String LESSON_VERSION = "1.0.0-heuristic";
    private static final int MAX_COMMON_MISTAKES = 2;

    @Override
    public Lesson generateLesson(ResearchResult research, MemoryState.TopicRecord topicMemory, Lesson.TeachingStyle requestedStyle) {
        List<String> keyPoints = research.coreConcepts();
        Lesson.CoreExample coreExample = buildCoreExample(research);
        List<String> commonMistakes = research.commonMisconceptions().stream().limit(MAX_COMMON_MISTAKES).toList();
        String beginnerTakeaway = "The key idea: " + lowerFirst(research.learningObjective());
        String retentionHook = commonMistakes.isEmpty()
                ? research.topicSummary()
                : "Most beginners get this wrong: " + lowerFirst(commonMistakes.get(0));

        Lesson.TeachingStyle teachingStyle = requestedStyle != null ? requestedStyle : deriveTeachingStyle(research);
        ConfidenceNotes confidenceNotes = buildConfidenceNotes(research, teachingStyle, requestedStyle == null);

        return new Lesson(
                research.topicId(),
                research.topicTitle(),
                research.learningObjective(),
                buildLessonSummary(research),
                keyPoints,
                buildStepByStepExplanation(research, keyPoints),
                coreExample,
                research.simpleAnalogy(),
                commonMistakes,
                research.safetyNotes(),
                beginnerTakeaway,
                retentionHook,
                List.of(
                        "Highlight the core code line while narrating it.",
                        "Show the key term as on-screen text the moment it's first spoken."
                ),
                confidenceNotes,
                research.audienceLevel(),
                research.targetReelLengthSeconds(),
                teachingStyle,
                LESSON_VERSION,
                Instant.now(),
                research.researchVersion()
        );
    }

    private String buildLessonSummary(ResearchResult research) {
        return "This lesson teaches " + lowerFirst(research.topicTitle()) + " by walking through "
                + research.coreConcepts().size() + " key idea(s), grounded in one concrete example.";
    }

    private List<String> buildStepByStepExplanation(ResearchResult research, List<String> keyPoints) {
        List<String> steps = new ArrayList<>();
        if (!keyPoints.isEmpty()) {
            steps.add("Start with: " + keyPoints.get(0));
            for (int i = 1; i < keyPoints.size(); i++) {
                steps.add("Then: " + keyPoints.get(i));
            }
        }
        if (!research.codeExampleIdeas().isEmpty()) {
            steps.add("Show the example: " + research.codeExampleIdeas().get(0));
        }
        return steps;
    }

    private Lesson.CoreExample buildCoreExample(ResearchResult research) {
        String description = research.codeExampleIdeas().isEmpty()
                ? "A minimal example illustrating " + lowerFirst(research.topicTitle())
                : research.codeExampleIdeas().get(0);
        return new Lesson.CoreExample(
                description,
                "// Illustrates: " + description,
                "Highlight the core behavior described above while narrating."
        );
    }

    private Lesson.TeachingStyle deriveTeachingStyle(ResearchResult research) {
        return research.commonMisconceptions().isEmpty()
                ? Lesson.TeachingStyle.DIRECT_EXPLANATION
                : Lesson.TeachingStyle.PROBLEM_FIRST;
    }

    private ConfidenceNotes buildConfidenceNotes(ResearchResult research, Lesson.TeachingStyle teachingStyle, boolean styleWasAutoChosen) {
        List<String> flagged = new ArrayList<>(research.confidenceNotes().flaggedUncertainties());
        if (styleWasAutoChosen) {
            flagged.add("teaching_style (" + teachingStyle + ") was chosen automatically from whether the"
                    + " research brief listed common misconceptions, not requested explicitly.");
        }
        ConfidenceLevel level = research.confidenceNotes().overallConfidence() == ConfidenceLevel.LOW
                ? ConfidenceLevel.LOW
                : ConfidenceLevel.MEDIUM;
        return new ConfidenceNotes(level, flagged, List.of());
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
