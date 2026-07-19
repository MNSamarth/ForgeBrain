package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
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
 * Vertex AI-backed {@link ContentDirectorService}: calls {@link VertexAiClient} to make all
 * seven directorial decisions over a committed {@link Lesson} — hook, teaching posture,
 * emotional goal, pacing/scene_pacing, visual strategy, code framing, CTA (see {@link
 * ContentDirectorPromptBuilder} and brain/content-director-spec.md Section 5) — and assembles
 * every other {@link ContentStrategy} field deterministically ({@code topicId}/{@code
 * topicTitle}/{@code targetDurationSeconds} carried from the lesson, version/traceability
 * fields set here), exactly as {@link ContentDirectorServiceImpl} does for its heuristic
 * fields.
 *
 * <p>Falls back to {@link ContentDirectorServiceImpl} whenever Vertex AI is unavailable —
 * missing {@code forgebrain.vertex-ai.project-id}/{@code content-director-model} configuration
 * ({@link ConfigurationException}), a failed API call, or a response that doesn't parse into
 * {@link VertexAiContentStrategy}. The fallback is a real, exercised code path, not a stub:
 * this is the only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiContentDirectorServiceImpl implements ContentDirectorService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiContentDirectorServiceImpl.class);
    private static final String CONTENT_DIRECTOR_VERSION = "1.0.0-vertex-ai";

    private final VertexAiClient vertexAiClient;
    private final VertexAiConfig vertexAiConfig;
    private final ObjectMapper objectMapper;
    private final ContentDirectorServiceImpl fallback;

    public VertexAiContentDirectorServiceImpl(VertexAiClient vertexAiClient, VertexAiConfig vertexAiConfig,
            ObjectMapper objectMapper) {
        this.vertexAiClient = vertexAiClient;
        this.vertexAiConfig = vertexAiConfig;
        this.objectMapper = objectMapper;
        this.fallback = new ContentDirectorServiceImpl();
    }

    @Override
    public ContentStrategy decideStrategy(Lesson lesson, MemoryState.TopicRecord topicMemory) {
        try {
            return decideStrategyViaVertexAi(lesson, topicMemory);
        } catch (ConfigurationException e) {
            log.info("Vertex AI content director generation unavailable ({}); falling back to heuristic"
                    + " strategy for topic '{}'.", e.getMessage(), lesson.topicId());
        } catch (Exception e) {
            log.warn("Vertex AI content director call failed for topic '{}'; falling back to heuristic"
                    + " strategy.", lesson.topicId(), e);
        }
        return fallback.decideStrategy(lesson, topicMemory);
    }

    private ContentStrategy decideStrategyViaVertexAi(Lesson lesson, MemoryState.TopicRecord topicMemory)
            throws Exception {
        String modelId = vertexAiConfig.contentDirectorModel();
        if (modelId == null || modelId.isBlank()) {
            throw new ConfigurationException("forgebrain.vertex-ai.content-director-model is not configured");
        }

        String promptText = ContentDirectorPromptBuilder.build(lesson, topicMemory);
        VertexAiPromptRequest request = new VertexAiPromptRequest(modelId, promptText,
                Map.of("topic_id", lesson.topicId()),
                vertexAiConfig.contentDirectorTemperature(), vertexAiConfig.contentDirectorMaxOutputTokens(),
                vertexAiConfig.contentDirectorResponseMimeType());
        VertexAiPromptResponse response = vertexAiClient.generate(request);

        VertexAiContentStrategy content = objectMapper.readValue(response.rawText(), VertexAiContentStrategy.class);
        validate(content);

        boolean isRevision = topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT;
        ConfidenceNotes confidenceNotes = buildConfidenceNotes(content, lesson, isRevision);

        return new ContentStrategy(
                lesson.topicId(),
                lesson.topicTitle(),
                content.hookType(),
                content.hookReason(),
                content.teachingStyle(),
                content.teachingStyleReason(),
                content.emotionalGoal(),
                content.emotionalGoalReason(),
                content.pacing(),
                content.pacingReason(),
                content.scenePacing(),
                content.visualStyle(),
                content.supportingVisuals(),
                content.visualStyleReason(),
                content.codeStyle(),
                content.codeStyleReason(),
                content.ctaStyle(),
                content.ctaReason(),
                content.retentionGoal(),
                content.estimatedWatchTime(),
                confidenceNotes,
                lesson.targetDurationSeconds(),
                CONTENT_DIRECTOR_VERSION,
                Instant.now(),
                lesson.lessonVersion()
        );
    }

    private void validate(VertexAiContentStrategy content) {
        if (content.hookType() == null || isBlank(content.hookReason())
                || content.teachingStyle() == null || isBlank(content.teachingStyleReason())
                || content.emotionalGoal() == null || isBlank(content.emotionalGoalReason())
                || content.pacing() == null || isBlank(content.pacingReason())
                || content.scenePacing() == null || content.scenePacing().isEmpty()
                || content.visualStyle() == null
                || content.supportingVisuals() == null
                || isBlank(content.visualStyleReason())
                || content.codeStyle() == null || isBlank(content.codeStyleReason())
                || content.ctaStyle() == null || isBlank(content.ctaReason())
                || isBlank(content.retentionGoal())
                || content.estimatedWatchTime() == null
                || content.confidenceNotes() == null
                || content.confidenceNotes().overallConfidence() == null) {
            throw new ContentGenerationException("content-director",
                    "Vertex AI response is missing required content strategy fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(VertexAiContentStrategy content, Lesson lesson,
            boolean isRevision) {
        List<String> flagged = new ArrayList<>(content.confidenceNotes().flaggedUncertainties() != null
                ? content.confidenceNotes().flaggedUncertainties()
                : List.of());
        flagged.add("Generated by Vertex AI (" + vertexAiConfig.contentDirectorModel() + ") from lesson"
                + " lesson_version=" + lesson.lessonVersion() + " — strategy_performance-informed weighting"
                + " is not yet available (see brain/content-director-spec.md Section 8).");
        if (isRevision) {
            flagged.add("This is a revision. Strategy was asked to visibly differ from the prior"
                    + " underperforming attempt rather than repeat it.");
        }
        return new ConfidenceNotes(content.confidenceNotes().overallConfidence(), flagged, List.of());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
