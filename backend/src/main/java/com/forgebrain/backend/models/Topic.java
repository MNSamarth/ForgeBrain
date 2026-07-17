package com.forgebrain.backend.models;

import java.util.List;

/**
 * A single curriculum topic. Mirrors one entry in {@code curriculum/java-roadmap.json}.
 *
 * @see <a href="../../../../../../../../curriculum/java-roadmap.json">curriculum/java-roadmap.json</a>
 */
public record Topic(
        String id,
        String title,
        String level,
        Difficulty difficulty,
        List<String> prerequisites,
        List<String> nextTopics,
        String learningObjective,
        Status status
) {

    public enum Difficulty {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    public enum Status {
        NOT_COVERED, QUEUED, IN_PROGRESS, POSTED, NEEDS_REVISIT, AVOIDED
    }
}
