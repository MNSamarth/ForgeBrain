package com.forgebrain.backend.shared;

/**
 * The fourteen stages of the ForgeBrain content pipeline, in order, as defined in
 * docs/PIPELINE.md Section 1. Declared once in {@code shared} because it is referenced from
 * more than one layer: {@link com.forgebrain.backend.pipeline.PipelineOrchestrator} uses it to
 * sequence execution, and {@code ReviewResult.suggestedStageToRevisit}
 * (see {@link com.forgebrain.backend.models.ReviewResult}) uses the same enum to point a
 * failed review at a specific stage to redo — one canonical stage list, not two.
 */
public enum PipelineStage {
    CURRICULUM,
    MEMORY,
    TOPIC_SELECTION,
    RESEARCH,
    LESSON,
    CONTENT_DIRECTOR,
    SCRIPT,
    STORYBOARD,
    VOICE,
    SUBTITLES,
    ASSETS,
    RENDERER,
    REVIEWER,
    PUBLISHING_PACKAGE
}
