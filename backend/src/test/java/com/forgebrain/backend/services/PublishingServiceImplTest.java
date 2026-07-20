package com.forgebrain.backend.services;

import static com.forgebrain.backend.services.ReviewFixtures.lesson;
import static com.forgebrain.backend.services.ReviewFixtures.publishingConfig;
import static com.forgebrain.backend.services.ReviewFixtures.reviewResult;
import static com.forgebrain.backend.services.ReviewFixtures.script;
import static com.forgebrain.backend.services.ReviewFixtures.videoPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.exceptions.PipelineStageException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PlatformPublishOutcome;
import com.forgebrain.backend.models.PublishingMetadata;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.PublishingResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.publishing.InstagramReelsPublishAdapter;
import com.forgebrain.backend.publishing.PlatformPublishAdapter;
import com.forgebrain.backend.publishing.YouTubeShortsPublishAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link PublishingServiceImpl} — per this mission's Part 7 ("publishing package
 * creation", "reviewer gate enforcement", "failed publish handling", "end-to-end approved reel
 * → publishing package path"). No Spring context, no real platform credentials.
 */
class PublishingServiceImplTest {

    @TempDir
    Path tempDir;

    private final Lesson lesson = lesson(List.of(), "takeaway");
    private final Script script = script("A reasonably sized hook line", "spoken script", "recap",
            ContentStrategy.HookType.MYTH);

    private static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private PublishingServiceImpl serviceWith(List<PlatformPublishAdapter> adapters) throws IOException {
        Path video = tempDir.resolve("reel.mp4");
        Files.writeString(video, "video");
        return new PublishingServiceImpl(publishingConfig(), objectMapper(), adapters, List.of());
    }

    private VideoPackage videoPackageFixture() throws IOException {
        Path video = tempDir.resolve("reel.mp4");
        if (!Files.exists(video)) {
            Files.writeString(video, "video");
        }
        return videoPackage(video.toString(), null, 40.0, 12345, "1080x1920");
    }

    @Test
    void refusesToPublishAReelThatWasNotApproved() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(realAdapter()));
        ReviewResult needsRevision = reviewResult(ReviewResult.Verdict.NEEDS_REVISION);

        assertThatThrownBy(() -> service.publish("job-1", tempDir, needsRevision, videoPackageFixture(), "sub.srt",
                lesson, script))
                .isInstanceOf(PipelineStageException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void buildsAPublishingPackageWithJobIdAndApprovedVerdictCarriedThrough() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(realAdapter()));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        PublishingPackage publishingPackage = result.publishingPackage();
        assertThat(publishingPackage.jobId()).isEqualTo("job-1");
        assertThat(publishingPackage.reviewVerdict()).isEqualTo("APPROVED");
        assertThat(publishingPackage.basedOnReviewId()).isEqualTo(approved.reviewId());
        assertThat(publishingPackage.captionsFileUri()).isEqualTo("sub.srt");
        assertThat(publishingPackage.metadata().title()).isNotBlank();
        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.publishingId()).isNotBlank();
    }

    @Test
    void createsOnePlatformVariantPerRegisteredAdapter() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(
                new YouTubeShortsPublishAdapter(new ObjectMapper()),
                new InstagramReelsPublishAdapter(new ObjectMapper())));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        assertThat(result.publishingPackage().platformVariants()).extracting(PublishingPackage.PlatformVariant::platform)
                .containsExactlyInAnyOrder(Script.Platform.YOUTUBE_SHORTS, Script.Platform.INSTAGRAM_REELS);
    }

    @Test
    void writesThePublishingPackageJsonFileToAPublishingSubdirectoryOfTheOutputDirectory() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(realAdapter()));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        assertThat(result.packageFileReference()).isNotBlank();
        Path packageFile = Path.of(result.packageFileReference());
        assertThat(Files.isRegularFile(packageFile)).isTrue();
        assertThat(packageFile.getParent().getFileName().toString()).isEqualTo("publishing");
        assertThat(Files.readString(packageFile)).contains("job-1").contains("APPROVED");
    }

    @Test
    void endToEndApprovedReelProducesAReadyResultWithBothPlatformOutcomes() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(
                new YouTubeShortsPublishAdapter(new ObjectMapper()),
                new InstagramReelsPublishAdapter(new ObjectMapper())));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        assertThat(result.status()).isEqualTo(PublishingResult.Status.READY);
        assertThat(result.errors()).isEmpty();
        assertThat(result.platformOutcomes()).hasSize(2);
        assertThat(result.platformOutcomes()).allMatch(PlatformPublishOutcome::success);
        result.platformOutcomes().forEach(
                outcome -> assertThat(Files.isRegularFile(Path.of(outcome.payloadReference()))).isTrue());
    }

    @Test
    void reportsPartialFailureWhenOnlySomeAdaptersFail() throws IOException {
        PublishingServiceImpl service = serviceWith(
                List.of(realAdapter(), failingAdapter(Script.Platform.INSTAGRAM_REELS)));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        assertThat(result.status()).isEqualTo(PublishingResult.Status.PARTIAL_FAILURE);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("INSTAGRAM_REELS");
    }

    @Test
    void reportsFailedWhenEveryAdapterFails() throws IOException {
        PublishingServiceImpl service = serviceWith(List.of(failingAdapter(Script.Platform.YOUTUBE_SHORTS)));
        ReviewResult approved = reviewResult(ReviewResult.Verdict.APPROVED);

        PublishingResult result = service.publish("job-1", tempDir, approved, videoPackageFixture(), "sub.srt",
                lesson, script);

        assertThat(result.status()).isEqualTo(PublishingResult.Status.FAILED);
        assertThat(result.errors()).hasSize(1);
    }

    private static PlatformPublishAdapter realAdapter() {
        return new YouTubeShortsPublishAdapter(new ObjectMapper());
    }

    private static PlatformPublishAdapter failingAdapter(Script.Platform platform) {
        return new PlatformPublishAdapter() {
            @Override
            public Script.Platform supportedPlatform() {
                return platform;
            }

            @Override
            public PlatformPublishOutcome publish(PublishingPackage publishingPackage,
                    PublishingMetadata platformMetadata, Path payloadDirectory) {
                return new PlatformPublishOutcome(platform, true, false, null, "Simulated adapter failure.",
                        Instant.now());
            }
        };
    }
}
