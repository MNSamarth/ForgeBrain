package com.forgebrain.backend.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The "one command" local execution path for a full reel export, mirroring {@link
 * PipelineCommandLineRunner}'s pattern exactly. Disabled by default (see {@code
 * forgebrain.reel-export.run-on-startup} in application.yml):
 *
 * <pre>{@code
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.reel-export.run-on-startup=true
 * }</pre>
 *
 * <p>Requires a local {@code ffmpeg} binary — see backend/README.md's "Storyboard to MP4"
 * section. The application keeps running afterward; this runner does not call {@code
 * System.exit}.
 */
@Component
@ConditionalOnProperty(name = "forgebrain.reel-export.run-on-startup", havingValue = "true")
public class ReelExportCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReelExportCommandLineRunner.class);

    private final ReelExportService reelExportService;

    public ReelExportCommandLineRunner(ReelExportService reelExportService) {
        this.reelExportService = reelExportService;
    }

    @Override
    public void run(String... args) {
        log.info("forgebrain.reel-export.run-on-startup=true — exporting one full reel...");
        try {
            ReelExportResult result = reelExportService.exportReel();
            log.info("Reel export complete for topic '{}' ({})", result.topicTitle(), result.topicId());
            log.info("  video:    {}", result.videoFilePath());
            log.info("  metadata: {}", result.metadataFilePath());
            log.info("  subtitles:{}", result.subtitleFilePath());
            log.info("  report:   {}", result.reportFilePath());
        } catch (Exception e) {
            log.error("Reel export failed: {}", e.getMessage(), e);
        }
    }
}
