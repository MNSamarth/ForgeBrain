package com.forgebrain.backend.exceptions;

/**
 * Thrown when a referenced {@code topic_id} does not exist in the curriculum roadmap, or
 * when a topic is used in a way its current memory state does not permit (e.g. selected
 * while still within its {@code avoid_until} cooldown). See brain/topic-selector-spec.md
 * Section 8 for the conditions this corresponds to.
 */
public class InvalidTopicException extends RuntimeException {

    public InvalidTopicException(String message) {
        super(message);
    }

    public InvalidTopicException(String message, Throwable cause) {
        super(message, cause);
    }
}
