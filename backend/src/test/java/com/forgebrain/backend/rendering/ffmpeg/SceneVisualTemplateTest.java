package com.forgebrain.backend.rendering.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.models.Scene;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SceneVisualTemplateTest {

    @Test
    void everySceneTypeHasATemplate() {
        for (Scene.SceneType sceneType : Scene.SceneType.values()) {
            SceneVisualTemplate template = SceneVisualTemplate.forSceneType(sceneType);
            assertThat(template.accentColorHex()).isNotBlank();
            assertThat(template.accentCardColor()).isNotBlank();
            assertThat(template.headingFontSize()).isPositive();
        }
    }

    @Test
    void hookAndCtaAreCenteredWhileOtherTypesAnchorNearTheTop() {
        assertThat(SceneVisualTemplate.forSceneType(Scene.SceneType.HOOK).centered()).isTrue();
        assertThat(SceneVisualTemplate.forSceneType(Scene.SceneType.CTA).centered()).isTrue();
        assertThat(SceneVisualTemplate.forSceneType(Scene.SceneType.EXPLANATION).centered()).isFalse();
    }

    @Test
    void centeredTemplatesAnchorProportionallyToVideoHeight() {
        SceneVisualTemplate hook = SceneVisualTemplate.forSceneType(Scene.SceneType.HOOK);

        assertThat(hook.textYFor(1920)).isEqualTo(1920 / 2 - hook.headingFontSize() / 2);
        assertThat(hook.textYFor(1080)).isEqualTo(1080 / 2 - hook.headingFontSize() / 2);
    }

    @Test
    void distinctSceneTypesGetDistinctAccentColors() {
        long distinctColors = Arrays.stream(Scene.SceneType.values())
                .map(type -> SceneVisualTemplate.forSceneType(type).accentColorHex())
                .collect(Collectors.toSet())
                .size();

        assertThat(distinctColors).isGreaterThanOrEqualTo(5);
    }
}
