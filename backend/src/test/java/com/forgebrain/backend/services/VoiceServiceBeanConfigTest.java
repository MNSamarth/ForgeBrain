package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.config.TextToSpeechConfig;
import com.forgebrain.backend.rendering.ffmpeg.FfmpegProcessRunner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors {@code PlatformPublishAdapterBeanConfig}'s dry-run-vs-real selection tests: {@link
 * VoiceServiceBeanConfig} is the single place deciding between {@link VoiceServiceImpl} (silent
 * fallback) and {@link GoogleCloudTextToSpeechVoiceServiceImpl} (real synthesis).
 */
class VoiceServiceBeanConfigTest {

    @TempDir
    Path tempDir;

    private RenderingConfig renderingConfig() {
        return new RenderingConfig("ffmpeg", "ffprobe", tempDir.resolve("renders").toString(),
                tempDir.resolve("voiceover").toString(), tempDir.resolve("assets").toString());
    }

    @Test
    void resolvesToTheSilentFallbackWhenTextToSpeechIsDisabled() {
        VoiceServiceBeanConfig beanConfig = new VoiceServiceBeanConfig();
        TextToSpeechConfig disabled = new TextToSpeechConfig(false, "en-US", "", 1.0, 0.0);

        VoiceService resolved = beanConfig.voiceService(disabled, renderingConfig(),
                new FfmpegProcessRunner(renderingConfig()));

        assertThat(resolved).isInstanceOf(VoiceServiceImpl.class);
    }

    @Test
    void resolvesToTheRealGoogleCloudImplementationWhenEnabled() {
        VoiceServiceBeanConfig beanConfig = new VoiceServiceBeanConfig();
        TextToSpeechConfig enabled = new TextToSpeechConfig(true, "en-US", "en-US-Neural2-D", 1.0, 0.0);

        VoiceService resolved = beanConfig.voiceService(enabled, renderingConfig(),
                new FfmpegProcessRunner(renderingConfig()));

        assertThat(resolved).isInstanceOf(GoogleCloudTextToSpeechVoiceServiceImpl.class);
    }
}
