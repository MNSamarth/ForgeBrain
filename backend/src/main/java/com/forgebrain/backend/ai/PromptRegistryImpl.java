package com.forgebrain.backend.ai;

import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the fixed set of {@link PromptDefinition}s this codebase knows about today — one per
 * generative pipeline stage — directly from {@link VertexAiConfig} at construction time. Model
 * routing (Research → the configured {@code research-model}, Lesson/Script → their own configured
 * models, and so on) is therefore entirely a matter of what {@code application.yml} says; this
 * class never hard-codes a model id itself.
 */
@Component
public class PromptRegistryImpl implements PromptRegistry {

    private static final String VERSION = "1.0.0-ai-gateway";

    private final Map<String, PromptDefinition> definitions;

    public PromptRegistryImpl(VertexAiConfig vertexAiConfig) {
        Map<String, PromptDefinition> defs = new LinkedHashMap<>();
        defs.put("research", new PromptDefinition("research", VERSION,
                "Fills in the research-brief fields (topic summary, core concepts, analogy, explanations, "
                        + "safety notes) for the Research stage — see ResearchPromptBuilder.",
                vertexAiConfig.researchModel(), null, null, "application/json"));
        defs.put("lesson", new PromptDefinition("lesson", VERSION,
                "Fills in the lesson-blueprint fields (objective, summary, key points, core example, common "
                        + "mistakes, retention hook) for the Lesson stage — see LessonPromptBuilder.",
                vertexAiConfig.lessonModel(), vertexAiConfig.lessonTemperature(),
                vertexAiConfig.lessonMaxOutputTokens(), vertexAiConfig.lessonResponseMimeType()));
        defs.put("content-director", new PromptDefinition("content-director", VERSION,
                "Decides the seven directorial-strategy fields (hook, teaching posture, emotional goal, "
                        + "pacing, visual strategy, code framing, CTA) for the Content Director stage — see "
                        + "ContentDirectorPromptBuilder.",
                vertexAiConfig.contentDirectorModel(), vertexAiConfig.contentDirectorTemperature(),
                vertexAiConfig.contentDirectorMaxOutputTokens(), vertexAiConfig.contentDirectorResponseMimeType()));
        defs.put("script", new PromptDefinition("script", VERSION,
                "Generates spoken narration and on-screen text for the Script stage — see ScriptPromptBuilder.",
                vertexAiConfig.scriptModel(), vertexAiConfig.scriptTemperature(),
                vertexAiConfig.scriptMaxOutputTokens(), vertexAiConfig.scriptResponseMimeType()));
        this.definitions = Map.copyOf(defs);
    }

    @Override
    public PromptDefinition get(String promptName) {
        PromptDefinition definition = definitions.get(promptName);
        if (definition == null) {
            throw new ConfigurationException("No PromptDefinition registered for prompt name '" + promptName
                    + "'; registered prompts: " + definitions.keySet());
        }
        return definition;
    }

    @Override
    public List<PromptDefinition> findAll() {
        return List.copyOf(definitions.values());
    }
}
