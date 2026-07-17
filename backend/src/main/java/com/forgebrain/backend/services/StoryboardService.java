package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;

/**
 * Contract for turning a finished script into a scene-by-scene, render-ready visual plan.
 * See brain/storyboard-spec.md.
 */
public interface StoryboardService {

    Storyboard generateStoryboard(Script script);
}
