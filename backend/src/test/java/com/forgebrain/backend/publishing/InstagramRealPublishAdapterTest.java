package com.forgebrain.backend.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link InstagramRealPublishAdapter} — per this mission's Part 6 ("missing
 * credentials failure handling", "successful adapter invocation with mocks", "publish status
 * recording"). Network calls are stubbed via {@link MockRestServiceServer}; no real platform
 * credentials, no real network call, no real sleeping (a no-op sleeper is injected).
 */
class InstagramRealPublishAdapterTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
    }

    private InstagramRealPublishAdapter adapter(PlatformUploadConfig.Instagram config) {
        return new InstagramRealPublishAdapter(config, restClientBuilder.build(), objectMapper, sleeps::add);
    }

    private static PlatformUploadConfig.Instagram config(String accessToken, String igUserId) {
        return new PlatformUploadConfig.Instagram(true, accessToken, igUserId, 5, 3);
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
        InstagramRealPublishAdapter adapter = adapter(config("", ""));
        PublishingPackage publishingPackage = samplePackage("https://cdn.example.com/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.dryRun()).isFalse();
        assertThat(outcome.message()).contains("access-token").contains("ig-user-id");
    }

    @Test
    void reportsAClearFailureWhenTheVideoFileUriIsALocalPathNotAPublicUrl() {
        InstagramRealPublishAdapter adapter = adapter(config("token", "ig-user-1"));
        PublishingPackage publishingPackage = samplePackage("/data/renders/job-1/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("publicly reachable URL");
    }

    @Test
    void successfullyPublishesAfterTheContainerFinishesProcessingOnTheFirstPoll() {
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media_publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"media-1\"}", MediaType.APPLICATION_JSON));

        InstagramRealPublishAdapter adapter = adapter(config("token", "ig-user-1"));
        PublishingPackage publishingPackage = samplePackage("https://cdn.example.com/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.dryRun()).isFalse();
        assertThat(outcome.payloadReference()).isEqualTo("media-1");
        assertThat(sleeps).isEmpty();
        mockServer.verify();
    }

    @Test
    void pollsUntilTheContainerFinishesThenPublishes() {
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media"))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status_code\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media_publish"))
                .andRespond(withSuccess("{\"id\":\"media-1\"}", MediaType.APPLICATION_JSON));

        InstagramRealPublishAdapter adapter = adapter(config("token", "ig-user-1"));
        PublishingPackage publishingPackage = samplePackage("https://cdn.example.com/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isTrue();
        assertThat(sleeps).containsExactly(5000L);
        mockServer.verify();
    }

    @Test
    void reportsAClearFailureWhenInstagramReportsAnErrorStatus() {
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media"))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status_code\":\"ERROR\"}", MediaType.APPLICATION_JSON));

        InstagramRealPublishAdapter adapter = adapter(config("token", "ig-user-1"));
        PublishingPackage publishingPackage = samplePackage("https://cdn.example.com/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("Instagram upload failed");
        mockServer.verify();
    }

    @Test
    void reportsAClearFailureWhenTheContainerNeverFinishesWithinTheConfiguredAttempts() {
        mockServer.expect(requestToUriTemplate("https://graph.facebook.com/v19.0/ig-user-1/media"))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 3; i++) {
            mockServer.expect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"status_code\":\"IN_PROGRESS\"}", MediaType.APPLICATION_JSON));
        }

        InstagramRealPublishAdapter adapter = adapter(config("token", "ig-user-1"));
        PublishingPackage publishingPackage = samplePackage("https://cdn.example.com/reel.mp4");

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), null);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).contains("not ready");
        mockServer.verify();
    }
}
