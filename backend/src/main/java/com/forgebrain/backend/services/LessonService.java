package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.ResearchResult;

/**
 * Contract for narrowing a research brief into one single-concept lesson blueprint. See
 * brain/lesson-spec.md, especially Section 4's "One of Everything" rule.
 */
public interface LessonService {

    /**
     * @param researchResult the research brief to narrow
     * @param topicMemory    this topic's memory record, if any — informs revision handling
     *                       (see brain/lesson-spec.md Section 9)
     * @param teachingStyle  desired teaching style, or {@code null} to let the service choose
     */
    Lesson generateLesson(
            ResearchResult researchResult,
            MemoryState.TopicRecord topicMemory,
            Lesson.TeachingStyle teachingStyle
    );
}
