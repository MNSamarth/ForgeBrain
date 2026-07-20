package com.forgebrain.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.curriculum.CurriculumLoaderImpl;
import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.Topic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetServiceImplTest {

    @TempDir
    Path tempDir;

    private Storyboard storyboard;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
        var curriculumLoader = new CurriculumLoaderImpl(objectMapper, new LocalStorageConfig(
                "../curriculum/java-roadmap.json", "unused", "unused", "unused"));
        Topic topic = curriculumLoader.findTopic("java-for-loop").orElseThrow();
        var research = new ResearchServiceImpl(curriculumLoader)
                .research("java-for-loop", topic, Topic.Difficulty.BEGINNER, 40, null);
        Lesson lesson = new LessonServiceImpl().generateLesson(research, null, null);
        ContentStrategy strategy = new ContentDirectorServiceImpl().decideStrategy(lesson, null);
        Script script = new ScriptServiceImpl().generateScript(lesson, strategy, Script.Platform.GENERIC_VERTICAL_SHORT);
        storyboard = new StoryboardServiceImpl().generateStoryboard(script, strategy);
    }

    private AssetServiceImpl service(Path assetsDirectory) {
        RenderingConfig config = new RenderingConfig("ffmpeg", "ffprobe", tempDir.resolve("renders").toString(),
                tempDir.resolve("voiceover").toString(), assetsDirectory.toString());
        return new AssetServiceImpl(config);
    }

    @Test
    void resolvesToDeterministicPlaceholdersWhenNoAssetCatalogExists() {
        AssetManifest manifest = service(tempDir.resolve("empty-assets")).resolveAssets(storyboard);

        assertThat(storyboard.renderStyle()).isEqualTo(Storyboard.RenderStyle.DARK_MODE_IDE);
        assertThat(manifest.resolvedTheme().fontHeading()).isEqualTo("Inter-Bold");
        assertThat(manifest.resolvedTheme().fontBody()).isEqualTo("Inter-Regular");
        assertThat(manifest.resolvedTheme().fontCode()).isEqualTo("JetBrainsMono-Regular");
        assertThat(manifest.resolvedTheme().codeSyntaxTheme()).isEqualTo("monokai");
        assertThat(manifest.backgroundMusic().trackUri()).isEqualTo("music/lofi-focus");
        assertThat(manifest.backgroundMusic().license()).isEqualTo("royalty-free-placeholder-not-verified");
        assertThat(manifest.watermark().assetUri()).isEqualTo("watermark/forgebrain-default");
        assertThat(manifest.assetManifestVersion()).contains("placeholder");
        assertThat(manifest.confidenceNotes().flaggedUncertainties())
                .anyMatch(note -> note.contains("No real asset catalog found"));

        long codeSceneCount = storyboard.scenes().stream().filter(s -> s.codeBlock() != null).count();
        assertThat(manifest.sceneAssets()).hasSize((int) codeSceneCount);
        manifest.sceneAssets().forEach(sceneAsset ->
                assertThat(sceneAsset.assetRefs()).anyMatch(ref -> ref.startsWith("code-screenshot:")));
    }

    @Test
    void resolvesToRealCatalogFilesWhenTheyExist() throws IOException {
        Path assetsDirectory = tempDir.resolve("real-assets");
        Files.createDirectories(assetsDirectory.resolve("fonts"));
        Files.createDirectories(assetsDirectory.resolve("music"));
        Files.createDirectories(assetsDirectory.resolve("watermark"));
        Path realHeadingFont = assetsDirectory.resolve("fonts/dark-mode-ide-heading.ttf");
        Path realMusic = assetsDirectory.resolve("music/dark-mode-ide.mp3");
        Path realWatermark = assetsDirectory.resolve("watermark/default.png");
        Files.writeString(realHeadingFont, "not a real font, just proving file resolution");
        Files.writeString(realMusic, "not real audio, just proving file resolution");
        Files.writeString(realWatermark, "not a real image, just proving file resolution");

        AssetManifest manifest = service(assetsDirectory).resolveAssets(storyboard);

        assertThat(manifest.resolvedTheme().fontHeading()).isEqualTo(realHeadingFont.toAbsolutePath().toString());
        // Body/code fonts weren't dropped into the catalog, so they still fall back.
        assertThat(manifest.resolvedTheme().fontBody()).isEqualTo("Inter-Regular");
        assertThat(manifest.backgroundMusic().trackUri()).isEqualTo(realMusic.toAbsolutePath().toString());
        assertThat(manifest.backgroundMusic().license()).isEqualTo("local-catalog");
        assertThat(manifest.watermark().assetUri()).isEqualTo(realWatermark.toAbsolutePath().toString());
        assertThat(manifest.assetManifestVersion()).contains("catalog");
    }
}
