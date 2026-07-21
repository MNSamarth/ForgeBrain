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
 * @param strict       whether {@link com.forgebrain.backend.services.GoogleCloudTextToSpeechVoiceServiceImpl}
 *                      must fail loudly instead of silently falling back to {@link
 *                      com.forgebrain.backend.services.VoiceServiceImpl}'s silent placeholder
 *                      track when synthesis fails. {@code false} (the default, and every local/dev
 *                      profile) means the silent fallback is an intentional, documented behavior;
 *                      {@code true} (the {@code cloud} profile) means narration is expected to be
 *                      real in that environment, so a synthesis failure should stop the render
 *                      rather than silently ship a mute reel. Has no effect when {@code enabled}
 *                      is {@code false} — {@link com.forgebrain.backend.services.VoiceServiceImpl}'s
 *                      silent track is the deliberately-chosen path in that case, not a failure.
 * @param languageCode BCP-47 language code, e.g. {@code "en-US"}
 * @param voiceName    a specific Google Cloud voice, e.g. {@code "en-US-Neural2-D"}
 * @param speakingRate speaking rate multiplier (1.0 = normal)
 * @param pitch        pitch adjustment in semitones (0.0 = unchanged)
 */
@ConfigurationProperties(prefix = "forgebrain.text-to-speech")
public record TextToSpeechConfig(
        boolean enabled,
        boolean strict,
        String languageCode,
        String voiceName,
        double speakingRate,
        double pitch
) {
}
