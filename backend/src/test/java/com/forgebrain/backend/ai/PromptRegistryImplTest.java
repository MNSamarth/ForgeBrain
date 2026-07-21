package com.forgebrain.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptRegistryImpl} — per this mission's Part 3 ("model routing").
 * Confirms every registered prompt routes to whichever model {@link VertexAiConfig} names, so
 * switching a model is purely a configuration change.
 */
class PromptRegistryImplTest {

    private static VertexAiConfig config() {
        return new VertexAiConfig("demo-project", "us-central1", "gemini-flash-research", "gemini-pro-lesson",
                "gemini-pro-script", "gemini-flash-director", 0.3, 1024, "application/json", 0.5, 2048,
                "application/json", 0.6, 4096, "application/json", "gemini-pro-visual-director", 0.7, 3072,
                "application/json");
    }

    @Test
    void routesResearchToItsConfiguredModelWithNoTemperatureOrTokenCapOverride() {
        PromptDefinition definition = new PromptRegistryImpl(config()).get("research");

        assertThat(definition.modelId()).isEqualTo("gemini-flash-research");
        assertThat(definition.temperature()).isNull();
        assertThat(definition.maxOutputTokens()).isNull();
        assertThat(definition.responseMimeType()).isEqualTo("application/json");
    }

    @Test
    void routesLessonToItsConfiguredModelAndGenerationParameters() {
        PromptDefinition definition = new PromptRegistryImpl(config()).get("lesson");

        assertThat(definition.modelId()).isEqualTo("gemini-pro-lesson");
        assertThat(definition.temperature()).isEqualTo(0.3);
        assertThat(definition.maxOutputTokens()).isEqualTo(1024);
    }

    @Test
    void routesContentDirectorToItsConfiguredModel() {
        PromptDefinition definition = new PromptRegistryImpl(config()).get("content-director");

        assertThat(definition.modelId()).isEqualTo("gemini-flash-director");
    }

    @Test
    void routesScriptToItsConfiguredModel() {
        PromptDefinition definition = new PromptRegistryImpl(config()).get("script");

        assertThat(definition.modelId()).isEqualTo("gemini-pro-script");
        assertThat(definition.temperature()).isEqualTo(0.6);
        assertThat(definition.maxOutputTokens()).isEqualTo(4096);
    }

    @Test
    void changingConfiguredModelIdsChangesRoutingWithoutAnyOtherChange() {
        VertexAiConfig switched = new VertexAiConfig("demo-project", "us-central1", "gemini-flash-research",
                "a-different-pro-model", "gemini-pro-script", "gemini-flash-director", 0.5, 2048, "application/json",
                0.5, 2048, "application/json", 0.6, 4096, "application/json", "gemini-pro-visual-director", 0.7,
                3072, "application/json");

        assertThat(new PromptRegistryImpl(switched).get("lesson").modelId()).isEqualTo("a-different-pro-model");
    }

    @Test
    void routesVisualDirectorToItsConfiguredModelAndGenerationParameters() {
        PromptDefinition definition = new PromptRegistryImpl(config()).get("visual-director");

        assertThat(definition.modelId()).isEqualTo("gemini-pro-visual-director");
        assertThat(definition.temperature()).isEqualTo(0.7);
        assertThat(definition.maxOutputTokens()).isEqualTo(3072);
    }

    @Test
    void findAllReturnsAllFiveRegisteredPrompts() {
        assertThat(new PromptRegistryImpl(config()).findAll()).extracting(PromptDefinition::name)
                .containsExactlyInAnyOrder("research", "lesson", "content-director", "script", "visual-director");
    }

    @Test
    void unknownPromptNameThrowsConfigurationException() {
        assertThatThrownBy(() -> new PromptRegistryImpl(config()).get("storyboard"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("storyboard");
    }
}
