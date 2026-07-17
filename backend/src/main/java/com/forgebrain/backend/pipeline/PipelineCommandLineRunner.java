package com.forgebrain.backend.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The "one command" local execution path this phase's mission asks for. Disabled by default
 * (see {@code forgebrain.pipeline.run-on-startup} in application.yml) so a normal {@code
 * ./mvnw spring-boot:run} just starts the web server with its health endpoint — enable it to
 * also run one full pipeline slice on startup:
 *
 * <pre>{@code
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.pipeline.run-on-startup=true
 * }</pre>
 *
 * <p>The application keeps running after the pipeline completes (or fails); this runner does
 * not call {@code System.exit}, so {@code /health} remains reachable afterward. See {@code
 * NEXT_EXECUTION.md} for the equivalent, more repeatable way to exercise this same path via
 * {@code ./mvnw test -Dtest=PipelineOrchestratorImplIT}.
 */
@Component
@ConditionalOnProperty(name = "forgebrain.pipeline.run-on-startup", havingValue = "true")
public class PipelineCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineCommandLineRunner.class);

    private final PipelineOrchestrator orchestrator;

    public PipelineCommandLineRunner(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        log.info("forgebrain.pipeline.run-on-startup=true — running one full pipeline slice...");
        try {
            PipelineResult result = orchestrator.runFullPipeline();
            log.info("Pipeline complete for topic '{}' ({})", result.topicTitle(), result.topicId());
            log.info("  hook_type={}, teaching_style={}", result.contentStrategy().hookType(), result.contentStrategy().teachingStyle());
            log.info("  script: {} words, ~{}s", result.script().wordCount(), result.script().estimatedDurationSeconds());
            log.info("  storyboard: {} scenes, {}s total", result.storyboard().sceneCount(), result.storyboard().totalDurationSeconds());
            log.info("  hook: \"{}\"", result.script().hook());
            log.info("Result saved under forgebrain.local-storage.pipeline-output-directory.");
        } catch (Exception e) {
            log.error("Pipeline run failed: {}", e.getMessage(), e);
        }
    }
}
