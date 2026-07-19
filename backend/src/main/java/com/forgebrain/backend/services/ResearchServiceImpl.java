package com.forgebrain.backend.services;

import com.forgebrain.backend.curriculum.CurriculumLoader;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic, curriculum-sourced implementation of {@link ResearchService} — see
 * brain/research-spec.md Section 6: Phase 1 has no external source-fetching pipeline, so this
 * distills the curriculum's own {@code learning_objective}, {@code common_mistakes}, and
 * {@code example_ideas} (already-curated, human-reviewed content — not invented) into a
 * lesson-ready brief, rather than calling an LLM. Every field this class cannot responsibly
 * generate from that source material (e.g. {@code advancedNotes}) is left empty rather than
 * fabricated.
 *
 * <p>Not a Spring bean: {@link VertexAiResearchServiceImpl} is the {@link ResearchService} bean
 * wired into the pipeline and holds one instance of this class as its deterministic fallback,
 * used whenever Vertex AI is unavailable.
 */
public class ResearchServiceImpl implements ResearchService {

    private static final String RESEARCH_VERSION = "1.0.0-heuristic";
    private static final int MAX_CORE_CONCEPTS = 4;
    private static final int MAX_SAFETY_NOTES = 2;

    private final CurriculumLoader curriculumLoader;

    public ResearchServiceImpl(CurriculumLoader curriculumLoader) {
        this.curriculumLoader = curriculumLoader;
    }

    @Override
    public ResearchResult research(String selectedTopicId, Topic curriculumContext, Topic.Difficulty audienceLevel,
            int targetReelLengthSeconds, MemoryState.TopicRecord topicMemory) {

        List<ResearchResult.TopicRef> prerequisites = curriculumContext.prerequisites().stream()
                .map(id -> new ResearchResult.TopicRef(id, resolveTitle(id)))
                .toList();

        String topicSummary = curriculumContext.title() + " covers " + lowerFirst(curriculumContext.learningObjective());
        List<String> coreConcepts = buildCoreConcepts(curriculumContext);
        String simpleAnalogy = "Think of " + lowerFirst(curriculumContext.title())
                + " as a new tool being added to your Java toolkit — once you see where it fits, using it becomes second nature.";
        String beginnerExplanation = buildBeginnerExplanation(curriculumContext);
        List<String> safetyNotes = buildSafetyNotes(curriculumContext);
        ConfidenceNotes confidenceNotes = buildConfidenceNotes(topicMemory);

        return new ResearchResult(
                selectedTopicId,
                curriculumContext.title(),
                topicSummary,
                curriculumContext.learningObjective(),
                curriculumContext.difficulty(),
                audienceLevel,
                targetReelLengthSeconds,
                prerequisites,
                curriculumContext.commonMistakes(),
                coreConcepts,
                simpleAnalogy,
                curriculumContext.exampleIdeas(),
                beginnerExplanation,
                List.of(),
                curriculumContext.nextTopics(),
                safetyNotes,
                confidenceNotes,
                List.of(),
                RESEARCH_VERSION,
                Instant.now()
        );
    }

    private List<String> buildCoreConcepts(Topic topic) {
        List<String> concepts = new ArrayList<>();
        concepts.add(topic.learningObjective());
        for (String exampleIdea : topic.exampleIdeas()) {
            if (concepts.size() >= MAX_CORE_CONCEPTS) {
                break;
            }
            concepts.add("Illustrate with: " + exampleIdea);
        }
        return concepts;
    }

    private String buildBeginnerExplanation(Topic topic) {
        StringBuilder explanation = new StringBuilder(topic.learningObjective());
        if (!topic.commonMistakes().isEmpty()) {
            explanation.append(" Watch out: ").append(lowerFirst(topic.commonMistakes().get(0))).append('.');
        }
        return explanation.toString();
    }

    private List<String> buildSafetyNotes(Topic topic) {
        List<String> notes = new ArrayList<>();
        for (String mistake : topic.commonMistakes()) {
            if (notes.size() >= MAX_SAFETY_NOTES) {
                break;
            }
            notes.add("Do not present '" + mistake + "' as correct or acceptable — this is a known"
                    + " misconception that must be corrected, not repeated.");
        }
        return notes;
    }

    private ConfidenceNotes buildConfidenceNotes(MemoryState.TopicRecord topicMemory) {
        List<String> flagged = new ArrayList<>();
        flagged.add("Generated heuristically from curriculum metadata (learning objective, common mistakes,"
                + " example ideas) without external source validation or LLM assistance — see"
                + " brain/research-spec.md Section 6 for the intended future sourcing model.");
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

    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
