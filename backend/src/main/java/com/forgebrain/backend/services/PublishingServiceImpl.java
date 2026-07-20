package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PublishingConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.PipelineStageException;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.publishing.PlatformFormatter;
import com.forgebrain.backend.publishing.PlatformPublishAdapter;
import com.forgebrain.backend.publishing.PlatformPublishAdapterFactory;
import com.forgebrain.backend.publishing.PublishingMetadataGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Real {@link PublishingService} implementation. Enforces the approval precondition
 * (publishing-spec.md Section 4), builds a {@link PublishingPackage} with one {@link
 * PublishingPackage.PlatformVariant} per platform {@link PlatformPublishAdapterFactory} supports
 * (formatted by a matching {@link PlatformFormatter} when one is registered for that platform),
 * writes it to disk, and hands it to whichever adapter — dry-run or real, see {@link
 * PlatformPublishAdapterFactory} — the factory resolves for each platform, collecting each one's
 * {@link PlatformPublishOutcome} rather than letting one platform's failure block the others.
 */
@Component
public class PublishingServiceImpl implements PublishingService {

    private final PublishingConfig config;
    private final ObjectMapper objectMapper;
    private final PublishingMetadataGenerator metadataGenerator;
    private final PlatformPublishAdapterFactory adapterFactory;
    private final Map<Script.Platform, PlatformFormatter> formattersByPlatform;

    public PublishingServiceImpl(PublishingConfig config, ObjectMapper objectMapper,
            PlatformPublishAdapterFactory adapterFactory, List<PlatformFormatter> formatters) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.metadataGenerator = new PublishingMetadataGenerator(config);
        this.adapterFactory = adapterFactory;
        this.formattersByPlatform = formatters.stream()
                .collect(Collectors.toMap(PlatformFormatter::supportedPlatform, f -> f));
    }

    @Override
    public PublishingResult publish(String jobId, Path outputDirectory, ReviewResult reviewResult,
            VideoPackage videoPackage, String subtitleFileUri, Lesson lesson, Script script) {
        if (reviewResult.verdict() != ReviewResult.Verdict.APPROVED) {
            throw new PipelineStageException("PUBLISHING",
                    "Publishing requires an APPROVED review verdict; review '" + reviewResult.reviewId()
                            + "' has verdict " + reviewResult.verdict() + ". Rejected or needs-revision reels must"
                            + " not be published.");
        }

        PublishingMetadata defaultMetadata = metadataGenerator.generate(lesson, script);
        List<PublishingPackage.PlatformVariant> variants = buildPlatformVariants(lesson, script, defaultMetadata);

        PublishingPackage publishingPackage = new PublishingPackage(
                UUID.randomUUID().toString(),
                jobId,
                videoPackage.topicId(),
                videoPackage.topicTitle(),
                reviewResult.reviewId(),
                reviewResult.verdict().name(),
                videoPackage.packageId(),
                videoPackage.videoFileUri(),
                videoPackage.thumbnailFrameUri(),
                subtitleFileUri,
                defaultMetadata,
                variants,
                new PublishingPackage.Scheduling(PublishingPackage.Scheduling.Status.READY, null),
                lesson.confidenceNotes(),
                config.publishingVersion(),
                Instant.now());

        Path publishingDirectory = outputDirectory.resolve("publishing");
        String packageFileReference = writePublishingPackage(publishingPackage, publishingDirectory);

        List<PlatformPublishOutcome> outcomes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (PublishingPackage.PlatformVariant variant : variants) {
            PlatformPublishAdapter adapter = adapterFactory.resolve(variant.platform());
            PlatformPublishOutcome outcome = adapter.publish(publishingPackage, variant.metadata(),
                    publishingDirectory);
            outcomes.add(outcome);
            if (!outcome.success()) {
                errors.add(variant.platform() + ": " + outcome.message());
            }
        }

        PublishingResult.Status status;
        if (errors.isEmpty()) {
            status = PublishingResult.Status.READY;
        } else if (errors.size() == outcomes.size()) {
            status = PublishingResult.Status.FAILED;
        } else {
            status = PublishingResult.Status.PARTIAL_FAILURE;
        }

        return new PublishingResult(UUID.randomUUID().toString(), jobId, videoPackage.topicId(), publishingPackage,
                packageFileReference, List.copyOf(outcomes), status, List.copyOf(errors), Instant.now());
    }

    private List<PublishingPackage.PlatformVariant> buildPlatformVariants(Lesson lesson, Script script,
            PublishingMetadata defaultMetadata) {
        List<PublishingPackage.PlatformVariant> variants = new ArrayList<>();
        for (Script.Platform platform : adapterFactory.supportedPlatforms()) {
            PlatformFormatter formatter = formattersByPlatform.get(platform);
            PublishingMetadata platformMetadata = formatter != null
                    ? formatter.format(lesson, script, defaultMetadata)
                    : defaultMetadata;
            variants.add(new PublishingPackage.PlatformVariant(platform, platformMetadata));
        }
        return List.copyOf(variants);
    }

    private String writePublishingPackage(PublishingPackage publishingPackage, Path publishingDirectory) {
        try {
            Files.createDirectories(publishingDirectory);
            Path file = publishingDirectory.resolve("publishing-package.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), publishingPackage);
            return file.toString();
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write publishing-package.json to " + publishingDirectory, e);
        }
    }
}
