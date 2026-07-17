package com.forgebrain.backend.services;

import com.forgebrain.backend.models.MemoryState;
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
}
