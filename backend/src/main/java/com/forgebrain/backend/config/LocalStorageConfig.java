package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File paths for this phase's local, file-based persistence — the "simplest durable approach"
 * for the first executable pipeline slice (see {@code NEXT_EXECUTION.md}). Bound from
 * {@code forgebrain.local-storage.*} in application.yml.
 *
 * <p>These are a deliberate, temporary choice: real deployments will read the curriculum from
 * a bundled resource or object storage and persist memory/pipeline output to Firestore/Cloud
 * Storage (see {@link FirestoreConfig}, {@link CloudStorageConfig}, and
 * docs/CONFIGURATION.md). Keeping the paths here, rather than hard-coded in the services that
 * use them, means swapping the storage mechanism later only touches this class and the
 * repository implementations, not any pipeline logic.
 *
 * @param curriculumRoadmapPath path to {@code curriculum/java-roadmap.json}, relative to the
 *                              application's working directory. Defaults to the repository
 *                              layout when run via {@code ./mvnw} from {@code backend/}.
 * @param memoryStatePath      path to the persisted memory state JSON file. Created with a
 *                             fresh, empty state on first run if it doesn't exist.
 * @param pipelineOutputDirectory directory where completed {@code PipelineResult}s are saved,
 *                                one file per run.
 */
@ConfigurationProperties(prefix = "forgebrain.local-storage")
public record LocalStorageConfig(
        String curriculumRoadmapPath,
        String memoryStatePath,
        String pipelineOutputDirectory
) {
}
