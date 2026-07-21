package com.forgebrain.backend.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue.Severity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderValidatorTest {

    private final RenderValidator validator = new RenderValidator();

    private SceneRenderPlan scene(String id, double start, double end, double duration,
            SceneRenderPlan.CodeLayer codeLayer, List<RenderPlan.GlobalAssetRef> assetRefs) {
        return new SceneRenderPlan(
                id, start, end, duration, Scene.SceneType.EXPLANATION,
                new SceneRenderPlan.BackgroundSpec("dark-mode-ide", "desc"),
                List.of(), codeLayer, "no motion", List.of(1), assetRefs,
                Scene.TransitionStyle.HARD_CUT, Scene.TransitionStyle.HARD_CUT);
    }

    private SubtitleTimeline subtitles(List<SubtitleTimeline.SubtitleCue> cues, double totalDuration) {
        return new SubtitleTimeline("topic", Storyboard.SubtitleStyle.BOLD_CENTERED, cues, totalDuration,
                "1.0.0-heuristic");
    }

    private RenderPlan plan(RenderPlan.VideoDimensions dimensions, int fps, double totalDuration,
            List<SceneRenderPlan> scenes, SubtitleTimeline subtitles) {
        return new RenderPlan(
                "topic", "Topic", dimensions, fps, totalDuration, scenes,
                new RenderPlan.FontSet("Inter-Bold", "Inter-Regular", "JetBrainsMono-Regular"), subtitles,
                new RenderPlan.AudioPlan("voiceover/topic", "music/lofi-focus", -18.0), List.of(), List.of(),
                Storyboard.RenderStyle.DARK_MODE_IDE, Storyboard.AspectRatio.RATIO_9_16, "1.0.0", Instant.now(),
                "1.0.0-heuristic", null);
    }

    private RenderPlan validBaselinePlan() {
        SceneRenderPlan sceneOne = scene("scene-1", 0, 5, 5, null, List.of());
        SceneRenderPlan sceneTwo = scene("scene-2", 5, 10, 5, null, List.of());
        SubtitleTimeline timeline = subtitles(List.of(
                new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 5, "Hello there.", List.of()),
                new SubtitleTimeline.SubtitleCue(2, "scene-2", 5, 10, "Goodbye now.", List.of())
        ), 10);
        return plan(new RenderPlan.VideoDimensions(1080, 1920), 30, 10, List.of(sceneOne, sceneTwo), timeline);
    }

    @Test
    void aWellFormedPlanHasNoIssuesAndIsValid() {
        RenderValidationResult result = validator.validate(validBaselinePlan());

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void noScenesProducesAnError() {
        RenderPlan plan = plan(new RenderPlan.VideoDimensions(1080, 1920), 30, 10, List.of(), subtitles(List.of(), 10));

        RenderValidationResult result = validator.validate(plan);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("NO_SCENES");
    }

    @Test
    void invalidDimensionsProducesAnError() {
        RenderPlan valid = validBaselinePlan();
        RenderPlan invalid = plan(new RenderPlan.VideoDimensions(0, 1920), valid.fps(), valid.totalDurationSeconds(),
                valid.scenes(), valid.subtitles());

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("INVALID_DIMENSIONS");
    }

    @Test
    void invalidFpsProducesAnError() {
        RenderPlan valid = validBaselinePlan();
        RenderPlan invalid = plan(valid.dimensions(), 0, valid.totalDurationSeconds(), valid.scenes(), valid.subtitles());

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("INVALID_FPS");
    }

    @Test
    void invalidDurationProducesAnError() {
        RenderPlan valid = validBaselinePlan();
        RenderPlan invalid = plan(valid.dimensions(), valid.fps(), 0, valid.scenes(), valid.subtitles());

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("INVALID_DURATION");
    }

    @Test
    void overlappingScenesProducesAnError() {
        SceneRenderPlan sceneOne = scene("scene-1", 0, 5, 5, null, List.of());
        SceneRenderPlan overlappingSceneTwo = scene("scene-2", 3, 8, 5, null, List.of());
        RenderPlan invalid = plan(new RenderPlan.VideoDimensions(1080, 1920), 30, 8,
                List.of(sceneOne, overlappingSceneTwo), subtitles(List.of(
                        new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 5, "Hi.", List.of())), 8));

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("OVERLAPPING_SCENES");
    }

    @Test
    void invalidTimingProducesAnErrorWhenDurationDoesNotMatchStartAndEnd() {
        SceneRenderPlan badTimingScene = scene("scene-1", 0, 5, 999, null, List.of());
        RenderPlan invalid = plan(new RenderPlan.VideoDimensions(1080, 1920), 30, 5,
                List.of(badTimingScene), subtitles(List.of(
                        new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 5, "Hi.", List.of())), 5));

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("INVALID_TIMING");
    }

    @Test
    void missingAssetsForACodeLayerProducesAnError() {
        SceneRenderPlan.CodeLayer codeLayer = new SceneRenderPlan.CodeLayer("int i = 0;", "int i = 0;", "java");
        SceneRenderPlan sceneWithoutAssets = scene("scene-1", 0, 5, 5, codeLayer, List.of());
        RenderPlan invalid = plan(new RenderPlan.VideoDimensions(1080, 1920), 30, 5,
                List.of(sceneWithoutAssets), subtitles(List.of(
                        new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 5, "Hi.", List.of())), 5));

        RenderValidationResult result = validator.validate(invalid);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("MISSING_ASSET");
    }

    @Test
    void emptySubtitleTimelineProducesAWarningButThePlanStaysValid() {
        RenderPlan valid = validBaselinePlan();
        RenderPlan withoutSubtitles = plan(valid.dimensions(), valid.fps(), valid.totalDurationSeconds(),
                valid.scenes(), subtitles(List.of(), valid.totalDurationSeconds()));

        RenderValidationResult result = validator.validate(withoutSubtitles);

        assertThat(result.valid()).isTrue();
        assertThat(codesOf(result)).contains("EMPTY_SUBTITLE_TIMELINE");
        assertThat(result.issues()).allMatch(issue -> issue.severity() == Severity.WARNING);
    }

    @Test
    void blankSubtitleTextProducesAnError() {
        RenderPlan valid = validBaselinePlan();
        RenderPlan withBlankCue = plan(valid.dimensions(), valid.fps(), valid.totalDurationSeconds(), valid.scenes(),
                subtitles(List.of(new SubtitleTimeline.SubtitleCue(1, "scene-1", 0, 5, "   ", List.of())),
                        valid.totalDurationSeconds()));

        RenderValidationResult result = validator.validate(withBlankCue);

        assertThat(result.valid()).isFalse();
        assertThat(codesOf(result)).contains("BLANK_SUBTITLE_TEXT");
    }

    private static List<String> codesOf(RenderValidationResult result) {
        return result.issues().stream().map(ValidationIssue::code).toList();
    }
}
