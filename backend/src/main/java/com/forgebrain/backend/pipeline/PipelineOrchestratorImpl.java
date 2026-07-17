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
import com.forgebrain.backend.shared.PipelineStage;
import java.util.Map;
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

    private final Map<String, PipelineContext> runsByTopicId = new ConcurrentHashMap<>();

    public PipelineOrchestratorImpl(CurriculumLoader curriculumLoader, MemoryService memoryService,
            com.forgebrain.backend.services.TopicSelector topicSelector, ResearchService researchService,
            LessonService lessonService, ContentDirectorService contentDirectorService,
            ScriptService scriptService, StoryboardService storyboardService, PipelineResultStore resultStore) {
        this.curriculumLoader = curriculumLoader;
        this.memoryService = memoryService;
        this.topicSelector = topicSelector;
        this.researchService = researchService;
        this.lessonService = lessonService;
        this.contentDirectorService = contentDirectorService;
        this.scriptService = scriptService;
        this.storyboardService = storyboardService;
        this.resultStore = resultStore;
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
        TopicSelectionDecision decision = topicSelector.selectNextTopic(
                TopicSelectionDecision.Mode.NEXT_TOPIC, memoryService.loadCurrentState(), java.time.Instant.now());

        PipelineContext context = startRun(decision);
        context.status(PipelineRunStatus.IN_PROGRESS);

        int iterations = 0;
        while (context.storyboard() == null) {
            if (++iterations > MAX_ADVANCE_ITERATIONS) {
                throw new PipelineStageException(String.valueOf(context.currentStage()),
                        "Pipeline did not reach a storyboard after " + MAX_ADVANCE_ITERATIONS + " advance() calls — likely stuck.");
            }
            advance(context);
        }

        PipelineResult result = PipelineResult.fromContext(context);
        resultStore.save(result);
        return result;
    }
}
