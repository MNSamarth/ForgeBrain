package com.forgebrain.backend.services;

import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.config.PublishingConfig;
import com.forgebrain.backend.config.ReviewerConfig;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal, valid fixtures for {@link QualityScorerTest}, {@link ReviewerServiceImplTest}, and
 * (being {@code public}) {@code com.forgebrain.backend.publishing}'s tests — every field a real
 * pipeline artifact requires, defaulted to an unremarkable value, with the handful of fields each
 * test actually varies exposed as parameters. Kept as one test-only helper rather than
 * duplicating this construction across test classes/packages.
 */
public final class ReviewFixtures {

    private ReviewFixtures() {
    }

    public static ConfidenceNotes confidence(ConfidenceLevel level) {
        return new ConfidenceNotes(level, List.of(), List.of());
    }

    public static Lesson lesson(List<String> whatToAvoidSaying, String beginnerTakeaway) {
        return new Lesson("topic-1", "The Topic", "objective", "summary", List.of("kp1"), List.of("step1"),
                new Lesson.CoreExample("desc", "code", "focus"), "analogy", List.of("mistake1"), whatToAvoidSaying,
                beginnerTakeaway, "retention hook", List.of("visual1"), confidence(ConfidenceLevel.HIGH),
                Topic.Difficulty.BEGINNER, 40, Lesson.TeachingStyle.DIRECT_EXPLANATION, "1.0.0", Instant.now(),
                "1.0.0");
    }

    public static Script script(String hook, String fullSpokenScript, String recapLine,
            ContentStrategy.HookType hookType) {
        return new Script("topic-1", "The Topic", "variant-1", hook, "intro", List.of(),
                new Script.CodeNarration(List.of(), "code", "focus"), recapLine, "cta", fullSpokenScript, List.of(),
                List.of(), 100, 40.0, Script.Tone.CALM_CONFIDENT, hookType, ContentStrategy.TeachingStyle.EXPLAIN_FIRST,
                confidence(ConfidenceLevel.HIGH), 40, Topic.Difficulty.BEGINNER, Script.Platform.YOUTUBE_SHORTS,
                "1.0.0", Instant.now(), "1.0.0", "1.0.0");
    }

    static Storyboard storyboard(double totalDurationSeconds, Storyboard.RenderStyle renderStyle) {
        return new Storyboard("topic-1", "The Topic", totalDurationSeconds, 0, List.of(), List.of(),
                ContentStrategy.VisualStyle.CODE_ANIMATION, Storyboard.AnimationStyle.SNAPPY_CUTS,
                Storyboard.SubtitleStyle.BOLD_CENTERED, Storyboard.CodeStyle.TYPING_ANIMATION,
                com.forgebrain.backend.models.Scene.TransitionStyle.HARD_CUT,
                new Storyboard.PacingProfile(ContentStrategy.Pacing.MEDIUM, 5.0, 3.0, 8.0), List.of(),
                confidence(ConfidenceLevel.HIGH), Script.Platform.YOUTUBE_SHORTS, Storyboard.AspectRatio.RATIO_9_16,
                renderStyle, 40, "1.0.0", Instant.now(), "1.0.0");
    }

    static VoiceResult voiceResult(double driftSeconds, double thresholdSeconds, boolean exceedsThreshold) {
        return new VoiceResult("topic-1", "The Topic", new VoiceResult.VoiceProfile("v1", "en-US", 1.0, 0.0),
                List.of(), 40.0, 40.0 + driftSeconds, driftSeconds, exceedsThreshold, thresholdSeconds,
                VoiceResult.AudioFormat.AUDIO_MPEG, 24000, confidence(ConfidenceLevel.HIGH), "1.0.0", Instant.now(),
                "1.0.0");
    }

    static SubtitleResult subtitleResult(List<SubtitleResult.SceneSubtitles> scenes) {
        return new SubtitleResult("topic-1", "The Topic", Storyboard.SubtitleStyle.BOLD_CENTERED,
                SubtitleResult.Format.SRT, null, new SubtitleResult.SafeRegion(10.0, 18.0), scenes, 40.0,
                confidence(ConfidenceLevel.HIGH), "1.0.0", Instant.now(), "1.0.0", "1.0.0");
    }

