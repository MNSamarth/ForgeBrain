package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;

/**
 * Contract for turning a finished script into a scene-by-scene, render-ready visual plan.
 * See brain/storyboard-spec.md.
 *
 * <p>Takes {@code contentStrategy} alongside the script because top-level storyboard fields
 * like {@code visualStyle}, {@code renderStyle}, and {@code pacingProfile.tier} are strategy
 * decisions the script itself only partially carries (it echoes {@code hookType}/{@code
 * teachingStyle}, not the full strategy) — see brain/storyboard-spec.md Section 6. This
 * signature was added during implementation; the original design left it implicit.
 */
public interface StoryboardService {

    Storyboard generateStoryboard(Script script, ContentStrategy contentStrategy);
}
