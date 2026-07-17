package com.forgebrain.backend.services;

import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import java.util.List;

/**
 * Contract for reading and updating persisted memory state. See memory/memory-spec.md.
 */
public interface MemoryService {

    /**
     * Loads the current memory state in full.
     */
    MemoryState loadCurrentState();

    /**
     * Returns the memory record for one topic, or {@code null} if the topic has never been
     * tracked (equivalent to {@code status: not_covered} per memory/README.md's "How it drives
     * topic selection" rule).
     */
    MemoryState.TopicRecord getTopicRecord(String topicId);

    /**
     * Persists an updated record for one topic (e.g. after a reel is posted or a topic is
     * flagged for revision).
     */
    void updateTopicRecord(String topicId, MemoryState.TopicRecord record);

    /**
     * Updates the global queue, replacing it in full.
     */
    void updateQueue(List<MemoryState.QueueEntry> queue);

    /**
     * Records a hook line as used, so a future revisit of this or a related topic can avoid
     * repeating it verbatim. See memory/memory-schema.json's {@code recently_used_hooks}.
     */
    void recordUsedHook(String topicId, String hookContent);

    /**
     * Records a code/teaching example as used, mirroring {@link #recordUsedHook} for
     * {@code recently_used_examples}.
     */
    void recordUsedExample(String topicId, String exampleContent);

    /**
     * Marks a topic as currently in progress: creates or updates its record with an
     * incremented {@code timesUsed} and a fresh {@code lastUsedAt}, and sets it as {@code
     * currentTopicId}. Called immediately after topic selection, before any content is
     * generated, so a repeated run can't select the same topic twice (see
     * brain/topic-selector-spec.md's not-in-progress gate).
     */
    void markTopicInProgress(String topicId, String title, Topic.Difficulty difficulty);
}
