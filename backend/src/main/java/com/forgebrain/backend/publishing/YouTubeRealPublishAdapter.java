package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Real YouTube Shorts adapter: exchanges the configured OAuth 2.0 refresh token for a short-lived
 * access token, then uploads the reel's MP4 (plus title/description/tags/privacy) to the YouTube
 * Data API v3 {@code videos.insert} endpoint via a multipart request. Registered as a plain
 * class (not {@code @Component}) and constructed by {@link PlatformPublishAdapterBeanConfig} — see
 * {@link YouTubeShortsPublishAdapter}'s javadoc for why.
 *
 * <p>Uses a {@code multipart/form-data} body (JSON metadata part + video part) rather than the
 * {@code multipart/related} framing the Data API docs describe for very large resumable uploads —
 * this is the minimal upload flow appropriate for a short-form video well under the API's simple-
 * upload size ceiling, not the chunked/resumable protocol meant for multi-gigabyte files. This
 * class has never been exercised against the live API (this project has no real YouTube
 * credentials) — treat the exact request framing as a well-reasoned first cut, not a verified
 * integration, until it's been run once against a real channel.
 *
 * <p>Never throws from {@link #publish}: missing configuration, a missing video file, or any HTTP/
 * parsing failure is caught and reported as a failed {@link PlatformPublishOutcome}, exactly like
 * {@link AbstractDryRunPlatformPublishAdapter} — {@code PublishingServiceImpl} doesn't need to
 * know which kind of adapter it just called.
 */
public class YouTubeRealPublishAdapter implements PlatformPublishAdapter {

    private static final Logger log = LoggerFactory.getLogger(YouTubeRealPublishAdapter.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String UPLOAD_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=multipart&part=snippet,status";
    private static final String DEFAULT_PRIVACY_STATUS = "private";

    private final PlatformUploadConfig.YouTube config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public YouTubeRealPublishAdapter(PlatformUploadConfig.YouTube config, RestClient restClient,
            ObjectMapper objectMapper) {
        this.config = config;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Script.Platform supportedPlatform() {
        return Script.Platform.YOUTUBE_SHORTS;
    }

    @Override
    public PlatformPublishOutcome publish(PublishingPackage publishingPackage, PublishingMetadata platformMetadata,
            Path payloadDirectory) {
        List<String> missing = missingConfiguration();
        if (!missing.isEmpty()) {
            return failure("YouTube real upload is enabled but missing configuration: "
                    + String.join(", ", missing) + ".");
        }

        Path videoFile = Path.of(publishingPackage.videoFileUri());
        if (!Files.isRegularFile(videoFile)) {
            return failure("Video file not found at '" + videoFile + "'; cannot upload to YouTube.");
        }

        try {
            String accessToken = fetchAccessToken();
            String videoId = uploadVideo(accessToken, videoFile, platformMetadata);
            String privacyStatus = effectivePrivacyStatus();
            return new PlatformPublishOutcome(supportedPlatform(), false, true, videoId,
                    "Uploaded to YouTube as video id '" + videoId + "' (privacy_status=" + privacyStatus + ").",
                    Instant.now());
        } catch (Exception e) {
            log.warn("YouTube upload failed for job '{}': {}", publishingPackage.jobId(), e.getMessage(), e);
            return failure("YouTube upload failed: " + e.getMessage());
        }
    }

    private List<String> missingConfiguration() {
        List<String> missing = new ArrayList<>();
        if (isBlank(config.clientId())) {
            missing.add("client-id");
        }
        if (isBlank(config.clientSecret())) {
            missing.add("client-secret");
        }
        if (isBlank(config.refreshToken())) {
            missing.add("refresh-token");
        }
        return missing;
    }

    private String fetchAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("refresh_token", config.refreshToken());
        form.add("grant_type", "refresh_token");

        String response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return extractField(response, "access_token", "OAuth token response");
    }

    private String uploadVideo(String accessToken, Path videoFile, PublishingMetadata metadata) {
        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("title", metadata.title());
        snippet.put("description", metadata.description());
        snippet.put("tags", metadata.tags());
        snippet.put("categoryId", config.categoryId());

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("privacyStatus", effectivePrivacyStatus());
        status.put("selfDeclaredMadeForKids", false);

        Map<String, Object> videoMetadata = new LinkedHashMap<>();
        videoMetadata.put("snippet", snippet);
        videoMetadata.put("status", status);

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("metadata", videoMetadata, MediaType.APPLICATION_JSON);
        body.part("video", new FileSystemResource(videoFile), MediaType.valueOf("video/mp4"));

        String response = restClient.post()
                .uri(UPLOAD_URL)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(String.class);
        return extractField(response, "id", "YouTube upload response");
    }

    private String effectivePrivacyStatus() {
        return isBlank(config.privacyStatus()) ? DEFAULT_PRIVACY_STATUS : config.privacyStatus();
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
}
