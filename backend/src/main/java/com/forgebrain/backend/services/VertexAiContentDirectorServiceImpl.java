package com.forgebrain.backend.services;

import com.forgebrain.backend.ai.AiGateway;
import com.forgebrain.backend.ai.AiGatewayResult;
import com.forgebrain.backend.ai.AiPromptExecution;
import com.forgebrain.backend.exceptions.AiGatewayException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI Gateway-backed {@link ContentDirectorService}: calls {@link AiGateway} to make all seven
 * directorial decisions over a committed {@link Lesson} — hook, teaching posture, emotional goal,
 * pacing/scene_pacing, visual strategy, code framing, CTA (see {@link ContentDirectorPromptBuilder}
 * and brain/content-director-spec.md Section 5) — and assembles every other {@link
 * ContentStrategy} field deterministically ({@code topicId}/{@code topicTitle}/{@code
 * targetDurationSeconds} carried from the lesson, version/traceability fields set here), exactly
 * as {@link ContentDirectorServiceImpl} does for its heuristic fields.
 *
 * <p>Falls back to {@link ContentDirectorServiceImpl} whenever the AI Gateway can't produce a
 * usable result — see {@link AiGatewayException}. The fallback is a real, exercised code path,
 * not a stub: this is the only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiContentDirectorServiceImpl implements ContentDirectorService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiContentDirectorServiceImpl.class);
    private static final String CONTENT_DIRECTOR_VERSION = "1.0.0-vertex-ai";
    private static final String PROMPT_NAME = "content-director";

    private final AiGateway aiGateway;
    private final ContentDirectorServiceImpl fallback;

    public VertexAiContentDirectorServiceImpl(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
        this.fallback = new ContentDirectorServiceImpl();
    }

    @Override
    public ContentStrategy decideStrategy(Lesson lesson, MemoryState.TopicRecord topicMemory) {
        try {
            return decideStrategyViaAiGateway(lesson, topicMemory);
        } catch (AiGatewayException e) {
            if (e.reason() == AiGatewayException.Reason.CONFIGURATION) {
                log.info("AI gateway unavailable for content director generation ({}); falling back to heuristic"
                        + " strategy for topic '{}'.", e.getMessage(), lesson.topicId());
            } else {
                log.warn("AI gateway content director call failed for topic '{}'; falling back to heuristic"
                        + " strategy.", lesson.topicId(), e);
            }
            aiGateway.recordFallbackUsed(PROMPT_NAME);
        }
        return fallback.decideStrategy(lesson, topicMemory);
    }

    private ContentStrategy decideStrategyViaAiGateway(Lesson lesson, MemoryState.TopicRecord topicMemory) {
        String promptText = ContentDirectorPromptBuilder.build(lesson, topicMemory);
        AiGatewayResult<VertexAiContentStrategy> result = aiGateway.execute(new AiPromptExecution<>(PROMPT_NAME,
                promptText, Map.of("topic_id", lesson.topicId()), VertexAiContentStrategy.class, this::validate));
        VertexAiContentStrategy content = result.content();

        boolean isRevision = topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT;
        ConfidenceNotes confidenceNotes = buildConfidenceNotes(content, lesson, isRevision, result.modelId());

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
                    "AI gateway response is missing required content strategy fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(VertexAiContentStrategy content, Lesson lesson, boolean isRevision,
            String modelId) {
        List<String> flagged = new ArrayList<>(content.confidenceNotes().flaggedUncertainties() != null
                ? content.confidenceNotes().flaggedUncertainties()
                : List.of());
        flagged.add("Generated by Vertex AI (" + modelId + ", via the AI Gateway's 'content-director' prompt) from"
                + " lesson lesson_version=" + lesson.lessonVersion() + " — strategy_performance-informed"
                + " weighting is not yet available (see brain/content-director-spec.md Section 8).");
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
