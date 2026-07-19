package com.forgebrain.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Script.CodeNarration;
import com.forgebrain.backend.models.Script.ScriptBeat;
import com.forgebrain.backend.models.Script.SubtitleSegment;
import com.forgebrain.backend.models.Script.SubtitleSegment.SourceField;
import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Vertex AI-backed {@link ScriptService}: calls {@link VertexAiClient} to turn a committed
 * {@link Lesson} and its binding {@link ContentStrategy} into spoken narration and on-screen text
 * (see {@link ScriptPromptBuilder} and brain/script-spec.md Section 3), and assembles every other
 * {@link Script} field deterministically — {@code hookType}/{@code teachingStyle} echoed
 * verbatim from the {@link ContentStrategy} (never a model choice, per Section 3), {@code
 * codeNarration.codeSnippet} carried from the lesson's own {@code core_example.code_sketch}, and
 * {@code fullSpokenScript}/{@code wordCount}/{@code estimatedDurationSeconds}/{@code
 * subtitleSegments} computed from the model's structured fields — exactly as {@link
 * ScriptServiceImpl} does for its heuristic fields.
 *
 * <p>Falls back to {@link ScriptServiceImpl} whenever Vertex AI is unavailable — missing {@code
 * forgebrain.vertex-ai.project-id}/{@code script-model} configuration ({@link
 * ConfigurationException}), a failed API call, or a response that doesn't parse into {@link
 * VertexAiScriptContent}. The fallback is a real, exercised code path, not a stub: this is the
 * only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiScriptServiceImpl implements ScriptService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiScriptServiceImpl.class);
    private static final String SCRIPT_VERSION = "1.0.0-vertex-ai";
    private static final double WORDS_PER_SECOND = 2.5;

    private final VertexAiClient vertexAiClient;
    private final VertexAiConfig vertexAiConfig;
    private final ObjectMapper objectMapper;
    private final ScriptServiceImpl fallback;

    public VertexAiScriptServiceImpl(VertexAiClient vertexAiClient, VertexAiConfig vertexAiConfig,
            ObjectMapper objectMapper) {
        this.vertexAiClient = vertexAiClient;
        this.vertexAiConfig = vertexAiConfig;
        this.objectMapper = objectMapper;
        this.fallback = new ScriptServiceImpl();
    }

    @Override
    public Script generateScript(Lesson lesson, ContentStrategy contentStrategy, Script.Platform platform) {
        try {
            return generateScriptViaVertexAi(lesson, contentStrategy, platform);
        } catch (ConfigurationException e) {
            log.info("Vertex AI script generation unavailable ({}); falling back to heuristic script for"
                    + " topic '{}'.", e.getMessage(), lesson.topicId());
        } catch (Exception e) {
            log.warn("Vertex AI script call failed for topic '{}'; falling back to heuristic script.",
                    lesson.topicId(), e);
        }
        return fallback.generateScript(lesson, contentStrategy, platform);
    }

    private Script generateScriptViaVertexAi(Lesson lesson, ContentStrategy contentStrategy,
            Script.Platform platform) throws Exception {
        String modelId = vertexAiConfig.scriptModel();
        if (modelId == null || modelId.isBlank()) {
            throw new ConfigurationException("forgebrain.vertex-ai.script-model is not configured");
        }

        String promptText = ScriptPromptBuilder.build(lesson, contentStrategy);
        VertexAiPromptRequest request = new VertexAiPromptRequest(modelId, promptText,
                Map.of("topic_id", lesson.topicId()),
                vertexAiConfig.scriptTemperature(), vertexAiConfig.scriptMaxOutputTokens(),
                vertexAiConfig.scriptResponseMimeType());
        VertexAiPromptResponse response = vertexAiClient.generate(request);

        VertexAiScriptContent content = objectMapper.readValue(response.rawText(), VertexAiScriptContent.class);
        validate(content);

        String codeSnippet = lesson.coreExample().codeSketch();
        CodeNarration codeNarration = new CodeNarration(
                content.codeNarration().spokenLines(), codeSnippet, content.codeNarration().focusLine());

        List<String> orderedPieces = new ArrayList<>();
        orderedPieces.add(content.hook());
        orderedPieces.add(content.introLine());
        content.mainScript().forEach(beat -> orderedPieces.add(beat.spokenLine()));
        orderedPieces.addAll(codeNarration.spokenLines());
        orderedPieces.add(content.recapLine());
        orderedPieces.add(content.ctaLine());

        String fullSpokenScript = String.join(" ", orderedPieces);
        int wordCount = countWords(fullSpokenScript);
        double estimatedDurationSeconds = round(wordCount / WORDS_PER_SECOND);

        List<SubtitleSegment> subtitleSegments = buildSubtitleSegments(
                content.hook(), content.introLine(), content.mainScript(), codeNarration,
                content.recapLine(), content.ctaLine());

        ConfidenceNotes confidenceNotes = buildConfidenceNotes(content, lesson, contentStrategy);

        return new Script(
                lesson.topicId(),
                lesson.topicTitle(),
                "primary",
                content.hook(),
                content.introLine(),
                content.mainScript(),
                codeNarration,
                content.recapLine(),
                content.ctaLine(),
                fullSpokenScript,
                content.sceneText(),
                subtitleSegments,
                wordCount,
                estimatedDurationSeconds,
                content.tone(),
                contentStrategy.hookType(),
                contentStrategy.teachingStyle(),
                confidenceNotes,
                lesson.targetDurationSeconds(),
                lesson.audienceLevel(),
                platform,
                SCRIPT_VERSION,
                Instant.now(),
                lesson.lessonVersion(),
                contentStrategy.contentDirectorVersion()
        );
    }

    private void validate(VertexAiScriptContent content) {
        if (isBlank(content.hook())
                || isBlank(content.introLine())
                || content.mainScript() == null || content.mainScript().isEmpty()
                || content.mainScript().stream().anyMatch(beat -> isBlank(beat.beat()) || isBlank(beat.spokenLine()))
                || content.codeNarration() == null
                || content.codeNarration().spokenLines() == null || content.codeNarration().spokenLines().isEmpty()
                || isBlank(content.codeNarration().focusLine())
                || isBlank(content.recapLine())
                || isBlank(content.ctaLine())
                || content.sceneText() == null || content.sceneText().isEmpty()
                || content.tone() == null
                || content.confidenceNotes() == null
                || content.confidenceNotes().overallConfidence() == null) {
            throw new ContentGenerationException("script",
                    "Vertex AI response is missing required script fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(VertexAiScriptContent content, Lesson lesson,
            ContentStrategy contentStrategy) {
        List<String> flagged = new ArrayList<>(content.confidenceNotes().flaggedUncertainties() != null
                ? content.confidenceNotes().flaggedUncertainties()
                : List.of());
        flagged.add("Generated by Vertex AI (" + vertexAiConfig.scriptModel() + ") from lesson lesson_version="
                + lesson.lessonVersion() + " and content_director_version="
                + contentStrategy.contentDirectorVersion() + ".");
        return new ConfidenceNotes(content.confidenceNotes().overallConfidence(), flagged, List.of());
    }

    private List<SubtitleSegment> buildSubtitleSegments(String hook, String introLine, List<ScriptBeat> mainScript,
            CodeNarration codeNarration, String recapLine, String ctaLine) {
        List<SubtitleSegment> segments = new ArrayList<>();
        int order = 1;
        segments.add(subtitleSegment(order++, hook, SourceField.HOOK));
        segments.add(subtitleSegment(order++, introLine, SourceField.INTRO_LINE));
        for (ScriptBeat beat : mainScript) {
            segments.add(subtitleSegment(order++, beat.spokenLine(), SourceField.MAIN_SCRIPT));
        }
        for (String line : codeNarration.spokenLines()) {
            segments.add(subtitleSegment(order++, line, SourceField.CODE_NARRATION));
        }
        segments.add(subtitleSegment(order++, recapLine, SourceField.RECAP_LINE));
        segments.add(subtitleSegment(order, ctaLine, SourceField.CTA_LINE));
        return segments;
    }

    private SubtitleSegment subtitleSegment(int order, String text, SourceField sourceField) {
        return new SubtitleSegment(order, text, sourceField, round(countWords(text) / WORDS_PER_SECOND), List.of());
    }

    private static int countWords(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
