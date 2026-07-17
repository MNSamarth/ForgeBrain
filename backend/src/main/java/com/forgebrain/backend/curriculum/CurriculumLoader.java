package com.forgebrain.backend.curriculum;

import com.forgebrain.backend.models.Topic;
import java.util.List;
import java.util.Optional;

/**
 * Contract for reading the Java curriculum roadmap. See curriculum/README.md.
 * {@link CurriculumLoaderImpl} implements this by parsing {@code curriculum/java-roadmap.json}
 * directly from the filesystem (see {@code NEXT_EXECUTION.md} for the known limitation this
 * implies for packaged deployments).
 */
public interface CurriculumLoader {

    List<RoadmapLevel> loadFullRoadmap();

    Optional<Topic> findTopic(String topicId);

    List<Topic> findPrerequisites(String topicId);
}
