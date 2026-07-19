package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Vertex AI-backed {@link LessonService}: calls {@link VertexAiClient} to narrow a {@link
 * ResearchResult} into everything a lesson blueprint requires deciding ({@code lessonObjective},
 * {@code lessonSummary}, {@code keyPoints}, {@code stepByStepExplanation}, {@code coreExample},
 * {@code analogy}, {@code commonMistakes}, {@code whatToAvoidSaying}, {@code beginnerTakeaway},
 * {@code retentionHook}, {@code visualNotes}, {@code confidenceNotes} — see
 * {@link LessonPromptBuilder}), and assembles every other {@link Lesson} field deterministically
 * ({@code topicId}/{@code topicTitle}/{@code audienceLevel}/{@code targetDurationSeconds} carried
 * from the research brief, {@code teachingStyle} either the caller's request or the same
 * heuristic {@link LessonServiceImpl} uses, version/traceability fields set here), exactly as
 * {@link LessonServiceImpl} does for its heuristic fields.
 *
 * <p>Falls back to {@link LessonServiceImpl} whenever Vertex AI is unavailable — missing
 * {@code forgebrain.vertex-ai.project-id}/{@code lesson-model} configuration ({@link
 * ConfigurationException}), a failed API call, or a response that doesn't parse into {@link
 * VertexAiLessonContent}. The fallback is a real, exercised code path, not a stub: this is the
 * only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiLessonServiceImpl implements LessonService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiLessonServiceImpl.class);
    private static final String LESSON_VERSION = "1.0.0-vertex-ai";

    private final VertexAiClient vertexAiClient;
    private final VertexAiConfig vertexAiConfig;
    private final ObjectMapper objectMapper;
    private final LessonServiceImpl fallback;

    public VertexAiLessonServiceImpl(VertexAiClient vertexAiClient, VertexAiConfig vertexAiConfig,
            ObjectMapper objectMapper) {
        this.vertexAiClient = vertexAiClient;
        this.vertexAiConfig = vertexAiConfig;
        this.objectMapper = objectMapper;
        this.fallback = new LessonServiceImpl();
    }

    @Override
    public Lesson generateLesson(ResearchResult research, MemoryState.TopicRecord topicMemory,
            Lesson.TeachingStyle requestedStyle) {
        try {
            return generateLessonViaVertexAi(research, topicMemory, requestedStyle);
        } catch (ConfigurationException e) {
            log.info("Vertex AI lesson generation unavailable ({}); falling back to heuristic lesson for topic"
                    + " '{}'.", e.getMessage(), research.topicId());
        } catch (Exception e) {
            log.warn("Vertex AI lesson call failed for topic '{}'; falling back to heuristic lesson.",
                    research.topicId(), e);
        }
        return fallback.generateLesson(research, topicMemory, requestedStyle);
    }

    private Lesson generateLessonViaVertexAi(ResearchResult research, MemoryState.TopicRecord topicMemory,
            Lesson.TeachingStyle requestedStyle) throws Exception {
        String modelId = vertexAiConfig.lessonModel();
        if (modelId == null || modelId.isBlank()) {
            throw new ConfigurationException("forgebrain.vertex-ai.lesson-model is not configured");
        }

        Lesson.TeachingStyle teachingStyle = requestedStyle != null ? requestedStyle : deriveTeachingStyle(research);

        String promptText = LessonPromptBuilder.build(research, topicMemory, teachingStyle);
        VertexAiPromptRequest request = new VertexAiPromptRequest(modelId, promptText,
                Map.of("topic_id", research.topicId()),
                vertexAiConfig.lessonTemperature(), vertexAiConfig.lessonMaxOutputTokens(),
                vertexAiConfig.lessonResponseMimeType());
        VertexAiPromptResponse response = vertexAiClient.generate(request);

        VertexAiLessonContent content = objectMapper.readValue(response.rawText(), VertexAiLessonContent.class);
        validate(content);

        ConfidenceNotes confidenceNotes = buildConfidenceNotes(content, research, teachingStyle,
                requestedStyle == null);

        return new Lesson(
                research.topicId(),
                research.topicTitle(),
                content.lessonObjective(),
                content.lessonSummary(),
                content.keyPoints(),
                content.stepByStepExplanation(),
                content.coreExample(),
                content.analogy(),
                content.commonMistakes(),
                content.whatToAvoidSaying(),
                content.beginnerTakeaway(),
                content.retentionHook(),
                content.visualNotes(),
                confidenceNotes,
                research.audienceLevel(),
                research.targetReelLengthSeconds(),
                teachingStyle,
                LESSON_VERSION,
                Instant.now(),
                research.researchVersion()
        );
    }

    private void validate(VertexAiLessonContent content) {
        if (isBlank(content.lessonObjective())
                || isBlank(content.lessonSummary())
                || content.keyPoints() == null || content.keyPoints().isEmpty()
                || content.stepByStepExplanation() == null || content.stepByStepExplanation().isEmpty()
                || content.coreExample() == null
                || isBlank(content.coreExample().description())
                || isBlank(content.coreExample().codeSketch())
                || isBlank(content.coreExample().focusNote())
                || isBlank(content.analogy())
                || content.commonMistakes() == null || content.commonMistakes().isEmpty()
                || content.whatToAvoidSaying() == null
                || isBlank(content.beginnerTakeaway())
                || isBlank(content.retentionHook())
                || content.visualNotes() == null || content.visualNotes().isEmpty()
                || content.confidenceNotes() == null
                || content.confidenceNotes().overallConfidence() == null) {
            throw new ContentGenerationException("lesson",
                    "Vertex AI response is missing required lesson fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(VertexAiLessonContent content, ResearchResult research,
            Lesson.TeachingStyle teachingStyle, boolean styleWasAutoChosen) {
        List<String> flagged = new ArrayList<>(content.confidenceNotes().flaggedUncertainties() != null
                ? content.confidenceNotes().flaggedUncertainties()
                : List.of());
        flagged.add("Generated by Vertex AI (" + vertexAiConfig.lessonModel() + ") from research brief"
                + " research_version=" + research.researchVersion() + " — see brain/lesson-spec.md Section 9.");
        if (styleWasAutoChosen) {
            flagged.add("teaching_style (" + teachingStyle + ") was chosen automatically from whether the"
                    + " research brief listed common misconceptions, not requested explicitly.");
        }
        return new ConfidenceNotes(content.confidenceNotes().overallConfidence(), flagged, List.of());
    }

    private Lesson.TeachingStyle deriveTeachingStyle(ResearchResult research) {
        return research.commonMisconceptions().isEmpty()
                ? Lesson.TeachingStyle.DIRECT_EXPLANATION
                : Lesson.TeachingStyle.PROBLEM_FIRST;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
