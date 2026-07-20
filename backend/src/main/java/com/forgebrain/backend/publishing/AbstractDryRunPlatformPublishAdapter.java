package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared dry-run behavior for every {@link PlatformPublishAdapter} registered today: write the
 * exact payload a real upload call would send to {@code <payloadDirectory>/<platform>-payload
 * .json}, and report success — no network call, no credentials, nothing that could fail outside
 * a full disk. This is this mission's Part 3.4 ("implement a safe dry-run adapter that writes the
 * exact payloads instead of uploading"), shared once so {@code YouTubeShortsPublishAdapter} and
 * {@code InstagramReelsPublishAdapter} don't each reimplement it — only {@link
 * #supportedPlatform()} differs between them.
 */
public abstract class AbstractDryRunPlatformPublishAdapter implements PlatformPublishAdapter {

    private final ObjectMapper objectMapper;

    protected AbstractDryRunPlatformPublishAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PlatformPublishOutcome publish(PublishingPackage publishingPackage, PublishingMetadata platformMetadata,
            Path payloadDirectory) {
        Script.Platform platform = supportedPlatform();
        try {
            Files.createDirectories(payloadDirectory);
            Path payloadFile = payloadDirectory.resolve(fileNameFor(platform) + "-payload.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(payloadFile.toFile(),
                    buildPayload(publishingPackage, platformMetadata, platform));
            return new PlatformPublishOutcome(platform, true, true, payloadFile.toString(),
                    "Dry-run payload written; no real upload was performed.", Instant.now());
        } catch (IOException e) {
            return new PlatformPublishOutcome(platform, true, false, null,
                    "Failed to write dry-run payload: " + e.getMessage(), Instant.now());
        }
    }

    private static Map<String, Object> buildPayload(PublishingPackage publishingPackage,
            PublishingMetadata platformMetadata, Script.Platform platform) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("platform", platform.name());
        payload.put("dry_run", true);
        payload.put("job_id", publishingPackage.jobId());
        payload.put("topic_id", publishingPackage.topicId());
        payload.put("title", platformMetadata.title());
        payload.put("description", platformMetadata.description());
        payload.put("tags", platformMetadata.tags());
        payload.put("hashtags", platformMetadata.hashtags());
        payload.put("category", platformMetadata.category());
        payload.put("language_code", platformMetadata.languageCode());
        payload.put("video_file_uri", publishingPackage.videoFileUri());
        payload.put("thumbnail_uri", publishingPackage.thumbnailUri());
        payload.put("captions_file_uri", publishingPackage.captionsFileUri());
        return payload;
    }

    private static String fileNameFor(Script.Platform platform) {
        return platform.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
