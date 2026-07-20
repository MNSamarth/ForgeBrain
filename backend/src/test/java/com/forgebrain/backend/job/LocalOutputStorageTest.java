package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.JobStorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalOutputStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesTheFileIntoAJobScopedSubdirectoryAndReturnsThatPath() throws IOException {
        Path sourceFile = tempDir.resolve("source").resolve("reel.mp4");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "not a real video, just proving the copy happened");

        Path storageRoot = tempDir.resolve("storage-root");
        LocalOutputStorage storage = new LocalOutputStorage(
                new JobStorageConfig(tempDir.resolve("jobs").toString(), storageRoot.toString()));

        String storedRef = storage.store("job-1", sourceFile);

        Path expected = storageRoot.resolve("job-1").resolve("reel.mp4");
        assertThat(storedRef).isEqualTo(expected.toAbsolutePath().toString());
        assertThat(Files.isRegularFile(expected)).isTrue();
        assertThat(Files.readString(expected)).isEqualTo(Files.readString(sourceFile));
    }

    @Test
    void differentJobsGetIsolatedSubdirectoriesForTheSameFileName() throws IOException {
        Path sourceFile = tempDir.resolve("reel.mp4");
        Files.writeString(sourceFile, "content");
        Path storageRoot = tempDir.resolve("storage-root");
        LocalOutputStorage storage = new LocalOutputStorage(
                new JobStorageConfig(tempDir.resolve("jobs").toString(), storageRoot.toString()));

        String ref1 = storage.store("job-1", sourceFile);
        String ref2 = storage.store("job-2", sourceFile);

        assertThat(ref1).isNotEqualTo(ref2);
        assertThat(Files.isRegularFile(Path.of(ref1))).isTrue();
        assertThat(Files.isRegularFile(Path.of(ref2))).isTrue();
    }
}
