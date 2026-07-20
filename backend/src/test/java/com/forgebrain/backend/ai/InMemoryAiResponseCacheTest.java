package com.forgebrain.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryAiResponseCache} — per this mission's Part 7 ("cache").
 */
class InMemoryAiResponseCacheTest {

    @Test
    void returnsEmptyForAKeyThatWasNeverStored() {
        assertThat(new InMemoryAiResponseCache().get("missing")).isEmpty();
    }

    @Test
    void returnsWhatWasStoredUnderTheSameKey() {
        InMemoryAiResponseCache cache = new InMemoryAiResponseCache();

        cache.put("key-1", "{\"a\":1}");

        assertThat(cache.get("key-1")).contains("{\"a\":1}");
    }

    @Test
    void differentKeysDoNotCollide() {
        InMemoryAiResponseCache cache = new InMemoryAiResponseCache();

        cache.put("key-1", "first");
        cache.put("key-2", "second");

        assertThat(cache.get("key-1")).contains("first");
        assertThat(cache.get("key-2")).contains("second");
    }

    @Test
    void puttingUnderAnExistingKeyOverwritesIt() {
        InMemoryAiResponseCache cache = new InMemoryAiResponseCache();

        cache.put("key-1", "first");
        cache.put("key-1", "second");

        assertThat(cache.get("key-1")).contains("second");
    }
}
