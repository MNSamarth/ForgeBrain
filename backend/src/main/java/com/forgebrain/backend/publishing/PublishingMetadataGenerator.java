package com.forgebrain.backend.publishing;

import com.forgebrain.backend.config.PublishingConfig;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.Script;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a default {@link PublishingMetadata} from a lesson and script, per
 * publishing/publishing-spec.md Section 6 — deterministic and dependency-free (a plain class, not
 * a {@code @Component}), mirroring {@code services.QualityScorer}: the title and description are
 * assembled from material already decided upstream, never independently invented.
 *
 * <ul>
 *     <li><b>Title</b> — {@code script.hook}, falling back to {@code lesson.retentionHook}, then
 *     {@code lesson.topicTitle}, truncated to {@link PublishingConfig#titleMaxLength()}.
 *     <li><b>Description</b> — {@code lesson.lessonObjective} followed by {@code
 *     lesson.beginnerTakeaway}, truncated to {@link PublishingConfig#descriptionMaxLength()}.
 *     <li><b>Hashtags</b> — {@link PublishingConfig#defaultHashtags()} plus one topic-derived tag.
 *     <li><b>Tags</b> — {@code lesson.keyPoints}, carried through as-is.
 * </ul>
 */
public class PublishingMetadataGenerator {

    private final PublishingConfig config;

    public PublishingMetadataGenerator(PublishingConfig config) {
        this.config = config;
    }

    public PublishingMetadata generate(Lesson lesson, Script script) {
        return new PublishingMetadata(
                buildTitle(lesson, script),
                buildDescription(lesson),
                lesson.keyPoints() == null ? List.of() : List.copyOf(lesson.keyPoints()),
                buildHashtags(lesson),
                config.defaultCategory(),
                config.defaultLanguageCode());
    }

    private String buildTitle(Lesson lesson, Script script) {
        String candidate = firstNonBlank(script.hook(), lesson.retentionHook(), lesson.topicTitle());
        return truncate(candidate, config.titleMaxLength());
    }

    private String buildDescription(Lesson lesson) {
        StringBuilder description = new StringBuilder();
        if (lesson.lessonObjective() != null && !lesson.lessonObjective().isBlank()) {
            description.append(lesson.lessonObjective().strip());
        }
        if (lesson.beginnerTakeaway() != null && !lesson.beginnerTakeaway().isBlank()) {
            if (!description.isEmpty()) {
                description.append(' ');
            }
            description.append(lesson.beginnerTakeaway().strip());
        }
        return truncate(description.toString(), config.descriptionMaxLength());
    }

    private List<String> buildHashtags(Lesson lesson) {
        List<String> hashtags = new ArrayList<>(config.defaultHashtags() == null ? List.of() : config.defaultHashtags());
        String topicHashtag = "#" + lesson.topicId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (!hashtags.contains(topicHashtag)) {
            hashtags.add(topicHashtag);
        }
        return List.copyOf(hashtags);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.strip();
            }
        }
        return "";
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)).stripTrailing() + "…";
    }
}
