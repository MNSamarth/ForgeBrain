package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.curriculum.CurriculumLoader;
import com.forgebrain.backend.exceptions.InvalidTopicException;
import com.forgebrain.backend.exceptions.PipelineStageException;
import com.forgebrain.backend.models.MemoryState;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Topic;
import com.forgebrain.backend.models.TopicSelectionDecision;
import com.forgebrain.backend.services.ContentDirectorService;
import com.forgebrain.backend.services.LessonService;
import com.forgebrain.backend.services.MemoryService;
import com.forgebrain.backend.services.ResearchService;
import com.forgebrain.backend.services.ScriptService;
import com.forgebrain.backend.services.StoryboardService;
import com.forgebrain.backend.shared.ConfidenceNotes;
import com.forgebrain.backend.shared.PipelineStage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Real implementation of {@link PipelineOrchestrator}. {@link #advance} is the actual state
 * machine: it inspects which fields of a {@link PipelineContext} are still unset and runs
 * exactly the next stage, which is what makes both the original stage-by-stage contract and
 * the {@link #runFullPipeline()} convenience method correct by construction — the latter is
 * just {@link #startRun} followed by calling {@link #advance} until a storyboard exists,
 * not a second, parallel implementation of the same sequencing logic.
 */
@Component
public class PipelineOrchestratorImpl implements PipelineOrchestrator {

    private static final int MAX_ADVANCE_ITERATIONS = 10;

    private final CurriculumLoader curriculumLoader;
    private final MemoryService memoryService;
    private final com.forgebrain.backend.services.TopicSelector topicSelector;
    private final ResearchService researchService;
    private final LessonService lessonService;
    private final ContentDirectorService contentDirectorService;
    private final ScriptService scriptService;
    private final StoryboardService storyboardService;
    private final PipelineResultStore resultStore;
    private final ReportWriter reportWriter;

    private final Map<String, PipelineContext> runsByTopicId = new ConcurrentHashMap<>();

    public PipelineOrchestratorImpl(CurriculumLoader curriculumLoader, MemoryService memoryService,
            com.forgebrain.backend.services.TopicSelector topicSelector, ResearchService researchService,
            LessonService lessonService, ContentDirectorService contentDirectorService,
            ScriptService scriptService, StoryboardService storyboardService, PipelineResultStore resultStore,
            ReportWriter reportWriter) {
        this.curriculumLoader = curriculumLoader;
        this.memoryService = memoryService;
        this.topicSelector = topicSelector;
        this.researchService = researchService;
        this.lessonService = lessonService;
        this.contentDirectorService = contentDirectorService;
        this.scriptService = scriptService;
        this.storyboardService = storyboardService;
        this.resultStore = resultStore;
        this.reportWriter = reportWriter;
    }

    @Override
    public PipelineContext startRun(TopicSelectionDecision decision) {
        if (decision.selectedTopicId() == null) {
            throw new InvalidTopicException("Cannot start a pipeline run: the topic selection decision has no"
                    + " selected topic (" + decision.reason() + ").");
        }
        Topic topic = curriculumLoader.findTopic(decision.selectedTopicId())
                .orElseThrow(() -> new InvalidTopicException("Selected topic_id does not exist in the curriculum: " + decision.selectedTopicId()));

        memoryService.markTopicInProgress(topic.id(), topic.title(), topic.difficulty());

        PipelineContext context = new PipelineContext(topic.id());
        context.topicSelectionDecision(decision);
        context.currentStage(PipelineStage.TOPIC_SELECTION);
        runsByTopicId.put(topic.id(), context);
        return context;
    }

    @Override
    public PipelineContext advance(PipelineContext context) {
        Topic topic = curriculumLoader.findTopic(context.topicId())
                .orElseThrow(() -> new InvalidTopicException("Unknown topic_id in pipeline context: " + context.topicId()));
        MemoryState.TopicRecord topicMemory = memoryService.getTopicRecord(context.topicId());

        if (context.researchResult() == null) {
            context.researchResult(researchService.research(
                    topic.id(), topic, topic.difficulty(), 45, topicMemory));
            context.currentStage(PipelineStage.RESEARCH);
        } else if (context.lesson() == null) {
            context.lesson(lessonService.generateLesson(context.researchResult(), topicMemory, null));
            context.currentStage(PipelineStage.LESSON);
        } else if (context.contentStrategy() == null) {
            context.contentStrategy(contentDirectorService.decideStrategy(context.lesson(), topicMemory));
            context.currentStage(PipelineStage.CONTENT_DIRECTOR);
        } else if (context.script() == null) {
            Script script = scriptService.generateScript(context.lesson(), context.contentStrategy(), Script.Platform.GENERIC_VERTICAL_SHORT);
            context.script(script);
            context.currentStage(PipelineStage.SCRIPT);
            memoryService.recordUsedHook(topic.id(), script.hook());
        } else if (context.storyboard() == null) {
            context.storyboard(storyboardService.generateStoryboard(context.script(), context.contentStrategy()));
            context.currentStage(PipelineStage.STORYBOARD);
            context.status(PipelineRunStatus.COMPLETED);
        }
        // else: already complete, advance is a no-op.

        return context;
    }

    @Override
    public PipelineContext getRun(String topicId) {
        return runsByTopicId.get(topicId);
    }

    @Override
    public PipelineResult runFullPipeline() {
        String pipelineId = UUID.randomUUID().toString();
        Instant executionStart = Instant.now();
        List<StageExecutionSummary> stageResults = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String selectedTopic = null;
        String finalStatus = "FAILED";
        PipelineContext context = null;
        Exception pipelineException = null;

        try {
            MemoryState currentMemory = memoryService.loadCurrentState();
            Instant stageStart = Instant.now();
            TopicSelectionDecision decision = null;
            try {
                decision = topicSelector.selectNextTopic(
                        TopicSelectionDecision.Mode.NEXT_TOPIC, currentMemory, Instant.now());
                selectedTopic = decision.selectedTopicId();
                context = startRun(decision);
                context.status(PipelineRunStatus.IN_PROGRESS);
                stageResults.add(new StageExecutionSummary(
                        PipelineStage.TOPIC_SELECTION.name(),
                        Duration.between(stageStart, Instant.now()),
                        true,
                        "mode=NEXT_TOPIC, tracked_topics=" + currentMemory.topics().size(),
                        "selected_topic_id=" + decision.selectedTopicId() + ", score=" + decision.score(),
                        false,
                        decision.score() == null ? "unknown" : String.valueOf(decision.score()),
                        null));
            } catch (Exception e) {
                stageResults.add(new StageExecutionSummary(
                        PipelineStage.TOPIC_SELECTION.name(),
                        Duration.between(stageStart, Instant.now()),
                        false,
                        "mode=NEXT_TOPIC, tracked_topics=" + currentMemory.topics().size(),
                        "topic selection failed",
                        false,
                        "unknown",
                        summarizeException(e)));
                errors.add("TOPIC_SELECTION: " + summarizeException(e));
                throw e;
            }

            int iterations = 0;
            while (context.storyboard() == null) {
                if (++iterations > MAX_ADVANCE_ITERATIONS) {
                    throw new PipelineStageException(String.valueOf(context.currentStage()),
                            "Pipeline did not reach a storyboard after " + MAX_ADVANCE_ITERATIONS + " advance() calls — likely stuck.");
                }
                PipelineStage pendingStage = nextPendingStage(context);
                Instant perStageStart = Instant.now();
                String inputSummary = stageInputSummary(pendingStage, context);
                try {
                    advance(context);
                    StageExecutionSummary summary = successSummaryForStage(
                            pendingStage, perStageStart, inputSummary, context);
                    stageResults.add(summary);
                    if (summary.fallbackUsed()) {
                        warnings.add(pendingStage.name() + " used deterministic fallback path.");
                    }
                } catch (Exception e) {
                    stageResults.add(new StageExecutionSummary(
                            pendingStage.name(),
                            Duration.between(perStageStart, Instant.now()),
                            false,
                            inputSummary,
                            "stage failed",
                            false,
                            "unknown",
                            summarizeException(e)));
                    errors.add(pendingStage.name() + ": " + summarizeException(e));
                    throw e;
                }
            }

            PipelineResult result = PipelineResult.fromContext(context);
            resultStore.save(result);
            finalStatus = "SUCCESS";
            return result;
        } catch (Exception e) {
            pipelineException = e;
            throw e;
        } finally {
            PipelineExecutionReport report = new PipelineExecutionReport(
                    pipelineId,
                    executionStart,
                    Instant.now(),
                    Duration.between(executionStart, Instant.now()),
                    selectedTopic,
                    List.copyOf(stageResults),
                    finalStatus,
                    List.copyOf(warnings),
                    List.copyOf(errors));
            try {
                reportWriter.write(report);
            } catch (RuntimeException reportWriteException) {
                if (pipelineException != null) {
                    pipelineException.addSuppressed(reportWriteException);
                } else {
                    throw reportWriteException;
                }
            }
        }
    }

    private PipelineStage nextPendingStage(PipelineContext context) {
        if (context.researchResult() == null) {
            return PipelineStage.RESEARCH;
        }
        if (context.lesson() == null) {
            return PipelineStage.LESSON;
        }
        if (context.contentStrategy() == null) {
            return PipelineStage.CONTENT_DIRECTOR;
        }
        if (context.script() == null) {
            return PipelineStage.SCRIPT;
        }
        if (context.storyboard() == null) {
            return PipelineStage.STORYBOARD;
        }
        return context.currentStage();
    }

    private String stageInputSummary(PipelineStage stage, PipelineContext context) {
        return switch (stage) {
            case RESEARCH -> "topic_id=" + context.topicId();
            case LESSON -> "research_version=" + context.researchResult().researchVersion();
            case CONTENT_DIRECTOR -> "lesson_version=" + context.lesson().lessonVersion();
            case SCRIPT -> "lesson_version=" + context.lesson().lessonVersion()
                    + ", content_director_version=" + context.contentStrategy().contentDirectorVersion();
            case STORYBOARD -> "script_version=" + context.script().scriptVersion();
            default -> "n/a";
        };
    }

    private StageExecutionSummary successSummaryForStage(PipelineStage stage, Instant stageStart,
            String inputSummary, PipelineContext context) {
        return switch (stage) {
            case RESEARCH -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "research_version=" + context.researchResult().researchVersion()
                            + ", core_concepts=" + context.researchResult().coreConcepts().size(),
                    context.researchResult().researchVersion().contains("heuristic"),
                    confidenceValue(context.researchResult().confidenceNotes()),
                    null);
            case LESSON -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "lesson_version=" + context.lesson().lessonVersion()
                            + ", key_points=" + context.lesson().keyPoints().size(),
                    context.lesson().lessonVersion().contains("heuristic"),
                    confidenceValue(context.lesson().confidenceNotes()),
                    null);
            case CONTENT_DIRECTOR -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "content_director_version=" + context.contentStrategy().contentDirectorVersion()
                            + ", hook_type=" + context.contentStrategy().hookType(),
                    false,
                    confidenceValue(context.contentStrategy().confidenceNotes()),
                    null);
            case SCRIPT -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "script_version=" + context.script().scriptVersion()
                            + ", word_count=" + context.script().wordCount(),
                    false,
                    confidenceValue(context.script().confidenceNotes()),
                    null);
            case STORYBOARD -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "storyboard_version=" + context.storyboard().storyboardVersion()
                            + ", scenes=" + context.storyboard().sceneCount(),
                    false,
                    confidenceValue(context.storyboard().confidenceNotes()),
                    null);
            default -> new StageExecutionSummary(
                    stage.name(),
                    Duration.between(stageStart, Instant.now()),
                    true,
                    inputSummary,
                    "completed",
                    false,
                    "unknown",
                    null);
        };
    }

    private static String summarizeException(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static String confidenceValue(ConfidenceNotes confidenceNotes) {
        if (confidenceNotes == null || confidenceNotes.overallConfidence() == null) {
            return "unknown";
        }
        return confidenceNotes.overallConfidence().name();
    }
}
