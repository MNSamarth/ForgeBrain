package com.forgebrain.backend.services;

import static com.forgebrain.backend.services.ReviewFixtures.assetManifest;
import static com.forgebrain.backend.services.ReviewFixtures.config;
import static com.forgebrain.backend.services.ReviewFixtures.contentStrategy;
import static com.forgebrain.backend.services.ReviewFixtures.lesson;
import static com.forgebrain.backend.services.ReviewFixtures.script;
import static com.forgebrain.backend.services.ReviewFixtures.storyboard;
import static com.forgebrain.backend.services.ReviewFixtures.subtitleResult;
import static com.forgebrain.backend.services.ReviewFixtures.subtitleScene;
import static com.forgebrain.backend.services.ReviewFixtures.videoPackage;
import static com.forgebrain.backend.services.ReviewFixtures.voiceResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QualityScorer}'s nine dimension checks and its {@code overallScore}
 * weighted average — per this mission's Part 6 ("scoring rules", "category score calculation").
 * No Spring context and no GCP credentials are used anywhere here.
 */
class QualityScorerTest {

    private final QualityScorer scorer = new QualityScorer(config());

    private static final SubtitleResult.ReconciledSegment READABLE_SEGMENT =
            new SubtitleResult.ReconciledSegment("Short line.", 0.0, 2.0, List.of());
    private static final SubtitleResult EASY_SUBTITLES =
            subtitleResult(List.of(subtitleScene("scene-1", List.of(READABLE_SEGMENT))));

    @Test
    void technicalAccuracyIsPerfectWhenScriptAvoidsEveryFlaggedStatement() {
        Lesson lesson = lesson(List.of("int overflow throws an exception"), "ints wrap around on overflow");
        Script script = script("A short punchy hook here", "int overflow wraps around silently.",
                "ints wrap around on overflow", ContentStrategy.HookType.MYTH);

        QualityScore score = score(lesson, script);

        assertThat(score.dimensions().technicalAccuracy()).isEqualTo(1.0);
    }

    @Test
    void technicalAccuracyDropsWhenScriptContainsAFlaggedStatement() {
        Lesson lesson = lesson(List.of("int overflow throws an exception"), "ints wrap around on overflow");
        Script script = script("A short punchy hook here",
                "Remember, int overflow throws an exception in Java.", "ints wrap around on overflow",
                ContentStrategy.HookType.MYTH);

        QualityScore score = score(lesson, script);

        assertThat(score.dimensions().technicalAccuracy()).isLessThan(1.0);
        assertThat(score.dimensionNotes()).anyMatch(n -> n.dimension() == QualityScore.Dimension.TECHNICAL_ACCURACY);
    }

    @Test
    void pacingFitIsPerfectWithNoDrift() {
        VoiceResult voice = voiceResult(0.0, 5.0, false);

        double pacingFit = score(voice).dimensions().pacingFit();

        assertThat(pacingFit).isEqualTo(1.0);
    }

    @Test
    void pacingFitIsPenalizedWhenDriftExceedsThreshold() {
        VoiceResult voice = voiceResult(8.0, 5.0, true);

        double pacingFit = score(voice).dimensions().pacingFit();

        assertThat(pacingFit).isLessThanOrEqualTo(0.4);
    }

    @Test
    void hookStrengthIsZeroForABlankHook() {
        Script script = script("", "some spoken script", "recap", ContentStrategy.HookType.MYTH);

        double hookStrength = scoreDefault(lesson(List.of(), "takeaway"), script).dimensions().hookStrength();

        assertThat(hookStrength).isEqualTo(0.0);
    }

    @Test
    void hookStrengthIsHighForAReasonablyLengthedMatchingHook() {
        Script script = script("What if every loop you have written so far has a hidden bug in it?",
                "some spoken script", "recap", ContentStrategy.HookType.MYTH);

        double hookStrength = scoreWithStrategy(script, ContentStrategy.HookType.MYTH).dimensions().hookStrength();

        assertThat(hookStrength).isEqualTo(1.0);
    }

    @Test
    void educationalClarityRewardsARecapThatSharesWordsWithTheBeginnerTakeaway() {
        Lesson lesson = lesson(List.of(), "ints wrap around silently on overflow instead of throwing");
        Script script = script("hook", "spoken", "ints wrap around silently when they overflow",
                ContentStrategy.HookType.MYTH);

        double clarity = score(lesson, script).dimensions().educationalClarity();

        assertThat(clarity).isGreaterThan(0.5);
    }

