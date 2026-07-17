package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * File-based {@link MemoryService}: the simplest durable approach for this phase (see
 * memory/README.md and {@code NEXT_EXECUTION.md}). Reads and writes one JSON file matching
 * {@code memory/memory-schema.json}, creating a fresh, empty state on first run.
 *
 * <p>The current state is cached in memory after first load and updated on every write, so
 * repeated reads within one process don't re-parse the file — reasonable for a single-process
 * local prototype, not intended to support concurrent writers across processes.
 */
@Component
public class MemoryServiceImpl implements MemoryService {

    private static final String MEMORY_VERSION = "1.0.0";
    private static final String LANGUAGE = "java";
    private static final int MAX_RECENT_FRAGMENTS = 20;

    private final ObjectMapper objectMapper;
    private final File stateFile;

    private MemoryState cachedState;

    public MemoryServiceImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.stateFile = new File(localStorageConfig.memoryStatePath());
    }

    @Override
    public synchronized MemoryState loadCurrentState() {
        if (cachedState == null) {
            cachedState = stateFile.exists() ? readFromDisk() : bootstrapFreshState();
        }
        return cachedState;
    }

    @Override
    public synchronized MemoryState.TopicRecord getTopicRecord(String topicId) {
        return loadCurrentState().topics().get(topicId);
    }

    @Override
    public synchronized void updateTopicRecord(String topicId, MemoryState.TopicRecord record) {
        MemoryState current = loadCurrentState();
        Map<String, MemoryState.TopicRecord> updatedTopics = new LinkedHashMap<>(current.topics());
        updatedTopics.put(topicId, record);
        persist(withTopics(current, updatedTopics));
    }

    @Override
    public synchronized void updateQueue(List<MemoryState.QueueEntry> queue) {
        persist(withQueue(loadCurrentState(), List.copyOf(queue)));
    }

    @Override
    public synchronized void recordUsedHook(String topicId, String hookContent) {
        MemoryState current = loadCurrentState();
        List<MemoryState.UsedContentFragment> updated = appendFragment(current.recentlyUsedHooks(), topicId, hookContent);
        persist(withRecentlyUsedHooks(current, updated));
    }

    @Override
    public synchronized void recordUsedExample(String topicId, String exampleContent) {
        MemoryState current = loadCurrentState();
        List<MemoryState.UsedContentFragment> updated = appendFragment(current.recentlyUsedExamples(), topicId, exampleContent);
        persist(withRecentlyUsedExamples(current, updated));
    }

    @Override
    public synchronized void markTopicInProgress(String topicId, String title, Topic.Difficulty difficulty) {
        MemoryState current = loadCurrentState();
        MemoryState.TopicRecord existing = current.topics().get(topicId);
        MemoryState.TopicRecord updated = new MemoryState.TopicRecord(
                topicId,
                title,
                Topic.Status.IN_PROGRESS,
                difficulty,
                existing == null ? 1 : existing.timesUsed() + 1,
                Instant.now(),
                existing == null ? null : existing.lastPostedAt(),
                existing == null ? 0 : existing.revisionCount(),
                existing == null ? null : existing.performanceScore(),
                existing == null ? null : existing.retentionScore(),
                existing == null ? null : existing.audienceResponse(),
                existing == null ? MemoryState.Priority.NORMAL : existing.priority(),
                null,
                existing == null ? List.of() : existing.relatedTopics(),
                existing == null ? null : existing.notes()
        );
        Map<String, MemoryState.TopicRecord> updatedTopics = new LinkedHashMap<>(current.topics());
        updatedTopics.put(topicId, updated);
        persist(withTopicsAndCurrent(current, updatedTopics, topicId));
    }

    private MemoryState bootstrapFreshState() {
        MemoryState fresh = new MemoryState(
                MEMORY_VERSION,
                LANGUAGE,
                Instant.now(),
                null,
                List.of(),
                List.of(),
                List.of(),
                new LinkedHashMap<>(),
                new MemoryState.GlobalStats(0, null, null)
        );
        persist(fresh);
        return fresh;
    }

    private MemoryState readFromDisk() {
        try {
            return objectMapper.readValue(stateFile, MemoryState.class);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read memory state from " + stateFile.getAbsolutePath(), e);
        }
    }

    private void persist(MemoryState state) {
        try {
            File parent = stateFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
            cachedState = state;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to persist memory state to " + stateFile.getAbsolutePath(), e);
        }
    }

    private static List<MemoryState.UsedContentFragment> appendFragment(
            List<MemoryState.UsedContentFragment> existing, String topicId, String content) {
        List<MemoryState.UsedContentFragment> updated = new ArrayList<>(existing);
        updated.add(new MemoryState.UsedContentFragment(topicId, content, Instant.now()));
        int overflow = updated.size() - MAX_RECENT_FRAGMENTS;
        if (overflow > 0) {
            updated = new ArrayList<>(updated.subList(overflow, updated.size()));
        }
        return updated;
    }

    private static MemoryState withTopics(MemoryState state, Map<String, MemoryState.TopicRecord> topics) {
        return new MemoryState(state.memoryVersion(), state.language(), Instant.now(), state.currentTopicId(),
                state.queue(), state.recentlyUsedHooks(), state.recentlyUsedExamples(), topics, state.globalStats());
    }

    private static MemoryState withTopicsAndCurrent(MemoryState state, Map<String, MemoryState.TopicRecord> topics, String currentTopicId) {
        return new MemoryState(state.memoryVersion(), state.language(), Instant.now(), currentTopicId,
                state.queue(), state.recentlyUsedHooks(), state.recentlyUsedExamples(), topics, state.globalStats());
    }

    private static MemoryState withQueue(MemoryState state, List<MemoryState.QueueEntry> queue) {
        return new MemoryState(state.memoryVersion(), state.language(), Instant.now(), state.currentTopicId(),
                queue, state.recentlyUsedHooks(), state.recentlyUsedExamples(), state.topics(), state.globalStats());
    }

    private static MemoryState withRecentlyUsedHooks(MemoryState state, List<MemoryState.UsedContentFragment> hooks) {
        return new MemoryState(state.memoryVersion(), state.language(), Instant.now(), state.currentTopicId(),
                state.queue(), hooks, state.recentlyUsedExamples(), state.topics(), state.globalStats());
    }

    private static MemoryState withRecentlyUsedExamples(MemoryState state, List<MemoryState.UsedContentFragment> examples) {
        return new MemoryState(state.memoryVersion(), state.language(), Instant.now(), state.currentTopicId(),
                state.queue(), state.recentlyUsedHooks(), examples, state.topics(), state.globalStats());
    }
}
