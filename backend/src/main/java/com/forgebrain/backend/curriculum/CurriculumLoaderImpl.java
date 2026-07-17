package com.forgebrain.backend.curriculum;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.models.Topic;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reads {@code curriculum/java-roadmap.json} into {@link Topic}/{@link RoadmapLevel} objects.
 * See curriculum/README.md for what the file contains and why topic selection depends on it.
 *
 * <p>The parsed roadmap is loaded once and cached for the lifetime of the bean — the file is
 * static curriculum data, not something that changes while the application is running.
 */
@Component
public class CurriculumLoaderImpl implements CurriculumLoader {

    private final ObjectMapper objectMapper;
    private final File roadmapFile;

    private List<RoadmapLevel> cachedLevels;
    private Map<String, Topic> cachedTopicsById;

    public CurriculumLoaderImpl(ObjectMapper objectMapper, LocalStorageConfig localStorageConfig) {
        this.objectMapper = objectMapper;
        this.roadmapFile = new File(localStorageConfig.curriculumRoadmapPath());
    }

    @Override
    public synchronized List<RoadmapLevel> loadFullRoadmap() {
        if (cachedLevels == null) {
            cachedLevels = readRoadmapFile();
            cachedTopicsById = indexTopicsById(cachedLevels);
            validateStructure(cachedLevels, cachedTopicsById);
        }
        return cachedLevels;
    }

    @Override
    public Optional<Topic> findTopic(String topicId) {
        loadFullRoadmap();
        return Optional.ofNullable(cachedTopicsById.get(topicId));
    }

    @Override
    public List<Topic> findPrerequisites(String topicId) {
        Topic topic = findTopic(topicId)
                .orElseThrow(() -> new ConfigurationException("Unknown topic_id, cannot resolve prerequisites: " + topicId));
        return topic.prerequisites().stream()
                .map(id -> findTopic(id).orElseThrow(() ->
                        new ConfigurationException("Topic '" + topicId + "' declares prerequisite '" + id + "' which does not exist in the roadmap")))
                .collect(Collectors.toList());
    }

    private List<RoadmapLevel> readRoadmapFile() {
        if (!roadmapFile.exists()) {
            throw new ConfigurationException(
                    "Curriculum roadmap not found at " + roadmapFile.getAbsolutePath()
                            + ". Set forgebrain.local-storage.curriculum-roadmap-path if running from a different working directory.");
        }
        try {
            RoadmapFile parsed = objectMapper.readValue(roadmapFile, RoadmapFile.class);
            if (parsed.levels() == null || parsed.levels().isEmpty()) {
                throw new ConfigurationException("Curriculum roadmap at " + roadmapFile.getAbsolutePath() + " contains no levels.");
            }
            return parsed.levels();
        } catch (IOException e) {
            throw new ConfigurationException("Failed to parse curriculum roadmap at " + roadmapFile.getAbsolutePath(), e);
        }
    }

    private static Map<String, Topic> indexTopicsById(List<RoadmapLevel> levels) {
        Map<String, Topic> byId = new LinkedHashMap<>();
        for (RoadmapLevel level : levels) {
            for (Topic topic : level.topics()) {
                if (byId.containsKey(topic.id())) {
                    throw new ConfigurationException("Duplicate topic_id in curriculum roadmap: " + topic.id());
                }
                byId.put(topic.id(), topic);
            }
        }
        return byId;
    }

    /**
     * Validates every prerequisite and next-topic reference resolves to a real topic in the
     * roadmap, catching a broken curriculum file at startup rather than at topic-selection
     * time deep in a pipeline run.
     */
    private static void validateStructure(List<RoadmapLevel> levels, Map<String, Topic> byId) {
        for (RoadmapLevel level : levels) {
            for (Topic topic : level.topics()) {
                for (String prerequisiteId : topic.prerequisites()) {
                    if (!byId.containsKey(prerequisiteId)) {
                        throw new ConfigurationException(
                                "Curriculum roadmap is invalid: topic '" + topic.id() + "' declares unknown prerequisite '" + prerequisiteId + "'");
                    }
                }
                for (String nextTopicId : topic.nextTopics()) {
                    if (!byId.containsKey(nextTopicId)) {
                        throw new ConfigurationException(
                                "Curriculum roadmap is invalid: topic '" + topic.id() + "' declares unknown next_topics entry '" + nextTopicId + "'");
                    }
                }
            }
        }
    }

    /**
     * Parsing-only shape for the roadmap file's top level. Only {@code levels} is exposed
     * through {@link CurriculumLoader} — the other top-level fields (language, version,
     * focus, audience, format, generated_at) are metadata this application doesn't currently
     * need to act on.
     */
    private record RoadmapFile(List<RoadmapLevel> levels) {
    }
}
