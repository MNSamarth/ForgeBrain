package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.models.VoiceResult.SceneAudio;
import com.forgebrain.backend.models.VoiceResult.WordTiming;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubtitleServiceImplTest {

    private static final VoiceResult.VoiceProfile PROFILE =
            new VoiceResult.VoiceProfile("en-US-Neural2-C", "en-US", 1.0, 0.0);
    private static final double SCALE_FACTOR = 1.2;

    private final SubtitleServiceImpl subtitleService = new SubtitleServiceImpl();
    private Storyboard storyboard;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig(
                "../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        Script script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
    }

    @Test
    void usesProportionalEstimateAndScalesSegmentTimingWhenNoWordTimingsExist() {
        List<SceneAudio> scenes = storyboard.scenes().stream()
                .map(scene -> new SceneAudio(scene.sceneId(), "voiceover/" + storyboard.topicId(),
                        scene.duration(), round(scene.duration() * SCALE_FACTOR), 0.0, List.of()))
                .toList();
        VoiceResult voiceResult = voiceResult(scenes);

        SubtitleResult result = subtitleService.generateSubtitles(storyboard, voiceResult);

        assertThat(result.scenes()).allMatch(
                s -> s.reconciliationMethod() == SubtitleResult.SceneSubtitles.ReconciliationMethod.PROPORTIONAL_ESTIMATE);
        assertThat(result.totalDurationSeconds())
                .isCloseTo(round(storyboard.totalDurationSeconds() * SCALE_FACTOR), Offset.offset(0.5));

        // First scene starts at 0 in both timelines, so its segments scale directly.
        Scene firstScene = storyboard.scenes().get(0);
        SubtitleResult.SceneSubtitles firstReconciled = result.scenes().get(0);
        for (int i = 0; i < firstScene.subtitleSegments().size(); i++) {
            Scene.TimedSubtitleSegment original = firstScene.subtitleSegments().get(i);
            SubtitleResult.ReconciledSegment reconciled = firstReconciled.segments().get(i);
            assertThat(reconciled.startTime()).isCloseTo(original.startTime() * SCALE_FACTOR, Offset.offset(0.1));
            assertThat(reconciled.endTime()).isCloseTo(original.endTime() * SCALE_FACTOR, Offset.offset(0.1));
            assertThat(reconciled.text()).isEqualTo(original.text());
        }

        assertThat(result.confidenceNotes().flaggedUncertainties())
                .anyMatch(note -> note.contains("proportional-estimate fallback"));
    }

    @Test
    void usesWordAlignmentForScenesWithRealWordTimingsAndFallsBackForScenesWithout() {
        Scene hookScene = storyboard.scenes().get(0);
        List<WordTiming> wordTimings = buildWordTimingsFor(hookScene);
        double hookActualDuration = wordTimings.get(wordTimings.size() - 1).endTime();

        List<SceneAudio> scenes = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            if (scene.sceneId().equals(hookScene.sceneId())) {
                scenes.add(new SceneAudio(scene.sceneId(), "voiceover/" + storyboard.topicId(),
                        scene.duration(), hookActualDuration, round(hookActualDuration - scene.duration()), wordTimings));
            } else {
                scenes.add(new SceneAudio(scene.sceneId(), "voiceover/" + storyboard.topicId(),
                        scene.duration(), scene.duration(), 0.0, List.of()));
            }
        }
        VoiceResult voiceResult = voiceResult(scenes);

        SubtitleResult result = subtitleService.generateSubtitles(storyboard, voiceResult);

        SubtitleResult.SceneSubtitles hookReconciled = result.scenes().get(0);
        assertThat(hookReconciled.reconciliationMethod())
                .isEqualTo(SubtitleResult.SceneSubtitles.ReconciliationMethod.WORD_ALIGNMENT);

        // Hook scene starts at real time 0, so word-aligned segment times should match the word
        // timings directly.
        int wordCursor = 0;
        for (int i = 0; i < hookScene.subtitleSegments().size(); i++) {
            Scene.TimedSubtitleSegment original = hookScene.subtitleSegments().get(i);
            int wordCount = original.text().trim().split("\\s+").length;
            SubtitleResult.ReconciledSegment reconciled = hookReconciled.segments().get(i);
            assertThat(reconciled.startTime()).isEqualTo(wordTimings.get(wordCursor).startTime());
            assertThat(reconciled.endTime()).isEqualTo(wordTimings.get(wordCursor + wordCount - 1).endTime());
            wordCursor += wordCount;
        }

        boolean anyOtherSceneUsesFallback = result.scenes().stream()
                .filter(s -> !s.sceneId().equals(hookScene.sceneId()))
                .anyMatch(s -> s.reconciliationMethod()
                        == SubtitleResult.SceneSubtitles.ReconciliationMethod.PROPORTIONAL_ESTIMATE);
        assertThat(anyOtherSceneUsesFallback).isTrue();
    }

    private List<WordTiming> buildWordTimingsFor(Scene scene) {
        List<WordTiming> wordTimings = new ArrayList<>();
        double cursor = 0.0;
        for (Scene.TimedSubtitleSegment segment : scene.subtitleSegments()) {
            for (String word : segment.text().trim().split("\\s+")) {
                double end = cursor + 0.3;
                wordTimings.add(new WordTiming(word, round(cursor), round(end)));
                cursor = end;
            }
        }
        return wordTimings;
    }

    private VoiceResult voiceResult(List<SceneAudio> scenes) {
        double totalActual = scenes.stream().mapToDouble(SceneAudio::actualDurationSeconds).sum();
        return new VoiceResult(
                storyboard.topicId(), storyboard.topicTitle(), PROFILE, scenes,
                storyboard.totalDurationSeconds(), totalActual, round(totalActual - storyboard.totalDurationSeconds()),
                false, 2.0, VoiceResult.AudioFormat.AUDIO_WAV, 44100,
                new com.forgebrain.backend.shared.ConfidenceNotes(
                        com.forgebrain.backend.shared.ConfidenceLevel.MEDIUM, List.of(), List.of()),
                "1.0.0-test-fixture", java.time.Instant.now(), storyboard.storyboardVersion());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
