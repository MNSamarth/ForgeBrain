package com.forgebrain.backend.pipeline;

import com.forgebrain.backend.models.AssetManifest;
import com.forgebrain.backend.models.ContentStrategy;
import com.forgebrain.backend.models.Lesson;
import com.forgebrain.backend.models.PublishingPackage;
import com.forgebrain.backend.models.QualityScore;
import com.forgebrain.backend.models.RenderJob;
import com.forgebrain.backend.models.ResearchResult;
import com.forgebrain.backend.models.ReviewResult;
import com.forgebrain.backend.models.Script;
import com.forgebrain.backend.models.Storyboard;
import com.forgebrain.backend.models.SubtitleResult;
import com.forgebrain.backend.models.TopicSelectionDecision;
import com.forgebrain.backend.models.VideoPackage;
import com.forgebrain.backend.models.VoiceResult;
import com.forgebrain.backend.shared.PipelineStage;

/**
 * Accumulates one topic's artifacts as it moves through the pipeline, for the {@link
 * PipelineOrchestrator}'s own bookkeeping and for {@link
 * com.forgebrain.backend.entities.PipelineRunEntity} persistence.
 *
 * <p>This is deliberately <b>not</b> what individual {@link com.forgebrain.backend.services}
 * interfaces take as their parameter — each service's contract stays explicit and
 * self-documenting (see {@link com.forgebrain.backend.services package-info}). This type is
 * for the orchestrator that calls those services in sequence and needs to hold onto every
 * result along the way.
 *
 * <p>Mutable by design: a pipeline run's context is filled in one stage at a time as the run
 * progresses, not constructed all at once.
 */
public final class PipelineContext {

    private final String topicId;
    private PipelineRunStatus status = PipelineRunStatus.NOT_STARTED;
    private PipelineStage currentStage;

    private TopicSelectionDecision topicSelectionDecision;
    private ResearchResult researchResult;
    private Lesson lesson;
    private ContentStrategy contentStrategy;
    private Script script;
    private Storyboard storyboard;
    private VoiceResult voiceResult;
    private SubtitleResult subtitleResult;
    private AssetManifest assetManifest;
    private RenderJob renderJob;
    private VideoPackage videoPackage;
    private QualityScore qualityScore;
    private ReviewResult reviewResult;
    private PublishingPackage publishingPackage;

    public PipelineContext(String topicId) {
        this.topicId = topicId;
    }

    public String topicId() {
        return topicId;
    }

    public PipelineRunStatus status() {
        return status;
    }

    public void status(PipelineRunStatus status) {
        this.status = status;
    }

    public PipelineStage currentStage() {
        return currentStage;
    }

    public void currentStage(PipelineStage currentStage) {
        this.currentStage = currentStage;
    }

    public TopicSelectionDecision topicSelectionDecision() {
        return topicSelectionDecision;
    }

    public void topicSelectionDecision(TopicSelectionDecision v) {
        this.topicSelectionDecision = v;
    }

    public ResearchResult researchResult() {
        return researchResult;
    }

    public void researchResult(ResearchResult v) {
        this.researchResult = v;
    }

    public Lesson lesson() {
        return lesson;
    }

    public void lesson(Lesson v) {
        this.lesson = v;
    }

    public ContentStrategy contentStrategy() {
        return contentStrategy;
    }

    public void contentStrategy(ContentStrategy v) {
        this.contentStrategy = v;
    }

    public Script script() {
        return script;
    }

    public void script(Script v) {
        this.script = v;
    }

    public Storyboard storyboard() {
        return storyboard;
    }

    public void storyboard(Storyboard v) {
        this.storyboard = v;
    }

    public VoiceResult voiceResult() {
        return voiceResult;
    }

    public void voiceResult(VoiceResult v) {
        this.voiceResult = v;
    }

    public SubtitleResult subtitleResult() {
        return subtitleResult;
    }

    public void subtitleResult(SubtitleResult v) {
        this.subtitleResult = v;
    }

    public AssetManifest assetManifest() {
        return assetManifest;
    }

    public void assetManifest(AssetManifest v) {
        this.assetManifest = v;
    }

    public RenderJob renderJob() {
        return renderJob;
    }

    public void renderJob(RenderJob v) {
        this.renderJob = v;
    }

    public VideoPackage videoPackage() {
        return videoPackage;
    }

    public void videoPackage(VideoPackage v) {
        this.videoPackage = v;
    }

    public QualityScore qualityScore() {
        return qualityScore;
    }

    public void qualityScore(QualityScore v) {
        this.qualityScore = v;
    }

    public ReviewResult reviewResult() {
        return reviewResult;
    }

    public void reviewResult(ReviewResult v) {
        this.reviewResult = v;
    }

    public PublishingPackage publishingPackage() {
        return publishingPackage;
    }

    public void publishingPackage(PublishingPackage v) {
        this.publishingPackage = v;
    }
}
