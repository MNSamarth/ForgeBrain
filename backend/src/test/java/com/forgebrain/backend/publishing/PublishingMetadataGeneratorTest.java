package com.forgebrain.backend.publishing;

import static com.forgebrain.backend.services.ReviewFixtures.lesson;
import static com.forgebrain.backend.services.ReviewFixtures.publishingConfig;
import static com.forgebrain.backend.services.ReviewFixtures.script;
import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.PublishingConfig;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.Script;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PublishingMetadataGenerator} — per this mission's Part 6 ("metadata
 * generation"). Deterministic and Spring-free.
 */
class PublishingMetadataGeneratorTest {

    private final PublishingMetadataGenerator generator = new PublishingMetadataGenerator(publishingConfig());

    @Test
    void titlePrefersTheScriptHook() {
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("What if every for-loop you wrote had a hidden bug?", "spoken", "recap",
                ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.title()).isEqualTo("What if every for-loop you wrote had a hidden bug?");
    }

    @Test
    void titleFallsBackToLessonRetentionHookWhenScriptHookIsBlank() {
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("", "spoken", "recap", ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.title()).isEqualTo("retention hook");
    }

    @Test
    void titleIsTruncatedToTheConfiguredMaxLength() {
        PublishingConfig shortTitleConfig = new PublishingConfig("1.0.0", java.util.List.of(), "Education", "en-US",
                10, 2000);
        PublishingMetadataGenerator shortTitleGenerator = new PublishingMetadataGenerator(shortTitleConfig);
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("This hook is definitely longer than ten characters", "spoken", "recap",
                ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = shortTitleGenerator.generate(lesson, script);

        assertThat(metadata.title()).hasSize(10);
        assertThat(metadata.title()).endsWith("…");
    }

    @Test
    void descriptionCombinesLessonObjectiveAndBeginnerTakeaway() {
        Lesson lesson = lesson(java.util.List.of(), "ints wrap around on overflow");
        Script script = script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.description()).contains("objective").contains("ints wrap around on overflow");
    }

    @Test
    void hashtagsIncludeTheConfiguredDefaultsPlusATopicSpecificOne() {
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.hashtags()).contains("#java", "#coding");
        assertThat(metadata.hashtags()).anyMatch(tag -> tag.equals("#topic1"));
    }

    @Test
    void tagsCarryLessonKeyPointsThrough() {
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.tags()).isEqualTo(lesson.keyPoints());
    }

    @Test
    void categoryAndLanguageComeFromConfig() {
        Lesson lesson = lesson(java.util.List.of(), "takeaway");
        Script script = script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH);

        PublishingMetadata metadata = generator.generate(lesson, script);

        assertThat(metadata.category()).isEqualTo("Education");
        assertThat(metadata.languageCode()).isEqualTo("en-US");
    }
}
