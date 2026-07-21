package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Cloud Text-to-Speech configuration. Bound from {@code forgebrain.text-to-speech.*}.
 * {@code enabled} gates {@link com.forgebrain.backend.services.VoiceServiceBeanConfig}'s choice
 * between the real synthesis path and {@link com.forgebrain.backend.services.VoiceServiceImpl}'s
 * silent fallback — off by default, matching every other live-cloud-call flag in this package
 * ({@link GcpConfig}, {@link CloudStorageConfig}), so a fresh checkout never attempts a real
 * network call.
 *
 * @param enabled      whether the real Text-to-Speech path is used at all
 * @param languageCode BCP-47 language code, e.g. {@code "en-US"}
 * @param voiceName    a specific Google Cloud voice, e.g. {@code "en-US-Neural2-D"}
 * @param speakingRate speaking rate multiplier (1.0 = normal)
 * @param pitch        pitch adjustment in semitones (0.0 = unchanged)
 */
@ConfigurationProperties(prefix = "forgebrain.text-to-speech")
public record TextToSpeechConfig(
        boolean enabled,
        String languageCode,
        String voiceName,
        double speakingRate,
        double pitch
) {
}
