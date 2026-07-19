package com.forgebrain.backend.rendering;

import com.forgebrain.backend.models.Storyboard;
import java.util.List;

/**
 * The reel's subtitles as one flat, chronologically-ordered timeline, independent of scene
 * boundaries — built by flattening every {@link com.forgebrain.backend.models.Scene#subtitleSegments()}
 * across a storyboard in order. Kept as its own type (rather than only living inside each {@link
 * SceneRenderPlan}) because subtitle rendering is a reel-wide concern (one continuous caption
 * track) even though each cue is produced by, and still traceable back to, a specific scene.
 *
 * <p>{@code style} is reel-wide rather than per-cue: {@link com.forgebrain.backend.models.Storyboard}
 * only ever carries one {@code subtitleStyle} for the whole reel (no per-scene override exists
 * upstream), so a per-cue style field would always just repeat this same value.
 */
public record SubtitleTimeline(
        String topicId,
        Storyboard.SubtitleStyle style,
        List<SubtitleCue> cues,
        double totalDurationSeconds,
        String basedOnStoryboardVersion
) {

    /**
     * @param order          sequential position across the whole reel, starting at 1
     * @param sceneId        the scene this cue was produced by, for traceability
     * @param emphasisWords  0-2 words in {@code text} to visually emphasize when rendered
     */
    public record SubtitleCue(
            int order,
            String sceneId,
            double startTime,
            double endTime,
            String text,
            List<String> emphasisWords
    ) {
    }
}
