package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.models.TopicSelectionDecision;

/**
 * Contract for running one topic through the full pipeline in docs/PIPELINE.md, calling each
 * {@link com.forgebrain.backend.services} interface in sequence and accumulating results in a
 * {@link PipelineContext}.
 *
 * <p>No implementation exists in this phase — orchestration logic (retry behavior, how a
 * {@code needs_revision} verdict routes back to an earlier stage per {@code
 * ReviewResult.suggestedStageToRevisit}) is explicitly future work. See TODO.md.
 */
public interface PipelineOrchestrator {

    /**
     * Starts a new pipeline run for a freshly selected topic.
     */
    PipelineContext startRun(TopicSelectionDecision topicSelectionDecision);

    /**
     * Advances an in-progress run by one stage.
     */
    PipelineContext advance(PipelineContext context);

    /**
     * Returns the current context for a topic's run, or {@code null} if no run is tracked for
     * it.
     */
    PipelineContext getRun(String topicId);
}