    @Test
    void educationalClarityIsLowWhenRecapSharesNoWordsWithTheTakeaway() {
        Lesson lesson = lesson(List.of(), "ints wrap around silently on overflow instead of throwing");
        Script script = script("hook", "spoken", "completely unrelated closing statement",
                ContentStrategy.HookType.MYTH);

        double clarity = score(lesson, script).dimensions().educationalClarity();

        assertThat(clarity).isLessThan(0.3);
    }

    @Test
    void productionPolishIsPenalizedByZeroFileSize() {
        QualityScore score = scoreWithVideo(videoPackage("video.mp4", "thumb.jpg", 40.0, 0, "1080x1920"), 40.0);

        assertThat(score.dimensions().productionPolish()).isLessThan(1.0);
        assertThat(score.dimensionNotes()).anyMatch(n -> n.dimension() == QualityScore.Dimension.PRODUCTION_POLISH);
    }

    @Test
    void productionPolishIsPenalizedByADurationMismatchBeyondTolerance() {
        QualityScore score = scoreWithVideo(videoPackage("video.mp4", "thumb.jpg", 60.0, 12345, "1080x1920"), 40.0);

        assertThat(score.dimensions().productionPolish()).isLessThan(1.0);
    }

    @Test
    void productionPolishIsPerfectWhenEverythingIsWithinBounds() {
        QualityScore score = scoreWithVideo(videoPackage("video.mp4", "thumb.jpg", 40.5, 12345, "1080x1920"), 40.0);

        assertThat(score.dimensions().productionPolish()).isEqualTo(1.0);
    }

    @Test
    void brandConsistencyIsPenalizedWhenResolvedRenderStyleDiffersFromStoryboard() {
        Storyboard storyboard = storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE);
        AssetManifest assets = assetManifest(Storyboard.RenderStyle.MINIMAL_LIGHT, "resolved");

        double brandConsistency = fullScore(storyboard, assets).dimensions().brandConsistency();

