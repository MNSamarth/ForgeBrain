package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.JobStorageConfig;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.VideoPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputPackagingServiceImplTest {

    @TempDir
    Path tempDir;

    private OutputPackagingServiceImpl packagingService;
    private Path renderDirectory;
    private Path videoFile;
    private Path thumbnailFile;
    private Path subtitleFile;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        LocalOutputStorage storage = new LocalOutputStorage(
                new JobStorageConfig(tempDir.resolve("jobs").toString(), tempDir.resolve("storage-root").toString()));
        packagingService = new OutputPackagingServiceImpl(objectMapper, storage);

        renderDirectory = tempDir.resolve("render");
        Files.createDirectories(renderDirectory);
        videoFile = renderDirectory.resolve("reel.mp4");
        thumbnailFile = renderDirectory.resolve("thumbnail.jpg");
        subtitleFile = renderDirectory.resolve("subtitles.srt");
        Files.writeString(videoFile, "not a real video");
        Files.writeString(thumbnailFile, "not a real image");
        Files.writeString(subtitleFile, "1\n00:00:00,000 --> 00:00:02,000\nHello.\n\n");
    }

    private VideoPackage videoPackage() {
        return new VideoPackage("pkg-1", null, "java-for-loop", "The For Loop",
                videoFile.toAbsolutePath().toString(), thumbnailFile.toAbsolutePath().toString(), 40.0,
                "1080x1920", Storyboard.AspectRatio.RATIO_9_16, VideoPackage.VideoCodec.H264,
                VideoPackage.AudioCodec.AAC, 12345L, "checksum123", Instant.now());
    }

    @Test
    void writesMetadataJsonAndStoresEveryFileThroughOutputStorage() throws IOException {
        ReelOutputPackage result = packagingService.packageOutputs("job-1", renderDirectory, videoPackage(), subtitleFile);

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.files()).containsKeys("video", "thumbnail", "subtitles", "metadata");

        assertThat(Files.isRegularFile(renderDirectory.resolve("metadata.json"))).isTrue();

        // Every stored reference points into the job-scoped storage root, not the raw render dir.
        result.files().values().forEach(ref -> assertThat(ref).contains("storage-root").contains("job-1"));
        result.files().values().forEach(ref -> assertThat(Files.isRegularFile(Path.of(ref))).isTrue());

        assertThat(result.videoRef()).isEqualTo(result.files().get("video"));
        assertThat(result.metadataRef()).isEqualTo(result.files().get("metadata"));
    }

    @Test
    void omitsSubtitlesKeyWhenNoSubtitleFileExists() {
        Path missingSubtitleFile = renderDirectory.resolve("does-not-exist.srt");

        ReelOutputPackage result = packagingService.packageOutputs("job-1", renderDirectory, videoPackage(),
                missingSubtitleFile);

        assertThat(result.files()).doesNotContainKey("subtitles");
        assertThat(result.files()).containsKeys("video", "thumbnail", "metadata");
    }

    @Test
    void storeReportStoresTheGivenFileThroughOutputStorageAndReturnsItsReference() throws IOException {
        Path reportFile = renderDirectory.resolve("report.json");
        Files.writeString(reportFile, "{}");

        String ref = packagingService.storeReport("job-1", reportFile);

        assertThat(Files.isRegularFile(Path.of(ref))).isTrue();
        assertThat(ref).contains("job-1");
    }
}
