package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.TopicSelectionDecision;
import java.time.Instant;

/**
 * The immutable, persistable outcome of one full run through the first executable pipeline
 * slice (topic selection through storyboard — see {@code NEXT_EXECUTION.md} for exactly which
 * stages this covers and which remain unimplemented). Distinct from {@link PipelineContext},
 * which is the mutable, in-progress accumulator a run is built up in; a {@code PipelineResult}
 * is the finished snapshot worth saving and inspecting afterward.
 */
public record PipelineResult(
        String topicId,
        String topicTitle,
        TopicSelectionDecision topicSelectionDecision,
        ResearchResult researchResult,
        Lesson lesson,
        ContentStrategy contentStrategy,
        Script script,
        Storyboard storyboard,
        Instant completedAt
) {

    /**
     * Builds a result snapshot from a completed context. Throws if the context hasn't reached
     * a storyboard yet — a partial run has no business being saved as a finished result.
     */
    public static PipelineResult fromContext(PipelineContext context) {
        if (context.storyboard() == null) {
            throw new IllegalStateException("Cannot build a PipelineResult from a context with no storyboard yet"
                    + " (current stage: " + context.currentStage() + ").");
        }
        return new PipelineResult(
                context.topicId(),
                context.storyboard().topicTitle(),
                context.topicSelectionDecision(),
                context.researchResult(),
                context.lesson(),
                context.contentStrategy(),
                context.script(),
                context.storyboard(),
                Instant.now()
        );
    }
}
