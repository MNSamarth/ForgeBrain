package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VisualPlan;

/**
 * Contract for the Visual Director stage: decides HOW each of a committed storyboard's scenes
 * should look — composition, camera motion, imagery, diagram/code framing — the same way {@link
 * ContentDirectorService} decides how a lesson should be taught. Runs after both {@link Script}
 * and {@link Storyboard} exist (it directs scenes whose content and timing are already final; it
 * never changes them), producing one {@link VisualPlan.VisualScenePlan} per storyboard scene plus
 * a reel-level thumbnail brief.
 */
public interface VisualDirectorService {

    VisualPlan generateVisualPlan(Script script, Storyboard storyboard);
}
