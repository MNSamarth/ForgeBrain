package com.forgebrain.backend.ai;

import java.util.Optional;

/**
 * Pluggable cache seam for {@link AiGateway}: if the exact same request (same prompt, same model,
 * same prompt text) arrives twice, the raw Vertex AI response can be reused instead of calling
 * the model again. {@link InMemoryAiResponseCache} is the only implementation today — a future
 * Redis- or Firestore-backed cache (for reuse across process restarts or multiple instances) can
 * implement this same interface and be swapped in as the sole {@code @Component}, exactly like
 * {@code OutputStorage}'s local-vs-cloud seam.
 */
public interface AiResponseCache {

    Optional<String> get(String cacheKey);

    void put(String cacheKey, String rawResponse);
}
