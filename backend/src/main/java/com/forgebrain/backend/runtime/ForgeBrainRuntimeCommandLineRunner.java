package com.forgebrain.backend.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The "one command" entry point for the whole Runtime — {@code forgebrain run}'s Spring Boot
 * equivalent — mirroring {@code ReelJobCommandLineRunner}'s pattern exactly. Disabled by default
 * (see {@code forgebrain.runtime.run-on-startup} in application.yml):
 *
 * <pre>{@code
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--forgebrain.runtime.run-on-startup=true
 * }</pre>
 *
 * <p>Never throws: {@link ForgeBrainRuntime#run()} tolerates every individual reel failure
 * internally and always returns a {@link RuntimeReport}. The application keeps running
 * afterward; this runner does not call {@code System.exit}.
 */
@Component
@ConditionalOnProperty(name = "forgebrain.runtime.run-on-startup", havingValue = "true")
public class ForgeBrainRuntimeCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ForgeBrainRuntimeCommandLineRunner.class);

    private final ForgeBrainRuntime runtime;

    public ForgeBrainRuntimeCommandLineRunner(ForgeBrainRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void run(String... args) {
        log.info("forgebrain.runtime.run-on-startup=true — starting the ForgeBrain Runtime...");
        RuntimeReport report = runtime.run();
        log.info("Runtime '{}' finished in {}: {}/{} reel(s) completed, {} failed.", report.runtimeId(),
                report.duration(), report.reelsCompleted(), report.reelsRequested(), report.reelsFailed());
        report.reelExecutions().forEach(reel -> log.info("  {} -> {} (topic={}, review={}, publish={})",
                reel.jobId(), reel.status(), reel.topicId(), reel.reviewVerdict(), reel.publishingStatus()));
        if (!report.warnings().isEmpty()) {
            report.warnings().forEach(warning -> log.warn("  warning: {}", warning));
        }
    }
}
