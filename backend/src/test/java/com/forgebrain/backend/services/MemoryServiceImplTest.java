package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the file-based memory store actually persists across separate instances (simulating
 * separate application runs), not just within one in-memory object.
 */
class MemoryServiceImplTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private String stateFilePath;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        stateFilePath = new File(tempDir.toFile(), "memory-state.json").getAbsolutePath();
    }

    private MemoryServiceImpl newService() {
        return new MemoryServiceImpl(objectMapper, new LocalStorageConfig("unused", stateFilePath, "unused",
                "unused"));
    }

    @Test
    void bootstrapsAFreshEmptyStateWhenNoFileExists() {
        MemoryState state = newService().loadCurrentState();

        assertThat(state.language()).isEqualTo("java");
        assertThat(state.topics()).isEmpty();
        assertThat(state.currentTopicId()).isNull();
        assertThat(new File(stateFilePath)).exists();
    }

    @Test
    void topicRecordUpdatesPersistAcrossSeparateServiceInstances() {
        MemoryServiceImpl first = newService();
        MemoryState.TopicRecord record = new MemoryState.TopicRecord(
                "java-variables-and-data-types", "Variables and Data Types", Topic.Status.POSTED,
                Topic.Difficulty.BEGINNER, 1, java.time.Instant.now(), java.time.Instant.now(),
                0, 0.78, 0.71, null, MemoryState.Priority.NORMAL, null, List.of(), "went well"
        );
        first.updateTopicRecord("java-variables-and-data-types", record);

        MemoryServiceImpl second = newService();
        MemoryState.TopicRecord reloaded = second.getTopicRecord("java-variables-and-data-types");

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.status()).isEqualTo(Topic.Status.POSTED);
        assertThat(reloaded.performanceScore()).isEqualTo(0.78);
    }

    @Test
    void markTopicInProgressSetsCurrentTopicAndIncrementsTimesUsed() {
        MemoryServiceImpl service = newService();

        service.markTopicInProgress("java-operators", "Operators", Topic.Difficulty.BEGINNER);
        MemoryState afterFirst = service.loadCurrentState();
        assertThat(afterFirst.currentTopicId()).isEqualTo("java-operators");
        assertThat(afterFirst.topics().get("java-operators").timesUsed()).isEqualTo(1);
        assertThat(afterFirst.topics().get("java-operators").status()).isEqualTo(Topic.Status.IN_PROGRESS);

        service.markTopicInProgress("java-operators", "Operators", Topic.Difficulty.BEGINNER);
        assertThat(service.loadCurrentState().topics().get("java-operators").timesUsed()).isEqualTo(2);
    }

    @Test
    void recordsRecentHooksAndCapsAtTwentyEntries() {
        MemoryServiceImpl service = newService();

        for (int i = 0; i < 25; i++) {
            service.recordUsedHook("java-topic-" + i, "hook number " + i);
        }

        List<MemoryState.UsedContentFragment> hooks = service.loadCurrentState().recentlyUsedHooks();
        assertThat(hooks).hasSize(20);
        assertThat(hooks.get(hooks.size() - 1).content()).isEqualTo("hook number 24");
    }

    @Test
    void updateQueueReplacesTheFullQueue() {
        MemoryServiceImpl service = newService();
        service.updateQueue(List.of(
                new MemoryState.QueueEntry("java-operators", MemoryState.Priority.HIGH, java.time.Instant.now(), "next up")
        ));

        assertThat(service.loadCurrentState().queue()).hasSize(1);
        assertThat(service.loadCurrentState().queue().get(0).topicId()).isEqualTo("java-operators");
    }
}
