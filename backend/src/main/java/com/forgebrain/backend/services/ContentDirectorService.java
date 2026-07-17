package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;

/**
 * Contract for deciding how a lesson should be taught — hook, pacing, tone, visual strategy —
 * without writing any dialogue. See brain/content-director-spec.md. Never returns script text;
 * see {@link ScriptService} for that.
 */
public interface ContentDirectorService {

    /**
     * @param lesson      the committed lesson this strategy presents
     * @param topicMemory this topic's memory record, if any — informs strategy changes on a
     *                    revision (see brain/content-director-spec.md Section 9)
     */
    ContentStrategy decideStrategy(Lesson lesson, MemoryState.TopicRecord topicMemory);
}
