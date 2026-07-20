package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.models.Storyboard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link com.forgebrain.backend.rendering.RenderPlan}'s abstract asset references
 * against real local files where possible, and falls back to a documented, deterministic
 * placeholder everywhere real production assets don't exist yet — Voice Generation and Asset
 * Management (TODO.md 1.8, 1.10) aren't implemented, so today every render genuinely goes
 * through the fallback paths. Both paths are real, exercised code, not stubs: as soon as a real
 * voiceover file is dropped into the configured directory, {@link #resolveVoiceoverPath} picks
 * it up automatically with no code change.
 */
@Component
class PlaceholderAssetResolver {

    private final RenderingConfig renderingConfig;

    PlaceholderAssetResolver(RenderingConfig renderingConfig) {
        this.renderingConfig = renderingConfig;
    }

    /**
     * @return the real narration file for this topic, if one exists in the configured
     * voiceover assets directory as {@code <topicId>.mp3} or {@code <topicId>.wav}; otherwise
     * empty, signaling the caller should fall back to a silent audio track.
     */
    Optional<Path> resolveVoiceoverPath(String topicId) {
        for (String extension : new String[] {"mp3", "wav"}) {
            Path candidate = Path.of(renderingConfig.voiceoverAssetsDirectory(), topicId + "." + extension);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * A deterministic background color standing in for a real background video/image asset,
     * which Asset Management doesn't produce yet — chosen per {@link Storyboard.RenderStyle} so
     * the placeholder still visually distinguishes the reel's intended look.
     */
    static String backgroundColorHexFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> "0x0d1117";
            case MINIMAL_LIGHT -> "0xffffff";
            case NEON_TECH -> "0x0a0014";
            case TERMINAL_RETRO -> "0x001100";
        };
    }

    /**
     * Readable text color that contrasts with {@link #backgroundColorHexFor}.
     */
    static String textColorFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case MINIMAL_LIGHT -> "black";
            case DARK_MODE_IDE, NEON_TECH, TERMINAL_RETRO -> "white";
        };
    }
}
