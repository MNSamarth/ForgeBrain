package com.forgebrain.backend.ai;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The default {@link AiResponseCache}: a plain in-process map, cleared on restart. Adequate for
 * this phase's single-instance local/Cloud Run deployment (see docs/CONFIGURATION.md) — a
 * cross-instance cache is future work, not needed until this runs with more than one replica.
 */
@Component
public class InMemoryAiResponseCache implements AiResponseCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String cacheKey) {
        return Optional.ofNullable(cache.get(cacheKey));
    }

    @Override
    public void put(String cacheKey, String rawResponse) {
        cache.put(cacheKey, rawResponse);
    }
}
