package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.analytics.AnalyticsAggregator;
import com.forgebrain.backend.analytics.AnalyticsMemoryFeedback;
import com.forgebrain.backend.analytics.AnalyticsReport;
import com.forgebrain.backend.analytics.DimensionPerformanceAggregate;
import com.forgebrain.backend.analytics.ReelOutcomeSnapshot;
import com.forgebrain.backend.analytics.TopicPerformanceAggregate;
import com.forgebrain.backend.config.AnalyticsConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.job.ReelJob;
import com.forgebrain.backend.job.ReelJobReport;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Real {@link ReelAnalyticsService}: one {@code <jobId>.json} {@link ReelOutcomeSnapshot} file per
 * job under {@link AnalyticsConfig#snapshotsDirectory()} — the same one-file-per-record local
 * durability convention as {@link com.forgebrain.backend.job.LocalFileReelJobRepository}. Owns a
 * plain {@link AnalyticsAggregator} and {@link AnalyticsMemoryFeedback} directly (mirrors {@code
 * ReviewerServiceImpl} owning {@code QualityScorer}) rather than exposing them as separate beans.
 */
@Component
public class ReelAnalyticsServiceImpl implements ReelAnalyticsService {

    private final ObjectMapper objectMapper;
    private final AnalyticsConfig config;
    private final MemoryService memoryService;
    private final AnalyticsAggregator aggregator;
    private final AnalyticsMemoryFeedback memoryFeedback;
    private final File snapshotsDirectory;
    private final File reportsDirectory;

    public ReelAnalyticsServiceImpl(ObjectMapper objectMapper, AnalyticsConfig config, MemoryService memoryService) {
        this.objectMapper = objectMapper;
        this.config = config;
        this.memoryService = memoryService;
        this.aggregator = new AnalyticsAggregator(config);
        this.memoryFeedback = new AnalyticsMemoryFeedback(config);
        this.snapshotsDirectory = new File(config.snapshotsDirectory());
        this.reportsDirectory = new File(config.reportsDirectory());
    }

    @Override
    public ReelOutcomeSnapshot recordOutcome(ReelJob job, ReelJobReport report, ContentStrategy contentStrategy,
            Script script, VideoPackage videoPackage) {
        ReelOutcomeSnapshot snapshot = buildSnapshot(job, report, contentStrategy, script, videoPackage);
        writeSnapshot(snapshot);
        if (job.topicId() != null) {
            applyMemoryFeedback(job, snapshot);
        }
        return snapshot;
    }

    @Override
    public List<ReelOutcomeSnapshot> findAll() {
        File[] files = snapshotsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        List<ReelOutcomeSnapshot> snapshots = new ArrayList<>();
        for (File file : files) {
            try {
                snapshots.add(objectMapper.readValue(file, ReelOutcomeSnapshot.class));
            } catch (IOException e) {
                throw new ConfigurationException("Failed to read analytics snapshot from " + file.getAbsolutePath(),
                        e);
            }
        }
        return List.copyOf(snapshots);
    }

    @Override
    public AnalyticsReport generateReport(Instant windowStart, Instant windowEnd) {
        List<ReelOutcomeSnapshot> windowSnapshots = findAll().stream()
                .filter(s -> !s.jobCreatedAt().isBefore(windowStart) && !s.jobCreatedAt().isAfter(windowEnd))
                .toList();
        AnalyticsReport report = aggregator.buildReport(windowSnapshots, windowStart, windowEnd);
        writeReportJson(report);
        writeReportMarkdown(report);
        return report;
    }

    private ReelOutcomeSnapshot buildSnapshot(ReelJob job, ReelJobReport report, ContentStrategy contentStrategy,
            Script script, VideoPackage videoPackage) {
        ReviewResult reviewResult = report.reviewResult();
        PublishingResult publishingResult = report.publishingResult();

        ContentStrategy.HookType hookType = script != null ? script.hookType()
                : (contentStrategy != null ? contentStrategy.hookType() : null);
        ContentStrategy.TeachingStyle teachingStyle = script != null ? script.teachingStyle()
                : (contentStrategy != null ? contentStrategy.teachingStyle() : null);
        List<Script.Platform> platformTargets = platformTargets(publishingResult, script);

        return new ReelOutcomeSnapshot(
                UUID.randomUUID().toString(),
                job.jobId(),
                job.topicId(),
                job.topicTitle(),
                hookType,
                teachingStyle,
                platformTargets,
                videoPackage != null ? videoPackage.durationSeconds() : null,
                reviewResult != null ? reviewResult.verdict() : null,
                reviewResult != null ? reviewResult.score() : null,
                job.publishingStatus(),
                job.outputFiles(),
                classifyOutcome(job, reviewResult, publishingResult),
                report.warnings().size(),
                !report.fallbackStages().isEmpty(),
                report.fallbackStages(),
                0,
                job.failureReason(),
                job.createdAt(),
                Instant.now(),
                config.snapshotVersion());
    }

    private static List<Script.Platform> platformTargets(PublishingResult publishingResult, Script script) {
        if (publishingResult != null && !publishingResult.platformOutcomes().isEmpty()) {
            return publishingResult.platformOutcomes().stream().map(outcome -> outcome.platform()).distinct().toList();
        }
        if (script != null && script.platform() != null) {
            return List.of(script.platform());
        }
        return List.of();
    }

    private static ReelOutcomeSnapshot.Outcome classifyOutcome(ReelJob job, ReviewResult reviewResult,
            PublishingResult publishingResult) {
        if (job.status() != ReelJob.Status.COMPLETED || reviewResult == null) {
            return ReelOutcomeSnapshot.Outcome.FAILED;
        }
        return switch (reviewResult.verdict()) {
            case REJECTED -> ReelOutcomeSnapshot.Outcome.REJECTED;
            case NEEDS_REVISION -> ReelOutcomeSnapshot.Outcome.NEEDS_REVISION;
            case APPROVED -> (publishingResult != null && publishingResult.status() == PublishingResult.Status.READY)
                    ? ReelOutcomeSnapshot.Outcome.PUBLISHED
                    : ReelOutcomeSnapshot.Outcome.PUBLISH_FAILED;
        };
    }

    private void applyMemoryFeedback(ReelJob job, ReelOutcomeSnapshot snapshot) {
        List<ReelOutcomeSnapshot> topicSnapshots = findAll().stream()
                .filter(s -> job.topicId().equals(s.topicId()))
                .toList();
        TopicPerformanceAggregate aggregate = aggregator.aggregateTopic(job.topicId(), job.topicTitle(),
                topicSnapshots);
        MemoryState.TopicRecord existing = memoryService.getTopicRecord(job.topicId());
        MemoryState.TopicRecord updated = memoryFeedback.apply(existing, snapshot, aggregate);
        memoryService.updateTopicRecord(job.topicId(), updated);
    }

    private void writeSnapshot(ReelOutcomeSnapshot snapshot) {
        if (!snapshotsDirectory.exists() && !snapshotsDirectory.mkdirs()) {
            throw new ConfigurationException("Could not create analytics snapshots directory at "
                    + snapshotsDirectory.getAbsolutePath());
        }
        File file = new File(snapshotsDirectory, snapshot.jobId() + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, snapshot);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write analytics snapshot to " + file.getAbsolutePath(), e);
        }
    }

    private void writeReportJson(AnalyticsReport report) {
        File file = reportFile(report, "json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, report);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write analytics report to " + file.getAbsolutePath(), e);
        }
    }

    private void writeReportMarkdown(AnalyticsReport report) {
        File file = reportFile(report, "md");
        try {
            java.nio.file.Files.writeString(file.toPath(), renderMarkdown(report));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write analytics report to " + file.getAbsolutePath(), e);
        }
    }

    private File reportFile(AnalyticsReport report, String extension) {
        if (!reportsDirectory.exists() && !reportsDirectory.mkdirs()) {
            throw new ConfigurationException("Could not create analytics reports directory at "
                    + reportsDirectory.getAbsolutePath());
        }
        return new File(reportsDirectory, report.reportId() + "." + extension);
    }

    private static String renderMarkdown(AnalyticsReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Analytics Report\n\n");
        md.append("Window: ").append(report.windowStart()).append(" to ").append(report.windowEnd()).append("\n\n");
        md.append("Reels analyzed: ").append(report.totalReelsAnalyzed()).append("\n\n");

        md.append("## Review Trends\n\n");
        md.append("- Average review score: ").append(report.reviewTrends().averageReviewScore()).append("\n");
        md.append("- Approval rate: ").append(percent(report.reviewTrends().approvalRate())).append("\n");
        md.append("- Rejection rate: ").append(percent(report.reviewTrends().rejectionRate())).append("\n");
        md.append("- Revision rate: ").append(percent(report.reviewTrends().revisionRate())).append("\n\n");

        md.append("## Publish Readiness\n\n");
        md.append("- Published: ").append(report.publishReadinessTrends().publishedCount()).append("\n");
        md.append("- Publish failed: ").append(report.publishReadinessTrends().publishFailedCount()).append("\n");
        md.append("- Needs revision: ").append(report.publishReadinessTrends().needsRevisionCount()).append("\n");
        md.append("- Rejected: ").append(report.publishReadinessTrends().rejectedCount()).append("\n");
        md.append("- Failed: ").append(report.publishReadinessTrends().failedCount()).append("\n\n");

        md.append("## Top-Performing Topics\n\n");
        appendTopicList(md, report.topPerformingTopics());

        md.append("## Weak Topics\n\n");
        appendTopicList(md, report.weakTopics());

        md.append("## Topic Drift (Declining Trend)\n\n");
        appendTopicList(md, report.topicsWithDecliningTrend());

        md.append("## Recommended Revisit Topics\n\n");
        if (report.recommendedRevisitTopics().isEmpty()) {
            md.append("- none\n\n");
        } else {
            for (String topicId : report.recommendedRevisitTopics()) {
                md.append("- ").append(topicId).append("\n");
            }
            md.append("\n");
        }

        appendDimensionSection(md, "Hook Type Performance", report.hookTypePerformance());
        appendDimensionSection(md, "Teaching Style Performance", report.teachingStylePerformance());
        appendDimensionSection(md, "Platform Performance", report.platformPerformance());

        return md.toString();
    }

    private static void appendTopicList(StringBuilder md, List<TopicPerformanceAggregate> topics) {
        if (topics.isEmpty()) {
            md.append("- none\n\n");
            return;
        }
        for (TopicPerformanceAggregate topic : topics) {
            md.append("- ").append(topic.topicTitle()).append(" (").append(topic.topicId()).append("): score=")
                    .append(topic.averageReviewScore()).append(", approval=").append(percent(topic.approvalRate()))
                    .append(", trend=").append(topic.trendDirection()).append("\n");
        }
        md.append("\n");
    }

    private static void appendDimensionSection(StringBuilder md, String heading,
            List<DimensionPerformanceAggregate> aggregates) {
        md.append("## ").append(heading).append("\n\n");
        if (aggregates.isEmpty()) {
            md.append("- none\n\n");
            return;
        }
        for (DimensionPerformanceAggregate aggregate : aggregates) {
            md.append("- ").append(aggregate.value()).append(": score=").append(aggregate.averageReviewScore())
                    .append(", approval=").append(percent(aggregate.approvalRate())).append(", n=")
                    .append(aggregate.sampleSize()).append("\n");
        }
        md.append("\n");
    }

    private static String percent(double rate) {
        return String.format(Locale.ROOT, "%.0f%%", rate * 100);
    }
}