        assertThat(brandConsistency).isLessThan(1.0);
    }

    @Test
    void brandConsistencyIsPenalizedByPlaceholderAssets() {
        Storyboard storyboard = storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE);
        AssetManifest assets = assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "placeholder");

        double brandConsistency = fullScore(storyboard, assets).dimensions().brandConsistency();

        assertThat(brandConsistency).isLessThan(1.0);
    }

    @Test
    void visualReadabilityIsPerfectForComfortablyTimedSubtitles() {
        SubtitleResult subtitles =
                subtitleResult(List.of(subtitleScene("scene-1", List.of(READABLE_SEGMENT))));

        double readability = scoreWithSubtitles(subtitles).dimensions().visualReadability();

        assertThat(readability).isEqualTo(1.0);
    }

    @Test
    void visualReadabilityIsPenalizedWhenTextIsFasterThanTheConfiguredReadingSpeed() {
        SubtitleResult.ReconciledSegment tooFast = new SubtitleResult.ReconciledSegment(
                "This is a very long line of subtitle text crammed into a tiny sliver of time.", 0.0, 0.5, List.of());
        SubtitleResult subtitles = subtitleResult(List.of(subtitleScene("scene-1", List.of(tooFast))));

        double readability = scoreWithSubtitles(subtitles).dimensions().visualReadability();

        assertThat(readability).isLessThan(1.0);
    }

    @Test
    void subtitleQualityIsPerfectWithNoOverlappingSegments() {
        SubtitleResult.ReconciledSegment first = new SubtitleResult.ReconciledSegment("First.", 0.0, 2.0, List.of());
        SubtitleResult.ReconciledSegment second = new SubtitleResult.ReconciledSegment("Second.", 2.0, 4.0, List.of());
        SubtitleResult subtitles = subtitleResult(List.of(subtitleScene("scene-1", List.of(first, second))));

        double quality = scoreWithSubtitles(subtitles).dimensions().subtitleQuality();

        assertThat(quality).isEqualTo(1.0);
    }

    @Test
    void subtitleQualityIsPenalizedByOverlappingSegments() {
        SubtitleResult.ReconciledSegment first = new SubtitleResult.ReconciledSegment("First.", 0.0, 3.0, List.of());
        SubtitleResult.ReconciledSegment second = new SubtitleResult.ReconciledSegment("Second.", 2.0, 4.0, List.of());
        SubtitleResult subtitles = subtitleResult(List.of(subtitleScene("scene-1", List.of(first, second))));

        double quality = scoreWithSubtitles(subtitles).dimensions().subtitleQuality();

        assertThat(quality).isLessThan(1.0);
    }

    @Test
    void overallScoreIsTheConfiguredWeightedAverageOfEveryDimension() {
        QualityScore score = fullScore(storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE),
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"));
        QualityScore.Dimensions d = score.dimensions();
        QualityScore.Dimensions w = config().dimensionWeights();

        double expected = (d.technicalAccuracy() * w.technicalAccuracy() + d.pacingFit() * w.pacingFit()
                + d.hookStrength() * w.hookStrength() + d.educationalClarity() * w.educationalClarity()
                + d.productionPolish() * w.productionPolish() + d.brandConsistency() * w.brandConsistency()
                + d.visualReadability() * w.visualReadability() + d.subtitleQuality() * w.subtitleQuality()
                + d.retentionPotential() * w.retentionPotential());

        assertThat(score.overallScore()).isEqualTo(expected, Offset.offset(0.0001));
    }

    @Test
    void everyDimensionStaysWithinTheZeroToOneRange() {
        QualityScore score = fullScore(storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE),
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"));
        QualityScore.Dimensions d = score.dimensions();

        for (double value : List.of(d.technicalAccuracy(), d.pacingFit(), d.hookStrength(), d.educationalClarity(),
                d.productionPolish(), d.brandConsistency(), d.visualReadability(), d.subtitleQuality(),
                d.retentionPotential())) {
            assertThat(value).isBetween(0.0, 1.0);
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private QualityScore score(Lesson lesson, Script script) {
        return scorer.score(videoPackage("video.mp4", "thumb.jpg", 40.0, 12345, "1080x1920"),
                storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE), voiceResult(0.0, 5.0, false), EASY_SUBTITLES,
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"), lesson, script,
                contentStrategy(ContentStrategy.HookType.MYTH));
    }

    private QualityScore scoreDefault(Lesson lesson, Script script) {
        return score(lesson, script);
    }

    private QualityScore scoreWithStrategy(Script script, ContentStrategy.HookType strategyHookType) {
        return scorer.score(videoPackage("video.mp4", "thumb.jpg", 40.0, 12345, "1080x1920"),
                storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE), voiceResult(0.0, 5.0, false), EASY_SUBTITLES,
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"), lesson(List.of(), "takeaway"),
                script, contentStrategy(strategyHookType));
    }

    private QualityScore score(VoiceResult voice) {
        return scorer.score(videoPackage("video.mp4", "thumb.jpg", 40.0, 12345, "1080x1920"),
                storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE), voice, EASY_SUBTITLES,
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"), lesson(List.of(), "takeaway"),
                script("A reasonably sized hook line", "spoken script", "recap", ContentStrategy.HookType.MYTH),
                contentStrategy(ContentStrategy.HookType.MYTH));
    }

    private QualityScore scoreWithVideo(VideoPackage videoPackage, double storyboardDurationSeconds) {
        return scorer.score(videoPackage, storyboard(storyboardDurationSeconds, Storyboard.RenderStyle.DARK_MODE_IDE),
                voiceResult(0.0, 5.0, false), EASY_SUBTITLES,
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"), lesson(List.of(), "takeaway"),
                script("A reasonably sized hook line", "spoken script", "recap", ContentStrategy.HookType.MYTH),
                contentStrategy(ContentStrategy.HookType.MYTH));
    }

    private QualityScore scoreWithSubtitles(SubtitleResult subtitles) {
        return scorer.score(videoPackage("video.mp4", "thumb.jpg", 40.0, 12345, "1080x1920"),
                storyboard(40.0, Storyboard.RenderStyle.DARK_MODE_IDE), voiceResult(0.0, 5.0, false), subtitles,
                assetManifest(Storyboard.RenderStyle.DARK_MODE_IDE, "resolved"), lesson(List.of(), "takeaway"),
                script("A reasonably sized hook line", "spoken script", "recap", ContentStrategy.HookType.MYTH),
                contentStrategy(ContentStrategy.HookType.MYTH));
    }

    private QualityScore fullScore(Storyboard storyboard, AssetManifest assets) {
        return scorer.score(videoPackage("video.mp4", "thumb.jpg", 40.0, 12345, "1080x1920"), storyboard,
                voiceResult(0.0, 5.0, false), EASY_SUBTITLES, assets, lesson(List.of(), "takeaway"),
                script("A reasonably sized hook line", "spoken script", "recap", ContentStrategy.HookType.MYTH),
                contentStrategy(ContentStrategy.HookType.MYTH));
    }
}
