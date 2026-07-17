package com.forgebrain.backend.services;

import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;

/**
 * Contract for the pipeline's final quality gate. See reviewer/reviewer-spec.md. Combines
 * hard safety gates (drawn from {@code lesson.safetyNotes}/{@code script.whatToAvoidSaying})
 * with {@link QualityScore}'s scored judgment — the two are never averaged together (see
 * reviewer-spec.md Section 3).
 */
public interface ReviewerService {

    /**
     * Produces the six-dimension quality evaluation consumed by {@link #review}. Not a
     * decision on its own — see reviewer/quality-scoring-spec.md Section 1.
     */
    QualityScore scoreQuality(VideoPackage videoPackage, Lesson lesson, Script script);

    ReviewResult review(VideoPackage videoPackage, QualityScore qualityScore, Lesson lesson, Script script);
}
