package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Scene.CodeBlock;
import com.forgebrain.backend.models.Scene.SceneType;
import com.forgebrain.backend.models.Scene.TimedSubtitleSegment;
import com.forgebrain.backend.models.Scene.TransitionStyle;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Script.SubtitleSegment;
import com.forgebrain.backend.models.Script.SubtitleSegment.SourceField;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Storyboard.EmphasisPoint;
import com.forgebrain.backend.models.Storyboard.PacingProfile;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic implementation of {@link StoryboardService}: groups the script's own
 * {@code subtitleSegments} (already correctly timed by {@link ScriptServiceImpl}) into scenes,
 * rather than recomputing timing independently. Each {@link SourceField} becomes its own
 * scene, except {@code MAIN_SCRIPT}, where every beat gets its own scene (one explanation
 * point per scene), and {@code CODE_NARRATION}, where consecutive lines are grouped into one
 * code-reveal scene. See brain/storyboard-spec.md Section 7 for the "first scene is always
 * hook, later scenes by content function" rule this follows.
 */
@Component
public class StoryboardServiceImpl implements StoryboardService {

    private static final String STORYBOARD_VERSION = "1.0.0-heuristic";

    @Override
    public Storyboard generateStoryboard(Script script, ContentStrategy contentStrategy) {
        List<List<SubtitleSegment>> groups = groupIntoScenes(script.subtitleSegments());

        List<Scene> scenes = new ArrayList<>();
        double cursor = 0.0;
        for (int i = 0; i < groups.size(); i++) {
            List<SubtitleSegment> group = groups.get(i);
            SourceField sourceField = group.get(0).sourceField();
            SceneType sceneType = i == 0 ? SceneType.HOOK : sceneTypeFor(sourceField);

            String voiceoverText = String.join(" ", group.stream().map(SubtitleSegment::text).toList());
            double duration = round(group.stream().mapToDouble(SubtitleSegment::estimatedDurationSeconds).sum());
            double startTime = round(cursor);
            double endTime = round(cursor + duration);
            cursor = endTime;

            String sceneId = "scene-" + (i + 1) + "-" + sceneType.name().toLowerCase().replace('_', '-');
            List<TimedSubtitleSegment> timedSegments = timeSegments(group, startTime);
            CodeBlock codeBlock = sceneType == SceneType.CODE_REVEAL ? buildCodeBlock(script) : null;

            scenes.add(new Scene(
                    sceneId,
                    startTime,
                    endTime,
                    duration,
                    sceneType,
                    voiceoverText,
                    onScreenTextFor(sceneType, script),
                    visualDescriptionFor(sceneType, contentStrategy),
                    codeBlock,
                    motionNotesFor(sceneType),
                    TransitionStyle.HARD_CUT,
                    TransitionStyle.HARD_CUT,
                    timedSegments,
                    List.of(),
                    purposeFor(sceneType)
            ));
        }

        double totalDuration = scenes.isEmpty() ? 0.0 : scenes.get(scenes.size() - 1).endTime();
        List<String> sceneOrder = scenes.stream().map(Scene::sceneId).toList();
        PacingProfile pacingProfile = buildPacingProfile(contentStrategy.pacing(), scenes, totalDuration);
        List<EmphasisPoint> emphasisPoints = buildEmphasisPoints(scenes);

        ConfidenceNotes confidenceNotes = new ConfidenceNotes(
                script.confidenceNotes().overallConfidence(),
                List.of("Scenes are a direct one-beat-per-scene mapping from the script, not yet informed by"
                        + " storytelling resequencing (e.g. cold-open framing) — see brain/storyboard-spec.md Section 7."),
                List.of()
        );

        return new Storyboard(
                script.topicId(),
                script.topicTitle(),
                totalDuration,
                scenes.size(),
                scenes,
                sceneOrder,
                contentStrategy.visualStyle(),
                Storyboard.AnimationStyle.SNAPPY_CUTS,
                Storyboard.SubtitleStyle.BOLD_CENTERED,
                mapCodeStyle(contentStrategy.codeStyle()),
                TransitionStyle.HARD_CUT,
                pacingProfile,
                emphasisPoints,
                confidenceNotes,
                script.platform(),
                Storyboard.AspectRatio.RATIO_9_16,
                Storyboard.RenderStyle.DARK_MODE_IDE,
                script.targetDurationSeconds(),
                STORYBOARD_VERSION,
                Instant.now(),
                script.scriptVersion()
        );
    }

    /**
     * Groups consecutive subtitle segments into scenes: MAIN_SCRIPT segments never group
     * (one beat = one scene); every other source field groups its consecutive run into one
     * scene.
     */
    private List<List<SubtitleSegment>> groupIntoScenes(List<SubtitleSegment> segments) {
        List<List<SubtitleSegment>> groups = new ArrayList<>();
        List<SubtitleSegment> current = null;
        SourceField previousField = null;

        for (SubtitleSegment segment : segments) {
            boolean startNewGroup = current == null
                    || segment.sourceField() == SourceField.MAIN_SCRIPT
                    || segment.sourceField() != previousField;
            if (startNewGroup) {
                current = new ArrayList<>();
                groups.add(current);
            }
            current.add(segment);
            previousField = segment.sourceField();
        }
        return groups;
    }

