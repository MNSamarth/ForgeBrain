package com.forgebrain.backend.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.Script;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link AbstractDryRunPlatformPublishAdapter} through its concrete {@link
 * YouTubeShortsPublishAdapter} subclass — per this mission's Part 6/7 ("dry-run adapter
 * behavior", "platform payload formatting"). No network call, no credentials.
 */
class YouTubeShortsPublishAdapterTest {

    private final YouTubeShortsPublishAdapter adapter = new YouTubeShortsPublishAdapter(new ObjectMapper());

    @TempDir
    Path tempDir;

    private static PublishingPackage samplePackage() {
        PublishingMetadata metadata = new PublishingMetadata("A Great Title", "A great description.",
                List.of("java", "coding"), List.of("#java", "#coding"), "Education", "en-US");
        return new PublishingPackage("pkg-1", "job-1", "topic-1", "The Topic", "review-1", "APPROVED", "video-pkg-1",
                "reel.mp4", "thumb.jpg", "subtitles.srt", metadata, List.of(),
                new PublishingPackage.Scheduling(PublishingPackage.Scheduling.Status.READY, null), null, "1.0.0",
                Instant.now());
    }

    @Test
    void supportsYouTubeShorts() {
        assertThat(adapter.supportedPlatform()).isEqualTo(Script.Platform.YOUTUBE_SHORTS);
    }

    @Test
    void writesADryRunPayloadFileAndReportsSuccess() {
        PublishingPackage publishingPackage = samplePackage();

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.platform()).isEqualTo(Script.Platform.YOUTUBE_SHORTS);
        assertThat(outcome.dryRun()).isTrue();
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.payloadReference()).isNotBlank();
        assertThat(Files.isRegularFile(Path.of(outcome.payloadReference()))).isTrue();
        assertThat(outcome.payloadReference()).endsWith("youtube-shorts-payload.json");
    }

    @Test
    void thePayloadFileContainsTheExactPlatformMetadataAndFileReferences() throws IOException {
        PublishingPackage publishingPackage = samplePackage();
        ObjectMapper reader = new ObjectMapper();

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        JsonNode payload = reader.readTree(Path.of(outcome.payloadReference()).toFile());
        assertThat(payload.get("platform").asText()).isEqualTo("YOUTUBE_SHORTS");
        assertThat(payload.get("dry_run").asBoolean()).isTrue();
        assertThat(payload.get("title").asText()).isEqualTo("A Great Title");
        assertThat(payload.get("description").asText()).isEqualTo("A great description.");
        assertThat(payload.get("video_file_uri").asText()).isEqualTo("reel.mp4");
        assertThat(payload.get("thumbnail_uri").asText()).isEqualTo("thumb.jpg");
        assertThat(payload.get("captions_file_uri").asText()).isEqualTo("subtitles.srt");
        assertThat(payload.get("hashtags")).hasSize(2);
    }

    @Test
    void neverActuallyUploadsAnything() {
        // No network client is constructed or invoked anywhere in this adapter — the only I/O
        // is the local file write asserted above, which is the entire point of a dry-run
        // adapter: this test documents that fact rather than mocking a client that doesn't exist.
        PublishingPackage publishingPackage = samplePackage();

        PlatformPublishOutcome outcome = adapter.publish(publishingPackage, publishingPackage.metadata(), tempDir);

        assertThat(outcome.dryRun()).isTrue();
        assertThat(outcome.message()).contains("no real upload was performed");
    }
}
