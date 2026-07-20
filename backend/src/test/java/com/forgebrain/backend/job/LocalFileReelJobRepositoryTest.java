package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.JobStorageConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileReelJobRepositoryTest {

    @TempDir
    Path tempDir;

    private LocalFileReelJobRepository repository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        repository = new LocalFileReelJobRepository(objectMapper,
                new JobStorageConfig(tempDir.resolve("jobs").toString(), tempDir.resolve("output").toString()));
    }

    @Test
    void findByIdReturnsEmptyWhenNoJobHasBeenCreated() {
        assertThat(repository.findById("does-not-exist")).isEmpty();
    }

    @Test
    void createPersistsAJobRetrievableById() {
        ReelJob job = ReelJob.queued("job-1", "run-1");

        repository.create(job);

        Optional<ReelJob> loaded = repository.findById("job-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().jobId()).isEqualTo("job-1");
        assertThat(loaded.get().status()).isEqualTo(ReelJob.Status.QUEUED);
    }

    @Test
    void updateOverwritesThePreviouslyPersistedSnapshot() {
        ReelJob job = ReelJob.queued("job-1", "run-1");
        repository.create(job);

        repository.update(job.running().withTopic("java-for-loop", "The For Loop"));

        ReelJob loaded = repository.findById("job-1").orElseThrow();
        assertThat(loaded.status()).isEqualTo(ReelJob.Status.RUNNING);
        assertThat(loaded.topicId()).isEqualTo("java-for-loop");
    }

    @Test
    void findAllListsEveryPersistedJob() {
        repository.create(ReelJob.queued("job-1", "run-1"));
        repository.create(ReelJob.queued("job-2", "run-2"));

        List<ReelJob> all = repository.findAll();

        assertThat(all).extracting(ReelJob::jobId).containsExactlyInAnyOrder("job-1", "job-2");
    }

    @Test
    void findAllReturnsEmptyListWhenTheJobsDirectoryDoesNotExistYet() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void roundTripsOutputFilesAndWarningsCorrectly() {
        ReelJob job = ReelJob.queued("job-1", "run-1")
                .withOutputFiles(Map.of("video", "/out/reel.mp4"))
                .withWarning("VOICE: silent placeholder audio track used.");
        repository.create(job);

        ReelJob loaded = repository.findById("job-1").orElseThrow();

        assertThat(loaded.outputFiles()).containsEntry("video", "/out/reel.mp4");
        assertThat(loaded.warnings()).containsExactly("VOICE: silent placeholder audio track used.");
    }
}
