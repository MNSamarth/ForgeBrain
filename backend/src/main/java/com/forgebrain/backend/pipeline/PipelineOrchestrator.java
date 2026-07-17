package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.models.TopicSelectionDecision;

/**
 * Contract for running one topic through the full pipeline in docs/PIPELINE.md, calling each
 * {@link com.forgebrain.backend.services} interface in sequence and accumulating results in a
 * {@link PipelineContext}.
 *
 * <p>{@link PipelineOrchestratorImpl} implements this for the stages this phase covers —
 * topic selection through storyboard (see {@code NEXT_EXECUTION.md}). What's still future
 * work: routing a {@code needs_revision} verdict back to an earlier stage per {@code
 * ReviewResult.suggestedStageToRevisit}, and everything downstream of storyboard (voice
 * through publishing), since the Reviewer stage those verdicts come from isn't implemented
 * yet. See TODO.md.
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

    /**
     * Runs a topic through every stage this phase implements — topic selection, research,
     * lesson, content strategy, script, and storyboard — saves the result, and returns it.
     * Internally this is {@link #startRun} followed by repeated {@link #advance} calls until
     * the context reaches a storyboard; added during implementation as the convenience entry
     * point the task's "single pipeline result object" and "one command" goals need, on top
     * of the original stage-by-stage contract above.
     */
    PipelineResult runFullPipeline();
}
