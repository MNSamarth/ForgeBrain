package com.forgebrain.backend.services;

import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Topic;

/**
 * Contract for producing a lesson-ready topic brief. See brain/research-spec.md.
 */
public interface ResearchService {

    /**
     * @param selectedTopicId    the topic to research, from a {@code TopicSelectionDecision}
     * @param curriculumContext  the topic's full curriculum entry
     * @param audienceLevel      caller-supplied, defaults to curriculumContext.difficulty()
     *                           if not overridden
     * @param targetReelLengthSeconds bounds how many core_concepts/examples are reasonable
     * @param topicMemory        this topic's memory record, if any (null if never covered)
     */
    ResearchResult research(
            String selectedTopicId,
            Topic curriculumContext,
            Topic.Difficulty audienceLevel,
            int targetReelLengthSeconds,
            MemoryState.TopicRecord topicMemory
    );
}
