package com.forgebrain.backend.memory;

import com.forgebrain.backend.models.MemoryState;
import java.util.List;

/**
 * The six standard questions memory/README.md defines as direct field lookups, promoted to
 * named methods so callers (especially {@link com.forgebrain.backend.services.TopicSelector})
 * don't re-implement the same lookups independently. Each method name and its corresponding
 * memory/README.md table row:
 *
 * <ul>
 *   <li>{@link #mostRecentlyPosted} — "What topic was posted most recently?"</li>
 *   <li>{@link #mostOverusedTopics} — "What topics are overused?"</li>
 *   <li>{@link #topicsNeedingRevision} — "What topics need a revisit?"</li>
 *   <li>{@link #topPerformingTopics} — "What topics performed well?"</li>
 *   <li>{@link #topicsOnCooldown} — "What topics should be delayed?"</li>
 * </ul>
 *
 * "What topic should be taught next?" is deliberately not here — that is {@link
 * com.forgebrain.backend.services.TopicSelector}'s job, which uses these queries as inputs
 * rather than being one itself.
 */
public interface MemoryQueries {

    MemoryState.TopicRecord mostRecentlyPosted(MemoryState state);

    List<MemoryState.TopicRecord> mostOverusedTopics(MemoryState state, int limit);

    List<MemoryState.TopicRecord> topicsNeedingRevision(MemoryState state);

    List<MemoryState.TopicRecord> topPerformingTopics(MemoryState state, int limit);

    List<MemoryState.TopicRecord> topicsOnCooldown(MemoryState state);
}
