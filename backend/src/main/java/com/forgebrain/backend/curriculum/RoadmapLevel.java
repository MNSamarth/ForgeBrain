package com.forgebrain.backend.curriculum;

import com.forgebrain.backend.models.Topic;
import java.util.List;

/**
 * One level/stage of the Java curriculum (e.g. "Foundations", "Collections"), mirroring the
 * {@code levels[]} grouping in curriculum/java-roadmap.json.
 */
public record RoadmapLevel(String id, String title, int order, List<Topic> topics) {
}
