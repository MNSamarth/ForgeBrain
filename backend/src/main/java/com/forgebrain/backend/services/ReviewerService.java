package com.forgebrain.backend.services;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import java.util.List;

/**
 * Contract for the pipeline's final quality gate. See reviewer/reviewer-spec.md. Combines
 * hard safety gates (drawn from {@code lesson.whatToAvoidSaying}) with {@link QualityScore}'s
 * scored judgment — the two are never averaged together (see reviewer-spec.md Section 3).
 *
 * <p>The parameter lists below are broader than reviewer-spec.md's original Phase 1 sketch —
 * they now match what quality-scoring-spec.md Section 2's own Inputs table always described
 * ({@code storyboard}, {@code voice_result}, {@code subtitle_result}, {@code asset_manifest},
 * {@code content_director_output}), since scoring nine real dimensions needs real inputs, not
 * just a video package and a script.
 */
public interface ReviewerService {

    /**
     * Produces the nine-dimension quality evaluation consumed by {@link #review}. Not a
     * decision on its own — see reviewer/quality-scoring-spec.md Section 1.
     */
    QualityScore scoreQuality(VideoPackage videoPackage, Storyboard storyboard, VoiceResult voiceResult,
            SubtitleResult subtitleResult, AssetManifest assetManifest, Lesson lesson, Script script,
            ContentStrategy contentStrategy);

    /**
     * @param jobId           the {@link com.forgebrain.backend.job.ReelJob} being reviewed, or
     *                        {@code null} outside the job layer — see {@link
     *                        ReviewResult#jobId()}
     * @param subtitleFileUri the actual on-disk location the subtitle file was written to (e.g.
     *                        {@code <renderDirectory>/subtitles.srt}) — deliberately not {@code
     *                        SubtitleResult.subtitleFileUri()}, which Subtitle Generation leaves
     *                        {@code null} (that stage runs before a file exists; the renderer
     *                        writes it), so this is the one place a caller must supply the real
     *                        path for the missing-artifact hard gate to check
     */
    ReviewResult review(String jobId, VideoPackage videoPackage, QualityScore qualityScore, Lesson lesson,
            Script script, String subtitleFileUri);

    /**
     * Picks the best of several reviewed variants of the same topic (see reviewer-spec.md's
     * {@code variant_id} extensibility note and {@code Script.variantId}) — the one place a
     * future multi-variant pipeline would plug in a "pick a winner" decision without needing a
     * new abstraction. Prefers a better {@link ReviewResult.Verdict} (approved over
     * needs-revision over rejected) before comparing {@link ReviewResult#score()}.
     *
     * @throws IllegalArgumentException if {@code variants} is empty
     */
    ReviewResult selectBest(List<ReviewResult> variants);
}
