package com.forgebrain.backend.services;

import com.forgebrain.backend.config.ReviewerConfig;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.QualityScore.Dimension;
import com.forgebrain.backend.models.QualityScore.DimensionNote;
import com.forgebrain.backend.models.QualityScore.Dimensions;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes a {@link QualityScore}'s nine dimensions from real pipeline artifacts, per
 * reviewer/quality-scoring-spec.md — kept as its own plain class (not a {@code @Component}),
 * mirroring {@code rendering.ffmpeg.FfmpegTextEscaper}/{@code SrtWriter}: pure, stateless-per-call
 * logic that {@link ReviewerServiceImpl} owns directly, exactly matching quality-scoring-spec.md
 * Section 1's own architectural principle that scoring is a function the Reviewer consumes, not
 * the review decision itself.
 *
 * <p>Every check here is mechanical and explainable, per this mission's "keep the reviewer
 * deterministic and explainable" — none of them are an AI judgment call. {@code hook_strength}
 * and {@code retention_potential} in particular are explicitly the weakest proxies here (see
 * their {@link DimensionNote}s) — quality-scoring-spec.md Section 3 already flags {@code
 * hook_strength} as "the most subjective dimension... most likely to need AI-assisted judgment,"
 * and this implementation does not pretend otherwise.
 */
public class QualityScorer {

    private final ReviewerConfig config;

    public QualityScorer(ReviewerConfig config) {
        this.config = config;
    }

