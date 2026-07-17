package com.forgebrain.backend.models;

import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;

/**
 * A topic brief: the lesson-ready output of the research stage. Mirrors
 * {@code brain/research-output-schema.json}.
 *
 * @see <a href="../../../../../../../../brain/research-output-schema.json">brain/research-output-schema.json</a>
 */
public record ResearchResult(
        String topicId,
        String topicTitle,
        String topicSummary,
        String learningObjective,
        Topic.Difficulty difficulty,
        Topic.Difficulty audienceLevel,
        int targetReelLengthSeconds,
        List<TopicRef> prerequisites,
        List<String> commonMisconceptions,
        List<String> coreConcepts,
        String simpleAnalogy,
        List<String> codeExampleIdeas,
        String beginnerExplanation,
        List<String> advancedNotes,
        List<String> relatedTopics,
        List<String> safetyNotes,
        ConfidenceNotes confidenceNotes,
        List<Source> sources,
        String researchVersion,
        Instant generatedAt
) {

    public record TopicRef(String topicId, String title) {
    }

    public record Source(String name, String url, TrustTier trustTier, Instant retrievedAt) {
        public enum TrustTier {
            OFFICIAL, TRUSTED_REFERENCE, INTERNAL_VALIDATED, UNVERIFIED
        }
    }
}
