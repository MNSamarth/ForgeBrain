package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore configuration. See docs/CONFIGURATION.md Section 3. Bound from
 * {@code forgebrain.firestore.*} in application.yml. Backs {@link
 * com.forgebrain.backend.repositories} and, ultimately, {@link
 * com.forgebrain.backend.entities}.
 *
 * @param projectId        GCP project ID (empty placeholder in every committed profile)
 * @param databaseId       Firestore database ID, "(default)" unless a named database is used
 * @param collectionPrefix prefix applied to every collection name, so multiple environments
 *                         can share one Firestore instance without colliding
 */
@ConfigurationProperties(prefix = "forgebrain.firestore")
public record FirestoreConfig(
        String projectId,
        String databaseId,
        String collectionPrefix
) {
}