    /**
     * Cross-checks {@code script.fullSpokenScript} against every entry in {@code
     * lesson.whatToAvoidSaying}, case-insensitively. Exposed statically so {@link
     * ReviewerServiceImpl} can reuse the exact same check for its hard-gate decision — see
     * reviewer-spec.md Section 3, which requires this to be the same check in both places, not
     * two independently-drifting implementations of "does the script say something it shouldn't."
     */
    public static List<String> findSafetyViolations(Lesson lesson, Script script) {
        List<String> avoid = lesson.whatToAvoidSaying() == null ? List.of() : lesson.whatToAvoidSaying();
        String spoken = script.fullSpokenScript() == null ? "" : script.fullSpokenScript().toLowerCase(Locale.ROOT);
        return avoid.stream()
                .filter(entry -> entry != null && !entry.isBlank() && spoken.contains(entry.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    public QualityScore score(VideoPackage videoPackage, Storyboard storyboard, VoiceResult voiceResult,
            SubtitleResult subtitleResult, AssetManifest assetManifest, Lesson lesson, Script script,
            ContentStrategy contentStrategy) {
        List<DimensionNote> notes = new ArrayList<>();

        double technicalAccuracy = scoreTechnicalAccuracy(lesson, script, notes);
        double pacingFit = scorePacingFit(voiceResult, notes);
        double hookStrength = scoreHookStrength(script, contentStrategy, notes);
        double educationalClarity = scoreEducationalClarity(lesson, script, notes);
        double productionPolish = scoreProductionPolish(videoPackage, storyboard, notes);
        double brandConsistency = scoreBrandConsistency(storyboard, assetManifest, notes);
        double visualReadability = scoreVisualReadability(subtitleResult, notes);
        double subtitleQuality = scoreSubtitleQuality(subtitleResult, notes);
        double retentionPotential = scoreRetentionPotential(hookStrength, pacingFit, notes);

        Dimensions dimensions = new Dimensions(technicalAccuracy, pacingFit, hookStrength, educationalClarity,
                productionPolish, brandConsistency, visualReadability, subtitleQuality, retentionPotential);

        return new QualityScore(
                UUID.randomUUID().toString(),
                videoPackage.topicId(),
                videoPackage.packageId(),
                dimensions,
                weightedAverage(dimensions, config.dimensionWeights()),
                config.dimensionWeights(),
                List.copyOf(notes),
                config.scoringVersion(),
                Instant.now());
    }

    private double scoreTechnicalAccuracy(Lesson lesson, Script script, List<DimensionNote> notes) {
        List<String> violations = findSafetyViolations(lesson, script);
        double score = violations.isEmpty() ? 1.0 : clamp(1.0 - violations.size() * 0.5);
        if (!violations.isEmpty()) {
            notes.add(new DimensionNote(Dimension.TECHNICAL_ACCURACY,
                    violations.size() + " statement(s) flagged in lesson.what_to_avoid_saying appear in the "
                            + "script: " + String.join("; ", violations)));
        }
        return score;
    }

    private double scorePacingFit(VoiceResult voiceResult, List<DimensionNote> notes) {
        double drift = Math.abs(voiceResult.totalDurationDriftSeconds());
        double threshold = voiceResult.driftThresholdSeconds() > 0 ? voiceResult.driftThresholdSeconds() : 1.0;
        double score = clamp(1.0 - (drift / threshold));
        if (voiceResult.driftExceedsThreshold()) {
            score = Math.min(score, 0.4);
        }
        addNoteIfBelowFloor(Dimension.PACING_FIT, score,
                "Voice timing drifted " + drift + "s against a " + threshold + "s threshold.", notes);
        return score;
    }

    private double scoreHookStrength(Script script, ContentStrategy contentStrategy, List<DimensionNote> notes) {
        String hook = script.hook() == null ? "" : script.hook().strip();
        int words = hook.isEmpty() ? 0 : hook.split("\\s+").length;
        double lengthScore = words == 0 ? 0.0 : (words < 3 || words > 30 ? 0.4 : 1.0);
        boolean strategyMatch = contentStrategy == null || script.hookType() == null
                || script.hookType() == contentStrategy.hookType();
        double score = clamp(strategyMatch ? lengthScore : lengthScore * 0.75);
        notes.add(new DimensionNote(Dimension.HOOK_STRENGTH,
                "Mechanically scored from hook word count (" + words + ") and hook-type/strategy match "
                        + "(" + strategyMatch + "); not an AI judgment of actual hook quality — "
                        + "quality-scoring-spec.md Section 3 flags this dimension as the one most likely to "
                        + "need that."));
        return score;
    }

    private double scoreEducationalClarity(Lesson lesson, Script script, List<DimensionNote> notes) {
        double overlap = tokenOverlapRatio(script.recapLine(), lesson.beginnerTakeaway());
        addNoteIfBelowFloor(Dimension.EDUCATIONAL_CLARITY, overlap,
                "recap_line shares only " + Math.round(overlap * 100)
                        + "% of its significant words with lesson.beginner_takeaway.", notes);
        return overlap;
    }

    private double scoreProductionPolish(VideoPackage videoPackage, Storyboard storyboard,
            List<DimensionNote> notes) {
        List<String> problems = new ArrayList<>();
        double score = 1.0;
        if (videoPackage.fileSizeBytes() <= 0) {
            problems.add("file size is 0 bytes");
            score -= 0.4;
        }
        if (videoPackage.resolution() == null || videoPackage.resolution().isBlank()) {
            problems.add("resolution is blank");
            score -= 0.2;
        }
        double durationDelta = Math.abs(videoPackage.durationSeconds() - storyboard.totalDurationSeconds());
        if (durationDelta > config.durationMismatchToleranceSeconds()) {
            problems.add("rendered duration differs from the storyboard's planned duration by "
                    + durationDelta + "s (tolerance " + config.durationMismatchToleranceSeconds() + "s)");
            score -= 0.4;
        }
        score = clamp(score);
        if (!problems.isEmpty()) {
            notes.add(new DimensionNote(Dimension.PRODUCTION_POLISH, String.join("; ", problems)));
        }
        return score;
    }

    private double scoreBrandConsistency(Storyboard storyboard, AssetManifest assetManifest,
            List<DimensionNote> notes) {
        List<String> problems = new ArrayList<>();
        double score = 1.0;
        if (assetManifest.renderStyle() != storyboard.renderStyle()) {
            problems.add("resolved renderStyle (" + assetManifest.renderStyle()
                    + ") does not match the storyboard's requested style (" + storyboard.renderStyle() + ")");
            score -= 0.6;
        }
        if (assetManifest.assetManifestVersion() != null
                && assetManifest.assetManifestVersion().contains("placeholder")) {
            problems.add("asset manifest used placeholder assets, not a real resolved catalog");
            score -= 0.3;
        }
        score = clamp(score);
        if (!problems.isEmpty()) {
            notes.add(new DimensionNote(Dimension.BRAND_CONSISTENCY, String.join("; ", problems)));
        }
        return score;
    }

    private double scoreVisualReadability(SubtitleResult subtitleResult, List<DimensionNote> notes) {
        List<Double> charsPerSecond = new ArrayList<>();
        for (SubtitleResult.SceneSubtitles scene : subtitleResult.scenes()) {
            for (SubtitleResult.ReconciledSegment segment : scene.segments()) {
                double duration = segment.endTime() - segment.startTime();
                if (duration <= 0 || segment.text() == null) {
                    continue;
                }
                charsPerSecond.add(segment.text().length() / duration);
            }
        }
        if (charsPerSecond.isEmpty()) {
            notes.add(new DimensionNote(Dimension.VISUAL_READABILITY,
                    "No timed subtitle segments were available to measure reading speed; neutral default used."));
            return 0.5;
        }
        long tooFast = charsPerSecond.stream().filter(cps -> cps > config.maxSubtitleReadingCharsPerSecond()).count();
        double score = clamp(1.0 - ((double) tooFast / charsPerSecond.size()));
        if (tooFast > 0) {
            notes.add(new DimensionNote(Dimension.VISUAL_READABILITY,
                    tooFast + " of " + charsPerSecond.size() + " subtitle segment(s) exceed "
                            + config.maxSubtitleReadingCharsPerSecond() + " chars/sec, likely unreadable."));
        }
        return score;
    }

    private double scoreSubtitleQuality(SubtitleResult subtitleResult, List<DimensionNote> notes) {
        int total = 0;
        int overlaps = 0;
        for (SubtitleResult.SceneSubtitles scene : subtitleResult.scenes()) {
            List<SubtitleResult.ReconciledSegment> segments = scene.segments();
            for (int i = 0; i < segments.size(); i++) {
                total++;
                if (i > 0 && segments.get(i).startTime() < segments.get(i - 1).endTime()) {
                    overlaps++;
                }
            }
        }
        if (total == 0) {
            notes.add(new DimensionNote(Dimension.SUBTITLE_QUALITY,
                    "No subtitle segments were available to check for overlap; neutral default used."));
            return 0.5;
        }
        double score = clamp(1.0 - ((double) overlaps / total));
        if (overlaps > 0) {
            notes.add(new DimensionNote(Dimension.SUBTITLE_QUALITY,
                    overlaps + " of " + total + " subtitle segment(s) start before the previous segment ends."));
        }
        return score;
    }

    private double scoreRetentionPotential(double hookStrength, double pacingFit, List<DimensionNote> notes) {
        double score = clamp((hookStrength + pacingFit) / 2.0);
        notes.add(new DimensionNote(Dimension.RETENTION_POTENTIAL,
                "Derived as the average of hook_strength and pacing_fit, not an independent measurement — no "
                        + "real audience retention data exists yet (see quality-scoring-spec.md Section 5)."));
        return score;
    }

    private void addNoteIfBelowFloor(Dimension dimension, double score, String reason, List<DimensionNote> notes) {
        if (score < config.dimensionFloor()) {
            notes.add(new DimensionNote(dimension, reason));
        }
    }

    private static double weightedAverage(Dimensions dimensions, Dimensions weights) {
        double weightSum = weights.technicalAccuracy() + weights.pacingFit() + weights.hookStrength()
                + weights.educationalClarity() + weights.productionPolish() + weights.brandConsistency()
                + weights.visualReadability() + weights.subtitleQuality() + weights.retentionPotential();
        if (weightSum <= 0) {
            weightSum = 1.0;
        }
        double weighted = dimensions.technicalAccuracy() * weights.technicalAccuracy()
                + dimensions.pacingFit() * weights.pacingFit()
                + dimensions.hookStrength() * weights.hookStrength()
                + dimensions.educationalClarity() * weights.educationalClarity()
                + dimensions.productionPolish() * weights.productionPolish()
                + dimensions.brandConsistency() * weights.brandConsistency()
                + dimensions.visualReadability() * weights.visualReadability()
                + dimensions.subtitleQuality() * weights.subtitleQuality()
                + dimensions.retentionPotential() * weights.retentionPotential();
        return clamp(weighted / weightSum);
    }

    private static double tokenOverlapRatio(String recapLine, String beginnerTakeaway) {
        Set<String> takeawayWords = significantWords(beginnerTakeaway);
        if (takeawayWords.isEmpty()) {
            return 0.5;
        }
        Set<String> recapWords = significantWords(recapLine);
        long shared = takeawayWords.stream().filter(recapWords::contains).count();
        return clamp((double) shared / takeawayWords.size());
    }

    private static Set<String> significantWords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 2)
                .collect(Collectors.toSet());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
