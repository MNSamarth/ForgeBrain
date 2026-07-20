package com.forgebrain.backend.services;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.AssetManifest.BackgroundMusic;
import com.forgebrain.backend.models.AssetManifest.ColorPalette;
import com.forgebrain.backend.models.AssetManifest.ResolvedTheme;
import com.forgebrain.backend.models.AssetManifest.SceneAsset;
import com.forgebrain.backend.models.AssetManifest.Watermark;
import com.forgebrain.backend.models.Scene;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.shared.ConfidenceLevel;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Real {@link AssetService} implementation: resolves a storyboard's abstract {@code
 * render_style} into concrete asset references, per {@code renderer/asset-management-spec.md}.
 *
 * <p>Checks {@link RenderingConfig#assetsDirectory()} (the local stand-in for the repository's
 * {@code assets/} catalog — see that spec's Section 4) for real files first; every category
 * falls back to a documented, deterministic placeholder reference when the catalog doesn't have
 * a real file yet, which is the honest Phase 1 state the spec itself describes ("Phase 1 ships
 * this stage's architecture with {@code assets/} still empty... an explicit, tracked gap"), not
 * the "missing catalog entry for a style that should exist" hard-stop case Section 7 describes.
 * As soon as real files are dropped into the catalog at the documented paths, this resolves to
 * them automatically — no code change, same pattern established by {@link VoiceServiceImpl} and
 * {@link com.forgebrain.backend.rendering.ffmpeg.PlaceholderAssetResolver}.
 */
@Component
public class AssetServiceImpl implements AssetService {

    private static final String VERSION_PLACEHOLDER = "1.0.0-local-placeholder";
    private static final String VERSION_CATALOG = "1.0.0-local-catalog";

    private final RenderingConfig renderingConfig;

    public AssetServiceImpl(RenderingConfig renderingConfig) {
        this.renderingConfig = renderingConfig;
    }

    @Override
    public AssetManifest resolveAssets(Storyboard storyboard) {
        Path assetsDirectory = Path.of(renderingConfig.assetsDirectory());
        List<String> flagged = new ArrayList<>();
        boolean[] usedRealAsset = {false};

        ResolvedTheme theme = resolveTheme(storyboard.renderStyle(), assetsDirectory, usedRealAsset);
        BackgroundMusic music = resolveMusic(storyboard.renderStyle(), assetsDirectory, usedRealAsset);
        Watermark watermark = resolveWatermark(assetsDirectory, usedRealAsset);
        List<SceneAsset> sceneAssets = resolveSceneAssets(storyboard, theme);

        if (!usedRealAsset[0]) {
            flagged.add("No real asset catalog found under '" + assetsDirectory + "'; every reference below is a"
                    + " deterministic placeholder name, not a resolved file — see"
                    + " renderer/asset-management-spec.md Section 4 and TODO.md 5.2. This is the documented"
                    + " Phase 1 gap, not a lookup failure against a populated catalog.");
        }

        return new AssetManifest(
                storyboard.topicId(),
                storyboard.topicTitle(),
                storyboard.renderStyle(),
                theme,
                music,
                watermark,
                sceneAssets,
                new ConfidenceNotes(usedRealAsset[0] ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM,
                        flagged, List.of()),
                usedRealAsset[0] ? VERSION_CATALOG : VERSION_PLACEHOLDER,
                Instant.now(),
                storyboard.storyboardVersion()
        );
    }

    private ResolvedTheme resolveTheme(Storyboard.RenderStyle renderStyle, Path assetsDirectory,
            boolean[] usedRealAsset) {
        String styleKey = renderStyle.name().toLowerCase(Locale.ROOT).replace('_', '-');
        String fontHeading = resolveOrPlaceholder(assetsDirectory, "fonts/" + styleKey + "-heading.ttf",
                headingFontPlaceholder(renderStyle), usedRealAsset);
        String fontBody = resolveOrPlaceholder(assetsDirectory, "fonts/" + styleKey + "-body.ttf",
                bodyFontPlaceholder(renderStyle), usedRealAsset);
        String fontCode = resolveOrPlaceholder(assetsDirectory, "fonts/" + styleKey + "-code.ttf",
                codeFontPlaceholder(renderStyle), usedRealAsset);
        return new ResolvedTheme(fontHeading, fontBody, fontCode, colorPaletteFor(renderStyle),
                codeSyntaxThemeFor(renderStyle));
    }

    private BackgroundMusic resolveMusic(Storyboard.RenderStyle renderStyle, Path assetsDirectory,
            boolean[] usedRealAsset) {
        String styleKey = renderStyle.name().toLowerCase(Locale.ROOT).replace('_', '-');
        Path realTrack = assetsDirectory.resolve("music/" + styleKey + ".mp3");
        if (Files.isRegularFile(realTrack)) {
            usedRealAsset[0] = true;
            return new BackgroundMusic(realTrack.toAbsolutePath().toString(), "local-catalog", -18.0);
        }
        return new BackgroundMusic(musicPlaceholder(renderStyle), "royalty-free-placeholder-not-verified", -18.0);
    }

    private Watermark resolveWatermark(Path assetsDirectory, boolean[] usedRealAsset) {
        Path realWatermark = assetsDirectory.resolve("watermark/default.png");
        if (Files.isRegularFile(realWatermark)) {
            usedRealAsset[0] = true;
            return new Watermark(realWatermark.toAbsolutePath().toString(), Watermark.Position.BOTTOM_RIGHT);
        }
        return new Watermark("watermark/forgebrain-default", Watermark.Position.BOTTOM_RIGHT);
    }

    private List<SceneAsset> resolveSceneAssets(Storyboard storyboard, ResolvedTheme theme) {
        List<SceneAsset> sceneAssets = new ArrayList<>();
        for (Scene scene : storyboard.scenes()) {
            if (scene.codeBlock() != null) {
                sceneAssets.add(new SceneAsset(scene.sceneId(),
                        List.of("code-font:" + theme.fontCode(), "code-screenshot:" + scene.sceneId())));
            }
        }
        return sceneAssets;
    }

    private String resolveOrPlaceholder(Path assetsDirectory, String relativePath, String placeholder,
            boolean[] usedRealAsset) {
        Path candidate = assetsDirectory.resolve(relativePath);
        if (Files.isRegularFile(candidate)) {
            usedRealAsset[0] = true;
            return candidate.toAbsolutePath().toString();
        }
        return placeholder;
    }

    private String headingFontPlaceholder(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> "Inter-Bold";
            case MINIMAL_LIGHT -> "Poppins-SemiBold";
            case NEON_TECH -> "Orbitron-Bold";
            case TERMINAL_RETRO -> "IBMPlexMono-Bold";
        };
    }

    private String bodyFontPlaceholder(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE, NEON_TECH -> "Inter-Regular";
            case MINIMAL_LIGHT -> "Poppins-Regular";
            case TERMINAL_RETRO -> "IBMPlexMono-Regular";
        };
    }

    private String codeFontPlaceholder(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE, MINIMAL_LIGHT -> "JetBrainsMono-Regular";
            case NEON_TECH -> "FiraCode-Regular";
            case TERMINAL_RETRO -> "IBMPlexMono-Regular";
        };
    }

    private String musicPlaceholder(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> "music/lofi-focus";
            case MINIMAL_LIGHT -> "music/soft-piano";
            case NEON_TECH -> "music/synthwave-drive";
            case TERMINAL_RETRO -> "music/chiptune-loop";
        };
    }

    private String codeSyntaxThemeFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> "monokai";
            case MINIMAL_LIGHT -> "github-light";
            case NEON_TECH -> "synthwave-84";
            case TERMINAL_RETRO -> "dracula";
        };
    }

    private ColorPalette colorPaletteFor(Storyboard.RenderStyle renderStyle) {
        return switch (renderStyle) {
            case DARK_MODE_IDE -> new ColorPalette("#0d1117", "#ffffff", "#58a6ff", "#f85149", "#3fb950");
            case MINIMAL_LIGHT -> new ColorPalette("#ffffff", "#0d1117", "#0969da", "#cf222e", "#1a7f37");
            case NEON_TECH -> new ColorPalette("#0a0014", "#ffffff", "#ff00e5", "#ff2d55", "#00ffa3");
            case TERMINAL_RETRO -> new ColorPalette("#001100", "#00ff41", "#00ff41", "#ff4136", "#39ff14");
        };
    }
}
