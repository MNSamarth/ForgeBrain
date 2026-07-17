package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.TopicSelectionDecision;
import com.forgebrain.backend.models.TopicSelectionDecision.Mode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the real gate-then-score algorithm against the actual curriculum, using
 * constructed memory states to exercise specific scenarios (fresh start, unmet prerequisites,
 * cooldowns, revision).
 */
class TopicSelectorImplTest {

    private TopicSelectorImpl selector;
    private static final Instant NOW = Instant.parse("2026-07-16T09:00:00Z");

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig("../curriculum/java-roadmap.json", "unused", "unused"));
        selector = new TopicSelectorImpl(curriculumLoader);
    }

    private MemoryState emptyMemory() {
        return new MemoryState("1.0.0", "java", NOW, null, List.of(), List.of(), List.of(), new LinkedHashMap<>(),
                new MemoryState.GlobalStats(0, null, null));
    }

    private MemoryState.TopicRecord posted(String id, String title, Double performanceScore) {
        return new MemoryState.TopicRecord(id, title, Topic.Status.POSTED, Topic.Difficulty.BEGINNER, 1,
                NOW, NOW, 0, performanceScore, 0.7, null, MemoryState.Priority.NORMAL, null, List.of(), null);
    }

    @Test
    void withFreshMemorySelectsTheVeryFirstCurriculumTopic() {
        TopicSelectionDecision decision = selector.selectNextTopic(Mode.NEXT_TOPIC, emptyMemory(), NOW);

        // java-what-is-java is the only topic in the entire 81-topic curriculum with no
        // prerequisites, so it is the sole topic ready on a completely empty memory.
        assertThat(decision.selectedTopicId()).isEqualTo("java-what-is-java");
        assertThat(decision.score()).isNotNull();
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    void advancesToTheNextTopicOnceItsPrerequisiteChainIsPosted() {
        Map<String, MemoryState.TopicRecord> topics = new LinkedHashMap<>();
        topics.put("java-what-is-java", posted("java-what-is-java", "What Is Java", 0.8));
        topics.put("java-jdk-jre-jvm", posted("java-jdk-jre-jvm", "JDK vs JRE vs JVM", 0.75));
        MemoryState memory = new MemoryState("1.0.0", "java", NOW, null, List.of(), List.of(), List.of(), topics,
                new MemoryState.GlobalStats(2, "java-jdk-jre-jvm", NOW));

        TopicSelectionDecision decision = selector.selectNextTopic(Mode.NEXT_TOPIC, memory, NOW);

        assertThat(decision.selectedTopicId()).isEqualTo("java-variables-and-data-types");
    }

    @Test
    void blocksATopicWhosePrerequisiteIsNotYetPosted() {
        // java-for-loop requires java-if-else, which is never posted here.
        TopicSelectionDecision decision = selector.selectNextTopic(Mode.NEXT_TOPIC, emptyMemory(), NOW);

        assertThat(decision.candidateTopics()).extracting(TopicSelectionDecision.ScoredCandidate::topicId)
                .doesNotContain("java-for-loop");
    }

    @Test
    void revisionModeReturnsNullSelectionWhenNoTopicNeedsRevision() {
        TopicSelectionDecision decision = selector.selectNextTopic(Mode.REVISION_TOPIC, emptyMemory(), NOW);

        assertThat(decision.selectedTopicId()).isNull();
        assertThat(decision.score()).isNull();
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    void revisionModeSelectsATopicPastItsCooldownButNotOneStillCoolingDown() {
        Map<String, MemoryState.TopicRecord> topics = new LinkedHashMap<>();
        topics.put("java-arrays-basics", new MemoryState.TopicRecord(
                "java-arrays-basics", "Arrays Basics", Topic.Status.NEEDS_REVISIT, Topic.Difficulty.BEGINNER, 1,
                NOW, NOW, 0, 0.41, 0.33, null, MemoryState.Priority.HIGH,
                LocalDate.parse("2026-07-01"), List.of(), "underperformed"));
        MemoryState memory = new MemoryState("1.0.0", "java", NOW, null, List.of(), List.of(), List.of(), topics,
                new MemoryState.GlobalStats(1, null, null));

        TopicSelectionDecision decision = selector.selectNextTopic(Mode.REVISION_TOPIC, memory, NOW);
        assertThat(decision.selectedTopicId()).isEqualTo("java-arrays-basics");
        assertThat(decision.needsRevision()).contains("java-arrays-basics");

        // Same topic, but still inside its cooldown window relative to "now".
        topics.put("java-arrays-basics", new MemoryState.TopicRecord(
                "java-arrays-basics", "Arrays Basics", Topic.Status.NEEDS_REVISIT, Topic.Difficulty.BEGINNER, 1,
                NOW, NOW, 0, 0.41, 0.33, null, MemoryState.Priority.HIGH,
                LocalDate.parse("2026-08-01"), List.of(), "underperformed"));
        MemoryState stillCoolingDown = new MemoryState("1.0.0", "java", NOW, null, List.of(), List.of(), List.of(), topics,
                new MemoryState.GlobalStats(1, null, null));

        TopicSelectionDecision blockedDecision = selector.selectNextTopic(Mode.REVISION_TOPIC, stillCoolingDown, NOW);
        assertThat(blockedDecision.selectedTopicId()).isNull();
        assertThat(blockedDecision.blockedByRecentUse()).contains("java-arrays-basics");
    }

    @Test
    void aTopicInProgressIsNeverReselected() {
        Map<String, MemoryState.TopicRecord> topics = new LinkedHashMap<>();
        topics.put("java-what-is-java", new MemoryState.TopicRecord(
                "java-what-is-java", "What Is Java", Topic.Status.IN_PROGRESS, Topic.Difficulty.BEGINNER, 1,
                NOW, null, 0, null, null, null, MemoryState.Priority.NORMAL, null, List.of(), null));
        MemoryState memory = new MemoryState("1.0.0", "java", NOW, "java-what-is-java", List.of(), List.of(), List.of(),
                topics, new MemoryState.GlobalStats(0, null, null));

        TopicSelectionDecision decision = selector.selectNextTopic(Mode.NEXT_TOPIC, memory, NOW);

        assertThat(decision.candidateTopics()).extracting(TopicSelectionDecision.ScoredCandidate::topicId)
                .doesNotContain("java-what-is-java");
        assertThat(decision.selectedTopicId()).isNotEqualTo("java-what-is-java");
    }
}
