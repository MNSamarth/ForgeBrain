package com.forgebrain.backend.job;

import java.util.List;
import java.util.Optional;

/**
 * Runs reel generation as a tracked, durable job rather than a one-shot function call — the
 * job-aware sibling of {@code com.forgebrain.backend.pipeline.ReelExportService}, which keeps
 * working exactly as before for callers that just want a synchronous export.
 *
 * <p>{@link #submitJob()} never throws for a pipeline/render/packaging failure — it always
 * returns a {@link ReelJob}, with {@link ReelJob.Status#FAILED} and {@code failureReason} set
 * when something went wrong, so a caller inspects the returned job's status instead of needing a
 * try/catch. This is the deliberate contract difference from {@code ReelExportService.exportReel()}
 * (which throws): a job system's normal API is "submit, then inspect the record," not
 * "catch an exception for an expected business-level failure."
 */
public interface ReelJobService {

    ReelJob submitJob();

    Optional<ReelJob> getJob(String jobId);

    List<ReelJob> listJobs();
}
