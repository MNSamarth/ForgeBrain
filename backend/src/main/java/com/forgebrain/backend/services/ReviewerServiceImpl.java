package com.forgebrain.backend.services;

import com.forgebrain.backend.config.ReviewerConfig;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.QualityScore.Dimensions;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.ReviewResult.RecommendedAction;
import com.forgebrain.backend.models.ReviewResult.ReviewIssue;
import com.forgebrain.backend.models.ReviewResult.ReviewIssue.Severity;
import com.forgebrain.backend.models.ReviewResult.Verdict;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.shared.PipelineStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Real {@link ReviewerService} implementation: the pipeline's final quality gate, run after
 * rendering and before publishing package preparation (see reviewer/reviewer-spec.md Section 1).
 * Combines two independent checks, never averaged together (reviewer-spec.md Section 3):
 *
 * <ul>
 *     <li><b>Hard gates</b> — {@link QualityScorer#findSafetyViolations} (script says something
 *     {@code lesson.whatToAvoidSaying} explicitly forbids) and missing output artifacts. Either
 *     one alone forces {@link Verdict#REJECTED}, regardless of how well everything else scored.
 *     <li><b>Scored judgment</b> — {@link QualityScorer}'s nine dimensions. {@code overallScore}
 *     below {@link ReviewerConfig#approvalThreshold()}, or any single dimension below {@link
 *     ReviewerConfig#dimensionFloor()}, yields {@link Verdict#NEEDS_REVISION} rather than a
 *     rejection — the reel isn't necessarily wrong, just not good enough yet.
 * </ul>
 */
@Component
public class ReviewerServiceImpl implements ReviewerService {

    private final ReviewerConfig config;
    private final QualityScorer qualityScorer;

    public ReviewerServiceImpl(ReviewerConfig config) {
        this.config = config;
        this.qualityScorer = new QualityScorer(config);
    }

    @Override
    public QualityScore scoreQuality(VideoPackage videoPackage, Storyboard storyboard, VoiceResult voiceResult,
            SubtitleResult subtitleResult, AssetManifest assetManifest, Lesson lesson, Script script,
            ContentStrategy contentStrategy) {
        return qualityScorer.score(videoPackage, storyboard, voiceResult, subtitleResult, assetManifest, lesson,
                script, contentStrategy);
    }

    @Override
    public ReviewResult review(String jobId, VideoPackage videoPackage, QualityScore qualityScore, Lesson lesson,
            Script script, String subtitleFileUri) {
        List<String> hardGateViolations = QualityScorer.findSafetyViolations(lesson, script);

        List<ReviewIssue> issues = new ArrayList<>();
        for (String violation : hardGateViolations) {
            issues.add(new ReviewIssue(Severity.BLOCKING,
                    "Hard gate violation: script contains \"" + violation + "\", which "
                            + "lesson.what_to_avoid_saying explicitly flags.", PipelineStage.SCRIPT));
        }
        for (String missing : findMissingArtifacts(videoPackage, subtitleFileUri)) {
            issues.add(new ReviewIssue(Severity.BLOCKING, "Missing output artifact: " + missing,
                    PipelineStage.RENDERER));
        }

        boolean hasBlocking = issues.stream().anyMatch(issue -> issue.severity() == Severity.BLOCKING);
        if (!hasBlocking) {
            issues.addAll(dimensionIssues(qualityScore));
        }

        Verdict verdict = determineVerdict(hasBlocking, qualityScore);
        RecommendedAction recommendedAction = determineRecommendedAction(verdict, issues);

        List<String> warnings = issues.stream()
                .filter(issue -> issue.severity() != Severity.BLOCKING)
                .map(ReviewIssue::description)
                .toList();
        List<String> errors = issues.stream()
                .filter(issue -> issue.severity() == Severity.BLOCKING)
                .map(ReviewIssue::description)
                .toList();

        ConfidenceNotes confidenceNotes = combineConfidence(lesson.confidenceNotes(), script.confidenceNotes());
        String reviewerNotes = buildReviewerNotes(verdict, recommendedAction, qualityScore, issues);

        return new ReviewResult(
                UUID.randomUUID().toString(),
                jobId,
                videoPackage.topicId(),
                videoPackage.packageId(),
                qualityScore.scoreId(),
                verdict,
                qualityScore.overallScore(),
                categoryScoresOf(qualityScore.dimensions()),
                List.copyOf(hardGateViolations),
                List.copyOf(issues),
                warnings,
                errors,
                recommendedAction,
                reviewerNotes,
                confidenceNotes,
                config.reviewerVersion(),
                Instant.now());
    }

    @Override
    public ReviewResult selectBest(List<ReviewResult> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("selectBest requires at least one ReviewResult to choose from.");
        }
        return variants.stream()
                .max(Comparator.comparingInt((ReviewResult r) -> verdictRank(r.verdict()))
                        .thenComparingDouble(ReviewResult::score))
                .orElseThrow();
    }

    private List<String> findMissingArtifacts(VideoPackage videoPackage, String subtitleFileUri) {
        List<String> missing = new ArrayList<>();
        if (!isRegularFile(videoPackage.videoFileUri())) {
            missing.add("video (" + videoPackage.videoFileUri() + ")");
        }
        if (videoPackage.thumbnailFrameUri() != null && !isRegularFile(videoPackage.thumbnailFrameUri())) {
            missing.add("thumbnail (" + videoPackage.thumbnailFrameUri() + ")");
        }
        if (!isRegularFile(subtitleFileUri)) {
            missing.add("subtitles (" + subtitleFileUri + ")");
        }
        return missing;
    }

    private static boolean isRegularFile(String uri) {
        return uri != null && !uri.isBlank() && Files.isRegularFile(Path.of(uri));
    }

    private List<ReviewIssue> dimensionIssues(QualityScore qualityScore) {
        Dimensions d = qualityScore.dimensions();
        List<ReviewIssue> issues = new ArrayList<>();
        addDimensionIssueIfBelowFloor(issues, "technical_accuracy", d.technicalAccuracy(), PipelineStage.SCRIPT);
        addDimensionIssueIfBelowFloor(issues, "pacing_fit", d.pacingFit(), PipelineStage.VOICE);
        addDimensionIssueIfBelowFloor(issues, "hook_strength", d.hookStrength(), PipelineStage.CONTENT_DIRECTOR);
        addDimensionIssueIfBelowFloor(issues, "educational_clarity", d.educationalClarity(), PipelineStage.SCRIPT);
        addDimensionIssueIfBelowFloor(issues, "production_polish", d.productionPolish(), PipelineStage.RENDERER);
        addDimensionIssueIfBelowFloor(issues, "brand_consistency", d.brandConsistency(), PipelineStage.ASSETS);
        addDimensionIssueIfBelowFloor(issues, "visual_readability", d.visualReadability(), PipelineStage.SUBTITLES);
        addDimensionIssueIfBelowFloor(issues, "subtitle_quality", d.subtitleQuality(), PipelineStage.SUBTITLES);
        addDimensionIssueIfBelowFloor(issues, "retention_potential", d.retentionPotential(),
                PipelineStage.CONTENT_DIRECTOR);
        return issues;
    }

    private void addDimensionIssueIfBelowFloor(List<ReviewIssue> issues, String dimensionName, double score,
            PipelineStage suggestedStage) {
        if (score < config.dimensionFloor()) {
            Severity severity = score < config.dimensionFloor() * 0.5 ? Severity.MAJOR : Severity.MINOR;
            issues.add(new ReviewIssue(severity,
                    dimensionName + " scored " + round2(score) + ", below the configured floor of "
                            + config.dimensionFloor() + ".", suggestedStage));
        }
    }

    private Verdict determineVerdict(boolean hasBlocking, QualityScore qualityScore) {
        if (hasBlocking) {
            return Verdict.REJECTED;
        }
        if (qualityScore.overallScore() < config.approvalThreshold() || belowFloorAnywhere(qualityScore)) {
            return Verdict.NEEDS_REVISION;
        }
        return Verdict.APPROVED;
    }

    private boolean belowFloorAnywhere(QualityScore qualityScore) {
        Dimensions d = qualityScore.dimensions();
        double floor = config.dimensionFloor();
        return d.technicalAccuracy() < floor || d.pacingFit() < floor || d.hookStrength() < floor
                || d.educationalClarity() < floor || d.productionPolish() < floor || d.brandConsistency() < floor
                || d.visualReadability() < floor || d.subtitleQuality() < floor || d.retentionPotential() < floor;
    }

    private static RecommendedAction determineRecommendedAction(Verdict verdict, List<ReviewIssue> issues) {
        return switch (verdict) {
            case APPROVED -> RecommendedAction.APPROVE;
            case REJECTED -> RecommendedAction.REJECT;
            case NEEDS_REVISION -> {
                Set<PipelineStage> stages = issues.stream()
                        .filter(issue -> issue.severity() != Severity.BLOCKING)
                        .map(ReviewIssue::suggestedStageToRevisit)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                yield stages.size() == 1 ? RecommendedAction.REGENERATE_SECTION : RecommendedAction.REGENERATE_FULL;
            }
        };
    }

    private static int verdictRank(Verdict verdict) {
        return switch (verdict) {
            case APPROVED -> 2;
            case NEEDS_REVISION -> 1;
            case REJECTED -> 0;
        };
    }

    private static Map<String, Double> categoryScoresOf(Dimensions d) {
        Map<String, Double> categoryScores = new LinkedHashMap<>();
        categoryScores.put("technical_accuracy", d.technicalAccuracy());
        categoryScores.put("pacing_fit", d.pacingFit());
        categoryScores.put("hook_strength", d.hookStrength());
        categoryScores.put("educational_clarity", d.educationalClarity());
        categoryScores.put("production_polish", d.productionPolish());
        categoryScores.put("brand_consistency", d.brandConsistency());
        categoryScores.put("visual_readability", d.visualReadability());
        categoryScores.put("subtitle_quality", d.subtitleQuality());
        categoryScores.put("retention_potential", d.retentionPotential());
        return Map.copyOf(categoryScores);
    }

    private static ConfidenceNotes combineConfidence(ConfidenceNotes lessonConfidence,
            ConfidenceNotes scriptConfidence) {
        ConfidenceLevel level = worstOf(
                lessonConfidence == null ? null : lessonConfidence.overallConfidence(),
                scriptConfidence == null ? null : scriptConfidence.overallConfidence());
        List<String> flagged = new ArrayList<>();
        if (lessonConfidence != null && lessonConfidence.flaggedUncertainties() != null) {
            flagged.addAll(lessonConfidence.flaggedUncertainties());
        }
        if (scriptConfidence != null && scriptConfidence.flaggedUncertainties() != null) {
            flagged.addAll(scriptConfidence.flaggedUncertainties());
        }
        flagged.add("hook_strength and retention_potential are mechanically-derived proxies, not AI-judged scores.");
        return new ConfidenceNotes(level, List.copyOf(flagged), List.of());
    }

    private static ConfidenceLevel worstOf(ConfidenceLevel a, ConfidenceLevel b) {
        if (a == ConfidenceLevel.LOW || b == ConfidenceLevel.LOW) {
            return ConfidenceLevel.LOW;
        }
        if (a == ConfidenceLevel.MEDIUM || b == ConfidenceLevel.MEDIUM) {
            return ConfidenceLevel.MEDIUM;
        }
        if (a == null && b == null) {
            return ConfidenceLevel.MEDIUM;
        }
        return ConfidenceLevel.HIGH;
    }

    private static String buildReviewerNotes(Verdict verdict, RecommendedAction action, QualityScore qualityScore,
            List<ReviewIssue> issues) {
        if (verdict == Verdict.APPROVED) {
            return "APPROVED — overall score " + round2(qualityScore.overallScore())
                    + ", no blocking or quality issues found.";
        }
        String issueSummary = issues.isEmpty()
                ? "no specific issues recorded"
                : issues.size() + " issue(s): "
                        + issues.stream().map(ReviewIssue::description).collect(Collectors.joining(" | "));
        return verdict + " (" + action + ") — overall score " + round2(qualityScore.overallScore()) + ". "
                + issueSummary;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
