package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Real Instagram Reels adapter: the Instagram Graph API's documented three-step publish flow —
 * create a media container from a publicly reachable video URL, poll it until Instagram finishes
 * processing the video, then publish the container. Registered as a plain class (not {@code
 * @Component}) and constructed by {@link PlatformPublishAdapterBeanConfig} — see {@link
 * InstagramReelsPublishAdapter}'s javadoc for why.
 *
 * <p><b>The Graph API requires a video URL it can fetch itself</b> — not a raw file upload. If
 * {@code publishingPackage.videoFileUri()} isn't an {@code http(s)://} URL (i.e. local storage is
 * in use, not Cloud Storage), this reports a clear failed outcome rather than attempting a call
 * that could never succeed. This class has never been exercised against the live API (this
 * project has no real Instagram credentials) — treat it as a well-reasoned first cut, not a
 * verified integration, until it's been run once against a real account.
 *
 * <p>Never throws from {@link #publish}: missing configuration, a non-public video URL, or any
 * HTTP/parsing/status failure is caught and reported as a failed {@link PlatformPublishOutcome},
 * exactly like {@link AbstractDryRunPlatformPublishAdapter}.
 */
public class InstagramRealPublishAdapter implements PlatformPublishAdapter {

    private static final Logger log = LoggerFactory.getLogger(InstagramRealPublishAdapter.class);
    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private final PlatformUploadConfig.Instagram config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final LongConsumer sleeper;

    public InstagramRealPublishAdapter(PlatformUploadConfig.Instagram config, RestClient restClient,
            ObjectMapper objectMapper) {
        this(config, restClient, objectMapper, InstagramRealPublishAdapter::sleep);
    }

    /** Package-private constructor so tests can inject a no-op sleeper and poll instantly. */
    InstagramRealPublishAdapter(PlatformUploadConfig.Instagram config, RestClient restClient,
            ObjectMapper objectMapper, LongConsumer sleeper) {
        this.config = config;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
    }

    @Override
    public Script.Platform supportedPlatform() {
        return Script.Platform.INSTAGRAM_REELS;
    }

    @Override
    public PlatformPublishOutcome publish(PublishingPackage publishingPackage, PublishingMetadata platformMetadata,
            Path payloadDirectory) {
        List<String> missing = missingConfiguration();
        if (!missing.isEmpty()) {
            return failure("Instagram real upload is enabled but missing configuration: "
                    + String.join(", ", missing) + ".");
        }

        String videoUrl = publishingPackage.videoFileUri();
        if (!isPubliclyReachable(videoUrl)) {
            return failure("video_file_uri is not a publicly reachable URL (found '" + videoUrl + "'); the"
                    + " Instagram Graph API requires an http(s):// video URL it can fetch directly. Enable"
                    + " Cloud Storage output (forgebrain.cloud-storage.enabled) so the reel has a public URL"
                    + " before enabling Instagram real upload.");
        }

        try {
            String caption = buildCaption(platformMetadata);
            String creationId = createMediaContainer(videoUrl, caption);
            waitUntilReady(creationId);
            String mediaId = publishMedia(creationId);
            return new PlatformPublishOutcome(supportedPlatform(), false, true, mediaId,
                    "Published to Instagram as media id '" + mediaId + "'.", Instant.now());
        } catch (Exception e) {
            log.warn("Instagram upload failed for job '{}': {}", publishingPackage.jobId(), e.getMessage(), e);
            return failure("Instagram upload failed: " + e.getMessage());
        }
    }

    private List<String> missingConfiguration() {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.accessToken())) {
            missing.add("access-token");
        }
        if (isBlank(config.igUserId())) {
            missing.add("ig-user-id");
        }
        return missing;
    }

    private static boolean isPubliclyReachable(String uri) {
        return uri != null && (uri.startsWith("https://") || uri.startsWith("http://"));
    }

    private String createMediaContainer(String videoUrl, String caption) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_type", "REELS");
        form.add("video_url", videoUrl);
        form.add("caption", caption);
        form.add("access_token", config.accessToken());

        String response = restClient.post()
                .uri(GRAPH_API_BASE + "/" + config.igUserId() + "/media")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return extractField(response, "id", "Instagram media creation response");
    }

    private void waitUntilReady(String creationId) {
        int maxAttempts = Math.max(1, config.publishPollMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String statusCode = fetchStatus(creationId);
            if ("FINISHED".equals(statusCode)) {
                return;
            }
            if ("ERROR".equals(statusCode) || "EXPIRED".equals(statusCode)) {
                throw new IllegalStateException(
                        "Instagram reported container status '" + statusCode + "' for creation id '" + creationId
                                + "'.");
            }
            if (attempt < maxAttempts) {
                sleeper.accept(config.publishPollIntervalSeconds() * 1000L);
            }
        }
        throw new IllegalStateException("Instagram media container '" + creationId + "' was not ready after "
                + maxAttempts + " status check(s).");
    }

    private String fetchStatus(String creationId) {
        String uri = UriComponentsBuilder.fromHttpUrl(GRAPH_API_BASE + "/" + creationId)
                .queryParam("fields", "status_code")
                .queryParam("access_token", config.accessToken())
                .toUriString();
        String response = restClient.get().uri(uri).retrieve().body(String.class);
        return extractField(response, "status_code", "Instagram container status response");
    }

    private String publishMedia(String creationId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", creationId);
        form.add("access_token", config.accessToken());

        String response = restClient.post()
                .uri(GRAPH_API_BASE + "/" + config.igUserId() + "/media_publish")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return extractField(response, "id", "Instagram publish response");
    }

    private static String buildCaption(PublishingMetadata metadata) {
        StringBuilder caption = new StringBuilder(metadata.title());
        if (metadata.description() != null && !metadata.description().isBlank()) {
            caption.append("\n\n").append(metadata.description());
        }
        if (metadata.hashtags() != null && !metadata.hashtags().isEmpty()) {
            caption.append("\n\n").append(String.join(" ", metadata.hashtags()));
        }
        return caption.toString();
    }

    private String extractField(String rawResponse, String fieldName, String responseDescription) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse " + responseDescription + ": " + rawResponse, e);
        }
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(responseDescription + " had no '" + fieldName + "': " + rawResponse);
        }
        return value;
    }

    private PlatformPublishOutcome failure(String message) {
        return new PlatformPublishOutcome(supportedPlatform(), false, false, null, message, Instant.now());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
