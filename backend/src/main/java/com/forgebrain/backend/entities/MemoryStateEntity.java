package com.forgebrain.backend.entities;

import java.time.Instant;

/**
 * Persisted memory state. Stores the full {@code MemoryState} document (see
 * memory/memory-schema.json) as a serialized blob rather than exploding every nested field
 * into entity properties — memory state is read and written as one coherent document, never
 * queried by its internal fields directly (queries go through {@link
 * com.forgebrain.backend.memory.MemoryQueries} against the deserialized model instead).
 *
 * <p>{@code documentId} is expected to map to the Firestore document ID once a real Firestore
 * mapping is wired up — see TODO.md.
 */
public class MemoryStateEntity {

    private String documentId;
    private String stateJson;
    private String memoryVersion;
    private Instant lastUpdated;

    public MemoryStateEntity() {
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public String getMemoryVersion() {
        return memoryVersion;
    }

    public void setMemoryVersion(String memoryVersion) {
        this.memoryVersion = memoryVersion;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
