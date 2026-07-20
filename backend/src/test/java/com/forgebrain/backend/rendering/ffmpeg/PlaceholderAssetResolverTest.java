package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.models.Storyboard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaceholderAssetResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesToEmptyWhenNoRealVoiceoverFileExists() {
        PlaceholderAssetResolver resolver = new PlaceholderAssetResolver(
                new RenderingConfig("ffmpeg", "ffprobe", "data/renders", tempDir.toString(), "assets"));

        assertThat(resolver.resolveVoiceoverPath("java-for-loop")).isEmpty();
    }

    @Test
    void resolvesToTheRealFileWhenAnMp3ExistsForTheTopic() throws IOException {
        Files.writeString(tempDir.resolve("java-for-loop.mp3"), "not real audio, just proving file resolution");
        PlaceholderAssetResolver resolver = new PlaceholderAssetResolver(
                new RenderingConfig("ffmpeg", "ffprobe", "data/renders", tempDir.toString(), "assets"));

        Optional<Path> resolved = resolver.resolveVoiceoverPath("java-for-loop");

        assertThat(resolved).contains(tempDir.resolve("java-for-loop.mp3"));
    }

    @Test
    void resolvesToAWavFileWhenNoMp3ExistsForTheTopic() throws IOException {
        Files.writeString(tempDir.resolve("java-for-loop.wav"), "not real audio either");
        PlaceholderAssetResolver resolver = new PlaceholderAssetResolver(
                new RenderingConfig("ffmpeg", "ffprobe", "data/renders", tempDir.toString(), "assets"));

        assertThat(resolver.resolveVoiceoverPath("java-for-loop")).contains(tempDir.resolve("java-for-loop.wav"));
    }

    @Test
    void everyRenderStyleHasABackgroundAndATextColor() {
        for (Storyboard.RenderStyle style : Storyboard.RenderStyle.values()) {
            assertThat(PlaceholderAssetResolver.backgroundColorHexFor(style)).startsWith("0x");
            assertThat(PlaceholderAssetResolver.textColorFor(style)).isIn("white", "black");
        }
    }
}
