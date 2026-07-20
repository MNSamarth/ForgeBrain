package com.forgebrain.backend.services;

import com.forgebrain.backend.ai.AiGateway;
import com.forgebrain.backend.ai.AiGatewayResult;
import com.forgebrain.backend.ai.AiPromptExecution;
import com.forgebrain.backend.exceptions.AiGatewayException;
import com.forgebrain.backend.exceptions.ContentGenerationException;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Script.CodeNarration;
import com.forgebrain.backend.models.Script.ScriptBeat;
import com.forgebrain.backend.models.Script.SubtitleSegment;
import com.forgebrain.backend.models.Script.SubtitleSegment.SourceField;
import com.forgebrain.backend.shared.ConfidenceNotes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI Gateway-backed {@link ScriptService}: calls {@link AiGateway} to turn a committed {@link
 * Lesson} and its binding {@link ContentStrategy} into spoken narration and on-screen text (see
 * {@link ScriptPromptBuilder} and brain/script-spec.md Section 3), and assembles every other
 * {@link Script} field deterministically — {@code hookType}/{@code teachingStyle} echoed verbatim
 * from the {@link ContentStrategy} (never a model choice, per Section 3), {@code
 * codeNarration.codeSnippet} carried from the lesson's own {@code core_example.code_sketch}, and
 * {@code fullSpokenScript}/{@code wordCount}/{@code estimatedDurationSeconds}/{@code
 * subtitleSegments} computed from the model's structured fields — exactly as {@link
 * ScriptServiceImpl} does for its heuristic fields.
 *
 * <p>Falls back to {@link ScriptServiceImpl} whenever the AI Gateway can't produce a usable
 * result — see {@link AiGatewayException}. The fallback is a real, exercised code path, not a
 * stub: this is the only path this sandbox can take without live GCP credentials.
 */
@Component
public class VertexAiScriptServiceImpl implements ScriptService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiScriptServiceImpl.class);
    private static final String SCRIPT_VERSION = "1.0.0-vertex-ai";
    private static final String PROMPT_NAME = "script";
    private static final double WORDS_PER_SECOND = 2.5;

    private final AiGateway aiGateway;
    private final ScriptServiceImpl fallback;

    public VertexAiScriptServiceImpl(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
        this.fallback = new ScriptServiceImpl();
    }

    @Override
    public Script generateScript(Lesson lesson, ContentStrategy contentStrategy, Script.Platform platform) {
        try {
            return generateScriptViaAiGateway(lesson, contentStrategy, platform);
        } catch (AiGatewayException e) {
            if (e.reason() == AiGatewayException.Reason.CONFIGURATION) {
                log.info("AI gateway unavailable for script generation ({}); falling back to heuristic script for"
                        + " topic '{}'.", e.getMessage(), lesson.topicId());
            } else {
                log.warn("AI gateway script call failed for topic '{}'; falling back to heuristic script.",
                        lesson.topicId(), e);
            }
            aiGateway.recordFallbackUsed(PROMPT_NAME);
        }
        return fallback.generateScript(lesson, contentStrategy, platform);
    }

    private Script generateScriptViaAiGateway(Lesson lesson, ContentStrategy contentStrategy,
            Script.Platform platform) {
        String promptText = ScriptPromptBuilder.build(lesson, contentStrategy);
        AiGatewayResult<VertexAiScriptContent> result = aiGateway.execute(new AiPromptExecution<>(PROMPT_NAME,
                promptText, Map.of("topic_id", lesson.topicId()), VertexAiScriptContent.class, this::validate));
        VertexAiScriptContent content = result.content();

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

        ConfidenceNotes confidenceNotes = buildConfidenceNotes(content, lesson, contentStrategy, result.modelId());

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
                    "AI gateway response is missing required script fields: " + content);
        }
    }

    private ConfidenceNotes buildConfidenceNotes(VertexAiScriptContent content, Lesson lesson,
            ContentStrategy contentStrategy, String modelId) {
        List<String> flagged = new ArrayList<>(content.confidenceNotes().flaggedUncertainties() != null
                ? content.confidenceNotes().flaggedUncertainties()
                : List.of());
        flagged.add("Generated by Vertex AI (" + modelId + ", via the AI Gateway's 'script' prompt) from lesson"
                + " lesson_version=" + lesson.lessonVersion() + " and content_director_version="
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
