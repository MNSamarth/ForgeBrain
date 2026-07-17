package com.forgebrain.backend.curriculum;

import com.forgebrain.backend.models.Topic;
import java.util.List;
import java.util.Optional;

/**
 * Contract for reading the Java curriculum roadmap. See curriculum/README.md. No
 * implementation exists in this phase — see TODO.md for the intended source (parsing
 * curriculum/java-roadmap.json) once this is built.
 */
public interface CurriculumLoader {

    List<RoadmapLevel> loadFullRoadmap();

    Optional<Topic> findTopic(String topicId);

    List<Topic> findPrerequisites(String topicId);
}
