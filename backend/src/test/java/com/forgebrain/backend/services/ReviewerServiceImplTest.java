package com.forgebrain.backend.services;

import static com.forgebrain.backend.services.ReviewFixtures.config;
import static com.forgebrain.backend.services.ReviewFixtures.lesson;
import static com.forgebrain.backend.services.ReviewFixtures.qualityScore;
import static com.forgebrain.backend.services.ReviewFixtures.script;
import static com.forgebrain.backend.services.ReviewFixtures.videoPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.ReviewResult.RecommendedAction;
import com.forgebrain.backend.models.ReviewResult.Verdict;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ReviewerServiceImpl#review}'s hard-gate/verdict/recommended-action logic
 * and {@link ReviewerServiceImpl#selectBest} — per this mission's Part 6 ("approval and rejection
 * outcomes", "pipeline integration boundary", "failure handling when required artifacts are
 * missing", "variant comparison"). No Spring context and no GCP credentials are used here; {@link
 * QualityScore} inputs are constructed directly via {@link ReviewFixtures#qualityScore} so this
 * class exercises the gate/verdict logic independently of {@link QualityScorerTest}.
 */
class ReviewerServiceImplTest {

    @TempDir
    Path tempDir;

    private final ReviewerServiceImpl reviewer = new ReviewerServiceImpl(config());

    @Test
    void approvesAReelWithNoHardGateViolationsAndScoresAboveEveryThreshold() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        VideoPackage videoPackage = videoPackage(video.toString(), null, 40.0, 12345, "1080x1920");
        QualityScore qualityScore = qualityScore(0.9);

        ReviewResult result = reviewer.review("job-1", videoPackage, qualityScore, lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH), subtitles.toString());

        assertThat(result.verdict()).isEqualTo(Verdict.APPROVED);
        assertThat(result.recommendedAction()).isEqualTo(RecommendedAction.APPROVE);
        assertThat(result.hardGateViolations()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void rejectsAReelWhoseScriptContainsAHardGateFlaggedStatement() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        VideoPackage videoPackage = videoPackage(video.toString(), null, 40.0, 12345, "1080x1920");
        Lesson lesson = lesson(List.of("int overflow throws an exception"), "takeaway");
        Script script = script("hook", "In Java, int overflow throws an exception if unchecked.", "recap",
                ContentStrategy.HookType.MYTH);

        ReviewResult result = reviewer.review("job-1", videoPackage, qualityScore(0.95), lesson, script,
                subtitles.toString());

        assertThat(result.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(result.recommendedAction()).isEqualTo(RecommendedAction.REJECT);
        assertThat(result.hardGateViolations()).containsExactly("int overflow throws an exception");
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void rejectsAReelWithAMissingVideoFile() {
        VideoPackage videoPackage = videoPackage(tempDir.resolve("does-not-exist.mp4").toString(), null, 40.0, 12345,
                "1080x1920");

        ReviewResult result = reviewer.review("job-1", videoPackage, qualityScore(0.95), lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH),
                tempDir.resolve("subtitles.srt").toString());

        assertThat(result.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(result.recommendedAction()).isEqualTo(RecommendedAction.REJECT);
        assertThat(result.hardGateViolations()).isEmpty();
        assertThat(result.errors()).anyMatch(e -> e.contains("Missing output artifact") && e.contains("video"));
    }

    @Test
    void rejectsAReelWithAMissingSubtitleFile() throws IOException {
        Path video = realFile("reel.mp4");

        ReviewResult result = reviewer.review("job-1", videoPackage(video.toString(), null, 40.0, 12345, "1080x1920"),
                qualityScore(0.95), lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH),
                tempDir.resolve("missing-subtitles.srt").toString());

        assertThat(result.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(result.errors()).anyMatch(e -> e.contains("Missing output artifact") && e.contains("subtitles"));
    }

    @Test
    void needsRevisionWhenOverallScoreIsBelowTheApprovalThresholdWithNoHardGate() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        // Every dimension individually clears the 0.5 floor, but the overall score (0.6) is
        // below the 0.7 approval threshold — this must be NEEDS_REVISION, not REJECTED.
        QualityScore qualityScore = qualityScore(0.6);

        ReviewResult result = reviewer.review("job-1", videoPackage(video.toString(), null, 40.0, 12345, "1080x1920"),
                qualityScore, lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH), subtitles.toString());

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVISION);
        assertThat(result.hardGateViolations()).isEmpty();
    }

    @Test
    void recommendsRegenerateSectionWhenExactlyOneDimensionIsBelowFloor() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        // hook_strength (-> CONTENT_DIRECTOR) is the only dimension below the 0.5 floor.
        QualityScore.Dimensions dimensions =
                new QualityScore.Dimensions(0.9, 0.9, 0.3, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        QualityScore qualityScore = qualityScore(dimensions, 0.75);

        ReviewResult result = reviewer.review("job-1", videoPackage(video.toString(), null, 40.0, 12345, "1080x1920"),
                qualityScore, lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH), subtitles.toString());

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVISION);
        assertThat(result.recommendedAction()).isEqualTo(RecommendedAction.REGENERATE_SECTION);
    }

    @Test
    void recommendsRegenerateFullWhenIssuesSpanMultipleStages() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        // hook_strength (-> CONTENT_DIRECTOR) and pacing_fit (-> VOICE) are both below floor.
        QualityScore.Dimensions dimensions =
                new QualityScore.Dimensions(0.9, 0.3, 0.3, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        QualityScore qualityScore = qualityScore(dimensions, 0.65);

        ReviewResult result = reviewer.review("job-1", videoPackage(video.toString(), null, 40.0, 12345, "1080x1920"),
                qualityScore, lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH), subtitles.toString());

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVISION);
        assertThat(result.recommendedAction()).isEqualTo(RecommendedAction.REGENERATE_FULL);
    }

    @Test
    void categoryScoresMirrorTheQualityScoreDimensionsExactly() throws IOException {
        Path video = realFile("reel.mp4");
        Path subtitles = realFile("subtitles.srt");
        QualityScore.Dimensions dimensions =
                new QualityScore.Dimensions(0.81, 0.72, 0.63, 0.94, 0.55, 0.86, 0.77, 0.68, 0.59);
        QualityScore qualityScore = qualityScore(dimensions, 0.73);

        ReviewResult result = reviewer.review("job-1", videoPackage(video.toString(), null, 40.0, 12345, "1080x1920"),
                qualityScore, lesson(List.of(), "takeaway"),
                script("hook", "spoken", "recap", ContentStrategy.HookType.MYTH), subtitles.toString());

        assertThat(result.categoryScores()).hasSize(9);
        assertThat(result.categoryScores().get("technical_accuracy")).isEqualTo(dimensions.technicalAccuracy());
        assertThat(result.categoryScores().get("hook_strength")).isEqualTo(dimensions.hookStrength());
        assertThat(result.categoryScores().get("retention_potential")).isEqualTo(dimensions.retentionPotential());
    }

    @Test
    void selectBestPrefersApprovedOverNeedsRevisionRegardlessOfScore() {
        ReviewResult approved = resultWithVerdict(Verdict.APPROVED, RecommendedAction.APPROVE, 0.71);
        ReviewResult needsRevision = resultWithVerdict(Verdict.NEEDS_REVISION, RecommendedAction.REGENERATE_FULL, 0.99);

        ReviewResult best = reviewer.selectBest(List.of(needsRevision, approved));

        assertThat(best).isSameAs(approved);
    }

    @Test
    void selectBestPrefersTheHigherScoreWithinTheSameVerdict() {
        ReviewResult lowerScoreApproved = resultWithVerdict(Verdict.APPROVED, RecommendedAction.APPROVE, 0.75);
        ReviewResult higherScoreApproved = resultWithVerdict(Verdict.APPROVED, RecommendedAction.APPROVE, 0.92);

        ReviewResult best = reviewer.selectBest(List.of(lowerScoreApproved, higherScoreApproved));

        assertThat(best).isSameAs(higherScoreApproved);
    }

    @Test
    void selectBestThrowsOnAnEmptyList() {
        assertThatThrownBy(() -> reviewer.selectBest(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private Path realFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "content");
        return file;
    }

    private ReviewResult resultWithVerdict(Verdict verdict, RecommendedAction action, double score) {
        return new ReviewResult("review-" + score, "job-1", "topic-1", "pkg-1", "score-1", verdict, score,
                java.util.Map.of(), List.of(), List.of(), List.of(), List.of(), action, "notes", null, "1.0.0",
                java.time.Instant.now());
    }
}
