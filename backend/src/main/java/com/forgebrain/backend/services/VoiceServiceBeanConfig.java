package com.forgebrain.backend.services;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.config.TextToSpeechConfig;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single {@link VoiceService} bean every caller injects. {@link VoiceServiceImpl}
 * (silent fallback) and {@link GoogleCloudTextToSpeechVoiceServiceImpl} (real Google Cloud
 * Text-to-Speech synthesis) are both plain classes, not {@code @Component}s, precisely so this is
 * the only place that decides between them — mirrors {@code PlatformPublishAdapterBeanConfig}'s
 * dry-run-vs-real platform adapter wiring exactly. Off by default ({@code
 * forgebrain.text-to-speech.enabled: false}), matching every other live-cloud-call flag in this
 * codebase, so a fresh checkout never attempts a real network call.
 */
@Configuration
public class VoiceServiceBeanConfig {

    @Bean
    public VoiceService voiceService(TextToSpeechConfig textToSpeechConfig, RenderingConfig renderingConfig,
            FfmpegProcessRunner processRunner) {
        VoiceServiceImpl fallback = new VoiceServiceImpl(renderingConfig, processRunner);
        if (!textToSpeechConfig.enabled()) {
            return fallback;
        }
        return new GoogleCloudTextToSpeechVoiceServiceImpl(textToSpeechConfig, renderingConfig, processRunner,
                fallback);
    }
}