    private SceneType sceneTypeFor(SourceField sourceField) {
        return switch (sourceField) {
            case HOOK -> SceneType.HOOK;
            case INTRO_LINE -> SceneType.SETUP;
            case MAIN_SCRIPT -> SceneType.EXPLANATION;
            case CODE_NARRATION -> SceneType.CODE_REVEAL;
            case RECAP_LINE -> SceneType.RECAP;
            case CTA_LINE -> SceneType.CTA;
        };
    }

    private List<TimedSubtitleSegment> timeSegments(List<SubtitleSegment> group, double sceneStartTime) {
        List<TimedSubtitleSegment> timed = new ArrayList<>();
        double cursor = sceneStartTime;
        for (SubtitleSegment segment : group) {
            double start = round(cursor);
            double end = round(cursor + segment.estimatedDurationSeconds());
            timed.add(new TimedSubtitleSegment(segment.text(), start, end, segment.emphasisWords()));
            cursor = end;
        }
        return timed;
    }

    private CodeBlock buildCodeBlock(Script script) {
        return new CodeBlock(script.codeNarration().codeSnippet(), script.codeNarration().focusLine(), "java");
    }

    private List<String> onScreenTextFor(SceneType sceneType, Script script) {
        return switch (sceneType) {
            case HOOK -> List.of(script.topicTitle().toUpperCase());
            case CODE_REVEAL -> List.of("CODE EXAMPLE");
            case RECAP -> List.of("TAKEAWAY");
            default -> List.of();
        };
    }

    private String visualDescriptionFor(SceneType sceneType, ContentStrategy strategy) {
        return switch (sceneType) {
            case HOOK -> "Bold centered text delivers the hook line over the reel's brand background.";
            case SETUP -> "Text transitions to a smaller lower-third caption as the topic is framed.";
            case EXPLANATION -> "On-screen text reinforces the spoken point as it's narrated.";
            case CODE_REVEAL -> "Code panel displays the example with the focus line highlighted, styled per "
                    + strategy.codeStyle() + ".";
            case RECAP -> "Full-screen text carries the takeaway line.";
            case CTA -> "A simple call-to-action card animates in.";
            default -> "Standard on-screen presentation for this beat.";
        };
    }

    private String motionNotesFor(SceneType sceneType) {
        return switch (sceneType) {
            case HOOK -> "Kinetic type-on, settles centered, brief hold.";
            case CODE_REVEAL -> "Code panel fades in; focus line highlights as it's narrated.";
            default -> "Standard cut, no additional motion.";
        };
    }

    private String purposeFor(SceneType sceneType) {
        return switch (sceneType) {
            case HOOK -> "Earn attention in the first beat.";
            case SETUP -> "Bridge from the hook into the teaching body.";
            case EXPLANATION -> "Deliver one point of the lesson's key points.";
            case CODE_REVEAL -> "Ground the lesson in a concrete example.";
            case RECAP -> "Consolidate the lesson's one takeaway.";
            case CTA -> "Convert attention into an action.";
            default -> "Supports the surrounding narrative.";
        };
    }

    private Storyboard.CodeStyle mapCodeStyle(ContentStrategy.CodeStyle strategyCodeStyle) {
        return switch (strategyCodeStyle) {
            case BUG_EXAMPLE -> Storyboard.CodeStyle.STATIC_PANEL_WITH_HIGHLIGHT;
            case OPTIMIZATION_EXAMPLE, COMPARISON_EXAMPLE -> Storyboard.CodeStyle.BEFORE_AFTER_COMPARISON;
            case INTERVIEW_EXAMPLE -> Storyboard.CodeStyle.LINE_BY_LINE_REVEAL;
            case MINIMAL_EXAMPLE -> Storyboard.CodeStyle.TYPING_ANIMATION;
        };
    }

    private PacingProfile buildPacingProfile(ContentStrategy.Pacing pacing, List<Scene> scenes, double totalDuration) {
        double average = scenes.isEmpty() ? 0.0 : round(totalDuration / scenes.size());
        double shortest = scenes.stream().mapToDouble(Scene::duration).min().orElse(0.0);
        double longest = scenes.stream().mapToDouble(Scene::duration).max().orElse(0.0);
        return new PacingProfile(pacing, average, round(shortest), round(longest));
    }

    private List<EmphasisPoint> buildEmphasisPoints(List<Scene> scenes) {
        return scenes.stream()
                .filter(scene -> scene.sceneType() == SceneType.CODE_REVEAL)
                .findFirst()
                .map(scene -> List.of(new EmphasisPoint(scene.sceneId(), "The code example is shown and narrated.",
                        "This is the concrete grounding the lesson's retention hinges on.")))
                .orElse(List.of());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