    static SubtitleResult.SceneSubtitles subtitleScene(String sceneId,
            List<SubtitleResult.ReconciledSegment> segments) {
        return new SubtitleResult.SceneSubtitles(sceneId,
                SubtitleResult.SceneSubtitles.ReconciliationMethod.WORD_ALIGNMENT, segments);
    }

    static AssetManifest assetManifest(Storyboard.RenderStyle renderStyle, String versionSuffix) {
        return new AssetManifest("topic-1", "The Topic", renderStyle,
                new AssetManifest.ResolvedTheme("heading", "body", "code",
                        new AssetManifest.ColorPalette("#000", "#fff", "#0f0", "#f00", "#0f0"), "theme"),
                new AssetManifest.BackgroundMusic("music.mp3", "license", -12.0),
                new AssetManifest.Watermark("wm.png", AssetManifest.Watermark.Position.BOTTOM_RIGHT), List.of(),
                confidence(ConfidenceLevel.HIGH), "1.0.0-" + versionSuffix, Instant.now(), "1.0.0");
    }

    public static VideoPackage videoPackage(String videoFileUri, String thumbnailFrameUri, double durationSeconds,
            long fileSizeBytes, String resolution) {
        return new VideoPackage("pkg-1", "job-1", "topic-1", "The Topic", videoFileUri, thumbnailFrameUri,
                durationSeconds, resolution, Storyboard.AspectRatio.RATIO_9_16, VideoPackage.VideoCodec.H264,
                VideoPackage.AudioCodec.AAC, fileSizeBytes, "checksum123", Instant.now());
    }

    static ContentStrategy contentStrategy(ContentStrategy.HookType hookType) {
        return new ContentStrategy("topic-1", "The Topic", hookType, "reason",
                ContentStrategy.TeachingStyle.EXPLAIN_FIRST, "reason", ContentStrategy.EmotionalGoal.CURIOSITY,
                "reason", ContentStrategy.Pacing.MEDIUM, "reason", List.of(), ContentStrategy.VisualStyle.CODE_ANIMATION,
                List.of(), "reason", ContentStrategy.CodeStyle.MINIMAL_EXAMPLE, "reason", ContentStrategy.CtaStyle.FOLLOW,
                "reason", "retention goal", 30, confidence(ConfidenceLevel.HIGH), 40, "1.0.0", Instant.now(), "1.0.0");
    }

    static ReviewerConfig config() {
        return new ReviewerConfig(0.7, 0.5, 3.0, 1.25, 20.0,
                new QualityScore.Dimensions(0.25, 0.15, 0.15, 0.15, 0.1, 0.05, 0.05, 0.05, 0.05), "1.0.0", "1.0.0");
    }

    /** A {@link QualityScore} with every dimension at the given uniform score. */
    static QualityScore qualityScore(double uniformScore) {
        return qualityScore(new QualityScore.Dimensions(uniformScore, uniformScore, uniformScore, uniformScore,
                uniformScore, uniformScore, uniformScore, uniformScore, uniformScore), uniformScore);
    }

    static QualityScore qualityScore(QualityScore.Dimensions dimensions, double overallScore) {
        return new QualityScore("score-1", "topic-1", "pkg-1", dimensions, overallScore, config().dimensionWeights(),
                List.of(), "1.0.0", Instant.now());
    }

    public static PublishingConfig publishingConfig() {
        return new PublishingConfig("1.0.0", List.of("#java", "#coding"), "Education", "en-US", 100, 2000);
    }

    /** A safe-by-default {@link PlatformUploadConfig}: real upload disabled everywhere. */
    public static PlatformUploadConfig platformUploadConfig() {
        return new PlatformUploadConfig(true,
                new PlatformUploadConfig.YouTube(false, "", "", "", "", "private", "27"),
                new PlatformUploadConfig.Instagram(false, "", "", 0, 3));
    }

    /** A minimal, valid {@link ReviewResult} with the given verdict — every list field empty. */
    public static ReviewResult reviewResult(ReviewResult.Verdict verdict) {
        return new ReviewResult(UUID.randomUUID().toString(), "job-1", "topic-1", "pkg-1", "score-1", verdict, 0.8,
                Map.of(), List.of(), List.of(), List.of(), List.of(),
                verdict == ReviewResult.Verdict.APPROVED ? ReviewResult.RecommendedAction.APPROVE
                        : ReviewResult.RecommendedAction.REGENERATE_FULL,
                "notes", confidence(ConfidenceLevel.HIGH), "1.0.0", Instant.now());
    }
}
