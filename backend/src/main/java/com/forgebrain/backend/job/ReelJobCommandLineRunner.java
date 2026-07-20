package com.forgebrain.backend.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The "one command" local execution path for the job layer, mirroring {@code
 * PipelineCommandLineRunner}/{@code ReelExportCommandLineRunner}'s pattern exactly. Disabled by
 * default (see {@code forgebrain.jobs.run-on-startup} in application.yml):
 *
 * <pre>{@code
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.jobs.run-on-startup=true
 * }</pre>
 *
 * <p>Unlike the other two runners, this one never throws on failure — {@link
 * ReelJobService#submitJob()} always returns a {@link ReelJob}, so this runner just logs
 * whichever status it landed in.
 */
@Component
@ConditionalOnProperty(name = "forgebrain.jobs.run-on-startup", havingValue = "true")
public class ReelJobCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReelJobCommandLineRunner.class);

    private final ReelJobService reelJobService;

    public ReelJobCommandLineRunner(ReelJobService reelJobService) {
        this.reelJobService = reelJobService;
    }

    @Override
    public void run(String... args) {
        log.info("forgebrain.jobs.run-on-startup=true — submitting one reel job...");
        ReelJob job = reelJobService.submitJob();
        if (job.status() == ReelJob.Status.COMPLETED) {
            log.info("Reel job '{}' completed for topic '{}' ({}).", job.jobId(), job.topicTitle(), job.topicId());
            job.outputFiles().forEach((category, ref) -> log.info("  {}: {}", category, ref));
        } else {
            log.error("Reel job '{}' ended with status {}: {}", job.jobId(), job.status(), job.failureReason());
        }
    }
}
