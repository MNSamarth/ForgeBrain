package com.forgebrain.backend.services;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;

/**
 * Contract for converting a lesson and a content strategy into actual spoken narration and
 * on-screen text. See brain/script-spec.md. Must follow the given {@link ContentStrategy} —
 * see Section 3 of that spec for the binding hook/teaching-style mapping.
 */
public interface ScriptService {

    /**
     * @param lesson          the committed content being scripted
     * @param contentStrategy the binding presentation strategy this script must follow
     * @param platform        target platform, affecting minor phrasing conventions only
     */
    Script generateScript(Lesson lesson, ContentStrategy contentStrategy, Script.Platform platform);
}
