package com.forgebrain.backend.services;

import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.TopicSelectionDecision;
import java.time.Instant;

/**
 * Contract for deciding which curriculum topic gets produced next. See
 * brain/topic-selector-spec.md. Reconciles {@code curriculum/java-roadmap.json} (read
 * internally via {@link com.forgebrain.backend.curriculum.CurriculumLoader}) with the current
 * {@link MemoryState} to produce one decision — never a topic outside the curriculum.
 */
public interface TopicSelector {

    /**
     * @param mode              which of the four decision modes to run (see
     *                          brain/topic-selector-spec.md Section 4)
     * @param currentMemoryState the memory state to reconcile against
     * @param selectionTimestamp explicit "now" so decisions are reproducible
     */
    TopicSelectionDecision selectNextTopic(
            TopicSelectionDecision.Mode mode,
            MemoryState currentMemoryState,
            Instant selectionTimestamp
    );
}
