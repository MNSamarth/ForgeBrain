package com.forgebrain.backend.job;

import java.util.List;
import java.util.Optional;

/**
 * Durable storage for {@link ReelJob} records. {@code create} and {@code update} are the same
 * underlying operation (persist the given snapshot) — kept as two named methods because "create
 * a job" and "update a job's status" are different callers' intents worth reading clearly at the
 * call site, per this mission's Part 4.
 */
public interface ReelJobRepository {

    ReelJob create(ReelJob job);

    ReelJob update(ReelJob job);

    Optional<ReelJob> findById(String jobId);

    List<ReelJob> findAll();
}
