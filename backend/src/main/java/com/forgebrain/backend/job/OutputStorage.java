package com.forgebrain.backend.job;

import java.nio.file.Path;

/**
 * The seam a packaged reel's output files pass through on their way to durable storage. Exactly
 * one implementation is active at a time, chosen by {@link OutputStorageFactory}: {@link
 * LocalOutputStorage} (the default — copies into a local directory tree keyed by job id) or
 * {@link CloudStorageOutputStorage} (uploads to a configured Cloud Storage bucket and returns a
 * {@code gs://} URI instead of a local path), per {@code forgebrain.cloud-storage.enabled}.
 * {@link OutputPackagingService} and every other caller depend only on this interface, so
 * switching backends never touches them — both implementations return the same thing: an
 * opaque, stable string reference to where a file now durably lives.
 */
public interface OutputStorage {

    /**
     * Persists one local file as part of a job's output package.
     *
     * @return a stable reference to the stored file — a local path from {@link
     * LocalOutputStorage}, a {@code gs://} URI from {@link CloudStorageOutputStorage}
     */
    String store(String jobId, Path localFile);
}
