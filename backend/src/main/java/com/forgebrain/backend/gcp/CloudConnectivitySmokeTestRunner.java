package com.forgebrain.backend.gcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The "one command" cloud connectivity smoke test — mirrors {@code ReelJobCommandLineRunner}'s
 * exact pattern. Disabled by default (see {@code forgebrain.gcp.smoke-test-on-startup} in
 * application.yml); running it against a real project also requires {@code
 * forgebrain.gcp.vertex-ai-enabled}/{@code gcs-enabled} (e.g. via the {@code cloud} profile) —
 * with both off, this still runs but every check reports {@code SKIPPED}, never touching the
 * network:
 *
 * <pre>{@code
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=cloud \
 *     -Dspring-boot.run.arguments=--forgebrain.gcp.smoke-test-on-startup=true
 * }</pre>
 *
 * <p>Never throws — {@link CloudConnectivityChecker}'s methods always return a result. The
 * application keeps running afterward; this runner does not call {@code System.exit}.
 */
@Component
@ConditionalOnProperty(name = "forgebrain.gcp.smoke-test-on-startup", havingValue = "true")
public class CloudConnectivitySmokeTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudConnectivitySmokeTestRunner.class);

    private final CloudConnectivityChecker checker;

    public CloudConnectivitySmokeTestRunner(CloudConnectivityChecker checker) {
        this.checker = checker;
    }

    @Override
    public void run(String... args) {
        log.info("forgebrain.gcp.smoke-test-on-startup=true — checking live cloud connectivity...");
        CloudConnectivityResult vertexAiResult = checker.checkVertexAi();
        log.info("  vertex-ai: {} — {}", vertexAiResult.status(), vertexAiResult.message());
        CloudConnectivityResult gcsResult = checker.checkGcs();
        log.info("  gcs:       {} — {}", gcsResult.status(), gcsResult.message());
    }
}
