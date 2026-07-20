package com.forgebrain.backend.pipeline;

/**
 * Contract for the full production path: {@code Topic Selection → Research → Lesson → Content
 * Director → Script → Storyboard → Voice → Subtitles → Assets → RenderPlan → Render → MP4}.
 * Callable from a test, a Spring-managed entry point, or {@link ReelExportCommandLineRunner}.
 * See backend/README.md's "End-to-End Reel Export" section.
 */
public interface ReelExportService {

    /**
     * Runs one topic all the way through to a rendered MP4, writing {@code reel.mp4}, {@code
     * metadata.json}, {@code subtitles.srt}, and {@code report.json} into one output folder.
     * Throws if any stage fails — see {@link ReelExportReport} (still written on failure) for
     * exactly where and why.
     */
    ReelExportResult exportReel();
}
