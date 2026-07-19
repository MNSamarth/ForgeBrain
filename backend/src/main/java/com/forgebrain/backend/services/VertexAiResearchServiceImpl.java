package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.curriculum.CurriculumLoader;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
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
 * Vertex AI-backed {@link ResearchService}: calls {@link VertexAiClient} for the fields that
 * genuinely benefit from generation ({@code topicSummary}, {@code coreConcepts}, {@code
 * simpleAnalogy}, {@code beginnerExplanation}, {@code advancedNotes}, {@code safetyNotes} — see
 * {@link ResearchPromptBuilder}), and assembles every other {@code ResearchResult} field
 * deterministically from curriculum data, exactly as {@link ResearchServiceImpl} does.
 *
 * <p>Falls back to {@link ResearchServiceImpl} whenever Vertex AI is unavailable — missing
 * {@code forgebrain.vertex-ai.project-id}/{@code research-model} configuration ({@link
 * ConfigurationException}), a failed API call, or a response that doesn't parse into {@link
 * VertexResearchContent}. The fallback is a real, exercised code path, not a stub: this is the
 * only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiResearchServiceImpl implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiResearchServiceImpl.class);
    private static final String RESEARCH_VERSION = "1.0.0-vertex-ai";

    private final VertexAiClient vertexAiClient;
    private final VertexAiConfig vertexAiConfig;
    private final CurriculumLoader curriculumLoader;
    private final ObjectMapper objectMapper;
    private final ResearchServiceImpl fallback;

    public VertexAiResearchServiceImpl(VertexAiClient vertexAiClient, VertexAiConfig vertexAiConfig,
            CurriculumLoader curriculumLoader, ObjectMapper objectMapper) {
        this.vertexAiClient = vertexAiClient;
        this.vertexAiConfig = vertexAiConfig;
        this.curriculumLoader = curriculumLoader;
        this.objectMapper = objectMapper;
        this.fallback = new ResearchServiceImpl(curriculumLoader);
    }

    @Override
    public ResearchResult research(String selectedTopicId, Topic curriculumContext, Topic.Difficulty audienceLevel,
            int targetReelLengthSeconds, MemoryState.TopicRecord topicMemory) {
        try {
            return researchViaVertexAi(selectedTopicId, curriculumContext, audienceLevel, targetReelLengthSeconds,
                    topicMemory);
        } catch (ConfigurationException e) {
            log.info("Vertex AI research unavailable ({}); falling back to heuristic research for topic '{}'.",
                    e.getMessage(), selectedTopicId);
        } catch (Exception e) {
            log.warn("Vertex AI research call failed for topic '{}'; falling back to heuristic research.",
                    selectedTopicId, e);
        }
        return fallback.research(selectedTopicId, curriculumContext, audienceLevel, targetReelLengthSeconds,
                topicMemory);
    }

    private ResearchResult researchViaVertexAi(String selectedTopicId, Topic curriculumContext,
            Topic.Difficulty audienceLevel, int targetReelLengthSeconds, MemoryState.TopicRecord topicMemory)
            throws Exception {
        String modelId = vertexAiConfig.researchModel();
        if (modelId == null || modelId.isBlank()) {
            throw new ConfigurationException("forgebrain.vertex-ai.research-model is not configured");
        }

        String promptText = ResearchPromptBuilder.build(curriculumContext, audienceLevel, targetReelLengthSeconds,
                topicMemory);
        VertexAiPromptRequest request = new VertexAiPromptRequest(modelId, promptText,
                Map.of("topic_id", selectedTopicId));
        VertexAiPromptResponse response = vertexAiClient.generate(request);

        VertexResearchContent content = objectMapper.readValue(response.rawText(), VertexResearchContent.class);
        validate(content);

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
                buildConfidenceNotes(topicMemory),
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
                    "Vertex AI response is missing required research fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(MemoryState.TopicRecord topicMemory) {
        List<String> flagged = new ArrayList<>();
        flagged.add("Generated by Vertex AI (" + vertexAiConfig.researchModel() + ") from curriculum-grounded"
                + " prompts, without independent external source verification — see"
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
