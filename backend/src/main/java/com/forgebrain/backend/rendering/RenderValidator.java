package com.forgebrain.backend.rendering;

import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue;
import com.forgebrain.backend.rendering.RenderValidationResult.ValidationIssue.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks a {@link RenderPlan} for internal consistency before anything downstream would act on
 * it — a real rendering engine has no reasonable way to recover from overlapping scenes or a
 * missing font, so these are checked here, once, rather than left for each future consumer to
 * discover independently. Every check runs and every issue found is collected; validation does
 * not stop at the first problem, so a caller sees the whole picture in one pass.
 */
@Component
public class RenderValidator {

    private static final double TIMING_TOLERANCE_SECONDS = 0.05;

    public RenderValidationResult validate(RenderPlan renderPlan) {
        List<ValidationIssue> issues = new ArrayList<>();

        validateDimensions(renderPlan, issues);
        validateFonts(renderPlan, issues);
        validateScenes(renderPlan, issues);
        validateSubtitles(renderPlan, issues);

        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        return new RenderValidationResult(valid, List.copyOf(issues));
    }

    private void validateDimensions(RenderPlan renderPlan, List<ValidationIssue> issues) {
        if (renderPlan.dimensions() == null
                || renderPlan.dimensions().width() <= 0 || renderPlan.dimensions().height() <= 0) {
            issues.add(error("INVALID_DIMENSIONS", "Video dimensions must be positive, got "
                    + renderPlan.dimensions() + ".", null));
        }
        if (renderPlan.fps() <= 0) {
            issues.add(error("INVALID_FPS", "fps must be positive, got " + renderPlan.fps() + ".", null));
        }
        if (renderPlan.totalDurationSeconds() <= 0) {
            issues.add(error("INVALID_DURATION", "totalDurationSeconds must be positive, got "
                    + renderPlan.totalDurationSeconds() + ".", null));
        }
    }

    private void validateFonts(RenderPlan renderPlan, List<ValidationIssue> issues) {
        if (renderPlan.fonts() == null) {
            issues.add(error("MISSING_ASSET", "No font set is present on the render plan.", null));
            return;
        }
        requireNonBlank(renderPlan.fonts().heading(), "heading", issues);
        requireNonBlank(renderPlan.fonts().body(), "body", issues);
        requireNonBlank(renderPlan.fonts().code(), "code", issues);
    }

    private void requireNonBlank(String fontRef, String role, List<ValidationIssue> issues) {
        if (fontRef == null || fontRef.isBlank()) {
            issues.add(error("MISSING_ASSET", "Font role '" + role + "' has no asset reference.", null));
        }
    }

    private void validateScenes(RenderPlan renderPlan, List<ValidationIssue> issues) {
        List<SceneRenderPlan> scenes = renderPlan.scenes();
        if (scenes == null || scenes.isEmpty()) {
            issues.add(error("NO_SCENES", "A render plan must have at least one scene.", null));
            return;
        }

        for (SceneRenderPlan scene : scenes) {
            validateSceneTiming(scene, issues);
            validateSceneAssets(scene, issues);
        }

        List<SceneRenderPlan> byStartTime = scenes.stream()
                .sorted(Comparator.comparingDouble(SceneRenderPlan::startTime))
                .toList();
        for (int i = 1; i < byStartTime.size(); i++) {
            SceneRenderPlan previous = byStartTime.get(i - 1);
            SceneRenderPlan current = byStartTime.get(i);
            if (current.startTime() < previous.endTime() - TIMING_TOLERANCE_SECONDS) {
                issues.add(error("OVERLAPPING_SCENES", "Scene '" + current.sceneId() + "' (starts "
                        + current.startTime() + "s) overlaps scene '" + previous.sceneId() + "' (ends "
                        + previous.endTime() + "s).", current.sceneId()));
            }
        }
    }

    private void validateSceneTiming(SceneRenderPlan scene, List<ValidationIssue> issues) {
        if (scene.startTime() < 0) {
            issues.add(error("INVALID_TIMING", "Scene '" + scene.sceneId() + "' has a negative startTime.",
                    scene.sceneId()));
        }
        if (scene.endTime() <= scene.startTime()) {
            issues.add(error("INVALID_TIMING", "Scene '" + scene.sceneId() + "' endTime ("
                    + scene.endTime() + ") is not after its startTime (" + scene.startTime() + ").",
                    scene.sceneId()));
        }
        double expectedDuration = scene.endTime() - scene.startTime();
        if (Math.abs(scene.duration() - expectedDuration) > TIMING_TOLERANCE_SECONDS) {
            issues.add(error("INVALID_TIMING", "Scene '" + scene.sceneId() + "' duration (" + scene.duration()
                    + ") does not match endTime - startTime (" + expectedDuration + ").", scene.sceneId()));
        }
    }

    private void validateSceneAssets(SceneRenderPlan scene, List<ValidationIssue> issues) {
        if (scene.codeLayer() != null && (scene.assetRefs() == null || scene.assetRefs().isEmpty())) {
            issues.add(error("MISSING_ASSET", "Scene '" + scene.sceneId()
                    + "' has a code layer but no asset references for it.", scene.sceneId()));
        }
    }

    private void validateSubtitles(RenderPlan renderPlan, List<ValidationIssue> issues) {
        if (renderPlan.subtitles() == null || renderPlan.subtitles().cues() == null
                || renderPlan.subtitles().cues().isEmpty()) {
            issues.add(warning("EMPTY_SUBTITLE_TIMELINE", "The render plan has no subtitle cues.", null));
            return;
        }
        for (SubtitleTimeline.SubtitleCue cue : renderPlan.subtitles().cues()) {
            if (cue.text() == null || cue.text().isBlank()) {
                issues.add(error("BLANK_SUBTITLE_TEXT", "Subtitle cue #" + cue.order() + " has blank text.",
                        cue.sceneId()));
            }
        }
    }

    private static ValidationIssue error(String code, String message, String sceneId) {
        return new ValidationIssue(Severity.ERROR, code, message, sceneId);
    }

    private static ValidationIssue warning(String code, String message, String sceneId) {
        return new ValidationIssue(Severity.WARNING, code, message, sceneId);
    }
}
