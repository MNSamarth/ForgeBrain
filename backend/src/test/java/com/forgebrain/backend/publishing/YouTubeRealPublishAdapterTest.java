package com.forgebrain.backend.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link YouTubeRealPublishAdapter} — per this mission's Part 6 ("missing
 * credentials failure handling", "successful adapter invocation with mocks", "publish status
 * recording"). Vertex/network calls are stubbed via {@link MockRestServiceServer}; no real
 * platform credentials, no real network call.
 */
class YouTubeRealPublishAdapterTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String UPLOAD_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=multipart&part=snippet,status";

    @TempDir
    Path tempDir;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
    }

    private YouTubeRealPublishAdapter adapter(PlatformUploadConfig.YouTube config) {
        return new YouTubeRealPublishAdapter(config, restClientBuilder.build(), objectMapper);
    }

    private static PlatformUploadConfig.YouTube config(String clientId, String clientSecret, String refreshToken) {
        return new PlatformUploadConfig.YouTube(true, clientId, clientSecret, refreshToken, "channel-1", "unlisted",
                "27");
    }

    private static PublishingPackage samplePackage(String videoFileUri) {
        PublishingMetadata metadata = new PublishingMetadata("A Great Title", "A great description.",
                List.of("java", "coding"), List.of("#java", "#coding"), "Education", "en-US");
        return new PublishingPackage("pkg-1", "job-1", "topic-1", "The Topic", "review-1", "APPROVED", "video-pkg-1",
                videoFileUri, "thumb.jpg", "subtitles.srt", metadata, List.of(),
                new PublishingPackage.Scheduling(PublishingPackage.Scheduling.Status.READY, null), null, "1.0.0",
                Instant.now());
    }

    @Test
    void reportsAClearFailureWhenCredentialsAreMissing() {
        YouTubeRealPublishAdapter adapter = adapter(config("", "", ""));
        PublishingPackage publishingPackage = samplePackage("irrelevant.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.dryRun()).isFalse();
        assertThat(outcome.message()).contains("client-id").contains("client-secret").contains("refresh-token");
    }

    @Test
    void reportsAClearFailureWhenTheVideoFileDoesNotExist() {
        YouTubeRealPublishAdapter adapter = adapter(config("id", "secret", "token"));
        PublishingPackage publishingPackage = samplePackage(tempDir.resolve("missing.mp4").toString());

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("Video file not found");
    }

    @Test
    void successfullyUploadsAndReportsTheVideoIdAsANonDryRunOutcome() throws IOException {
        Path video = tempDir.resolve("reel.mp4");
        Files.writeString(video, "fake video bytes");

        mockServer.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"access-123\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(UPLOAD_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-123"))
                .andRespond(withSuccess("{\"id\":\"yt-video-1\"}", MediaType.APPLICATION_JSON));

        YouTubeRealPublishAdapter adapter = adapter(config("id", "secret", "token"));
        PublishingPackage publishingPackage = samplePackage(video.toString());

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.dryRun()).isFalse();
        assertThat(outcome.payloadReference()).isEqualTo("yt-video-1");
        assertThat(outcome.message()).contains("yt-video-1").contains("unlisted");
        mockServer.verify();
    }

    @Test
    void reportsAClearFailureWhenTheUploadCallReturnsAnErrorStatus() throws IOException {
        Path video = tempDir.resolve("reel.mp4");
        Files.writeString(video, "fake video bytes");

        mockServer.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"access-123\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(UPLOAD_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":\"quota exceeded\"}"));

        YouTubeRealPublishAdapter adapter = adapter(config("id", "secret", "token"));
        PublishingPackage publishingPackage = samplePackage(video.toString());

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("YouTube upload failed");
    }

    @Test
    void reportsAClearFailureWhenTheTokenResponseHasNoAccessToken() throws IOException {
        mockServer.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        YouTubeRealPublishAdapter adapter = adapter(config("id", "secret", "token"));
        Path video = tempDir.resolve("reel.mp4");
        Files.writeString(video, "fake video bytes");
        PublishingPackage publishingPackage = samplePackage(video.toString());

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("YouTube upload failed");
    }
}
