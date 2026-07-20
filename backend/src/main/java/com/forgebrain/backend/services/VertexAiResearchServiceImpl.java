package com.forgebrain.backend.services;

import com.forgebrain.backend.ai.AiGateway;
import com.forgebrain.backend.ai.AiGatewayResult;
import com.forgebrain.backend.ai.AiPromptExecution;
import com.forgebrain.backend.curriculum.CurriculumLoader;
import com.forgebrain.backend.exceptions.AiGatewayException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI Gateway-backed {@link ResearchService}: calls {@link AiGateway} for the fields that
 * genuinely benefit from generation ({@code topicSummary}, {@code coreConcepts}, {@code
 * simpleAnalogy}, {@code beginnerExplanation}, {@code advancedNotes}, {@code safetyNotes} — see
 * {@link ResearchPromptBuilder}), and assembles every other {@code ResearchResult} field
 * deterministically from curriculum data, exactly as {@link ResearchServiceImpl} does.
 *
 * <p>Falls back to {@link ResearchServiceImpl} whenever the AI Gateway can't produce a usable
 * result — see {@link AiGatewayException}. The fallback is a real, exercised code path, not a
 * stub: this is the only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiResearchServiceImpl implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiResearchServiceImpl.class);
    private static final String RESEARCH_VERSION = "1.0.0-vertex-ai";
    private static final String PROMPT_NAME = "research";

    private final AiGateway aiGateway;
    private final CurriculumLoader curriculumLoader;
    private final ResearchServiceImpl fallback;

    public VertexAiResearchServiceImpl(AiGateway aiGateway, CurriculumLoader curriculumLoader) {
        this.aiGateway = aiGateway;
        this.curriculumLoader = curriculumLoader;
        this.fallback = new ResearchServiceImpl(curriculumLoader);
    }

    @Override
    public ResearchResult research(String selectedTopicId, Topic curriculumContext, Topic.Difficulty audienceLevel,
            int targetReelLengthSeconds, MemoryState.TopicRecord topicMemory) {
        try {
            return researchViaAiGateway(selectedTopicId, curriculumContext, audienceLevel, targetReelLengthSeconds,
                    topicMemory);
        } catch (AiGatewayException e) {
            if (e.reason() == AiGatewayException.Reason.CONFIGURATION) {
                log.info("AI gateway unavailable for research ({}); falling back to heuristic research for topic"
                        + " '{}'.", e.getMessage(), selectedTopicId);
            } else {
                log.warn("AI gateway research call failed for topic '{}'; falling back to heuristic research.",
                        selectedTopicId, e);
            }
            aiGateway.recordFallbackUsed(PROMPT_NAME);
        }
        return fallback.research(selectedTopicId, curriculumContext, audienceLevel, targetReelLengthSeconds,
                topicMemory);
    }

    private ResearchResult researchViaAiGateway(String selectedTopicId, Topic curriculumContext,
            Topic.Difficulty audienceLevel, int targetReelLengthSeconds, MemoryState.TopicRecord topicMemory) {
        String promptText = ResearchPromptBuilder.build(curriculumContext, audienceLevel, targetReelLengthSeconds,
                topicMemory);
        AiGatewayResult<VertexResearchContent> result = aiGateway.execute(new AiPromptExecution<>(PROMPT_NAME,
                promptText, Map.of("topic_id", selectedTopicId), VertexResearchContent.class, this::validate));
        VertexResearchContent content = result.content();

        List<ResearchResult.TopicRef> prerequisites = curriculumContext.prerequisites().stream()
                .map(id -> new ResearchResult.TopicRef(id, resolveTitle(id)))
                .toList();

        return new ResearchResult(
                selectedTopicId,
                curriculumContext.title(),
                content.topicSummary(),
                curriculumContext.learningObjective(),
                curriculumContext.difficulty(),
                audienceLevel,
                targetReelLengthSeconds,
                prerequisites,
                curriculumContext.commonMistakes(),
                content.coreConcepts(),
                content.simpleAnalogy(),
                curriculumContext.exampleIdeas(),
                content.beginnerExplanation(),
                content.advancedNotes(),
                curriculumContext.nextTopics(),
                content.safetyNotes(),
                buildConfidenceNotes(topicMemory, result.modelId()),
                List.of(),
                RESEARCH_VERSION,
                Instant.now()
        );
    }

    private void validate(VertexResearchContent content) {
        if (content.topicSummary() == null || content.topicSummary().isBlank()
                || content.coreConcepts() == null || content.coreConcepts().isEmpty()
                || content.simpleAnalogy() == null || content.simpleAnalogy().isBlank()
                || content.beginnerExplanation() == null || content.beginnerExplanation().isBlank()
                || content.advancedNotes() == null
                || content.safetyNotes() == null) {
            throw new ContentGenerationException("research",
                    "AI gateway response is missing required research fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(MemoryState.TopicRecord topicMemory, String modelId) {
        List<String> flagged = new ArrayList<>();
        flagged.add("Generated by Vertex AI (" + modelId + ", via the AI Gateway's 'research' prompt) from"
                + " curriculum-grounded prompts, without independent external source verification — see"
                + " brain/research-spec.md Section 8.");
        if (topicMemory != null && topicMemory.status() == Topic.Status.NEEDS_REVISIT) {
            flagged.add("This is a revision. Prior attempt performance_score=" + topicMemory.performanceScore()
                    + (topicMemory.notes() != null ? ", noted issue: " + topicMemory.notes() : "")
                    + ". The lesson and script stages should visibly respond to this.");
        }
        return new ConfidenceNotes(ConfidenceLevel.MEDIUM, flagged, List.of());
    }

    private String resolveTitle(String topicId) {
        return curriculumLoader.findTopic(topicId).map(Topic::title).orElse(topicId);
    }
}
