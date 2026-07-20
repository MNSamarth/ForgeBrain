package com.forgebrain.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forgebrain.backend.config.CloudStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * No real GCP project or credentials are used anywhere in this class — {@link Storage} is always
 * a Mockito mock, per this mission's "do not require a GCP project or credentials for tests".
 */
class CloudStorageOutputStorageTest {

    private static CloudStorageConfig config(String mediaBucket, String outputPrefix) {
        return new CloudStorageConfig(true, mediaBucket, "", "", outputPrefix, "");
    }

    @Test
    void uploadsTheFileToTheConfiguredBucketAndPrefixAndReturnsAGsUri() throws IOException {
        Storage storage = mock(Storage.class);
        CloudStorageOutputStorage cloudStorage = new CloudStorageOutputStorage(storage, config("my-bucket", "reels"));
        Path localFile = Path.of("some", "working", "dir", "reel.mp4");

        String ref = cloudStorage.store("job-1", localFile);

        assertThat(ref).isEqualTo("gs://my-bucket/reels/job-1/reel.mp4");
        BlobId expectedBlobId = BlobId.of("my-bucket", "reels/job-1/reel.mp4");
        verify(storage).createFrom(eq(BlobInfo.newBuilder(expectedBlobId).build()), eq(localFile));
    }

    @Test
    void normalizesAnOutputPrefixWithLeadingAndTrailingSlashes() throws IOException {
        Storage storage = mock(Storage.class);
        CloudStorageOutputStorage cloudStorage = new CloudStorageOutputStorage(storage, config("my-bucket", "/reels/"));

        String ref = cloudStorage.store("job-1", Path.of("reel.mp4"));

        assertThat(ref).isEqualTo("gs://my-bucket/reels/job-1/reel.mp4");
    }

    @Test
    void omitsThePrefixSegmentEntirelyWhenBlank() throws IOException {
        Storage storage = mock(Storage.class);
        CloudStorageOutputStorage cloudStorage = new CloudStorageOutputStorage(storage, config("my-bucket", ""));

        String ref = cloudStorage.store("job-1", Path.of("reel.mp4"));

        assertThat(ref).isEqualTo("gs://my-bucket/job-1/reel.mp4");
    }

    @Test
    void wrapsAnIoExceptionFromTheUploadInAConfigurationException() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.createFrom(any(BlobInfo.class), any(Path.class))).thenThrow(new IOException("network down"));
        CloudStorageOutputStorage cloudStorage = new CloudStorageOutputStorage(storage, config("my-bucket", "reels"));

        assertThatThrownBy(() -> cloudStorage.store("job-1", Path.of("reel.mp4")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("job-1")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsAStorageExceptionFromTheUploadInAConfigurationException() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.createFrom(any(BlobInfo.class), any(Path.class)))
                .thenThrow(new StorageException(403, "permission denied"));
        CloudStorageOutputStorage cloudStorage = new CloudStorageOutputStorage(storage, config("my-bucket", "reels"));

        assertThatThrownBy(() -> cloudStorage.store("job-1", Path.of("reel.mp4")))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("permission denied")
                .hasCauseInstanceOf(StorageException.class);
    }
}
