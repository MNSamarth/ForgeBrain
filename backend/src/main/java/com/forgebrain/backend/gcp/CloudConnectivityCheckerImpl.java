package com.forgebrain.backend.gcp;

import com.forgebrain.backend.config.GcpConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.job.OutputStorage;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link CloudConnectivityChecker}. Deliberately calls {@link VertexAiClient} directly
 * rather than going through {@code AiGateway} — a connectivity smoke test wants one narrow,
 * unretried, uncached attempt at the lowest real layer, not a re-exercise of the gateway's own
 * routing/retry/cache logic (already thoroughly covered by {@code AiGatewayImplTest} and friends).
 * The GCS check reuses the real {@link OutputStorage} bean exactly as the rest of the job layer
 * does — whichever implementation {@code OutputStorageFactory} resolved for the active profile.
 */
@Component
public class CloudConnectivityCheckerImpl implements CloudConnectivityChecker {

    private static final Logger log = LoggerFactory.getLogger(CloudConnectivityCheckerImpl.class);
    private static final String SMOKE_TEST_PROMPT = "Reply with exactly one word: OK";

    private final GcpConfig gcpConfig;
    private final VertexAiClient vertexAiClient;
    private final VertexAiConfig vertexAiConfig;
    private final OutputStorage outputStorage;

    public CloudConnectivityCheckerImpl(GcpConfig gcpConfig, VertexAiClient vertexAiClient,
            VertexAiConfig vertexAiConfig, OutputStorage outputStorage) {
        this.gcpConfig = gcpConfig;
        this.vertexAiClient = vertexAiClient;
        this.vertexAiConfig = vertexAiConfig;
        this.outputStorage = outputStorage;
    }

    @Override
    public CloudConnectivityResult checkVertexAi() {
        if (!gcpConfig.vertexAiEnabled()) {
            return CloudConnectivityResult.skipped("vertex-ai", "forgebrain.gcp.vertex-ai-enabled is false.");
        }
        try {
            VertexAiPromptRequest request = new VertexAiPromptRequest(vertexAiConfig.researchModel(),
                    SMOKE_TEST_PROMPT, Map.of(), 0.0, 8, "text/plain");
            VertexAiPromptResponse response = vertexAiClient.generate(request);
            return CloudConnectivityResult.success("vertex-ai", "Model '" + response.modelId()
                    + "' responded (finish_reason=" + response.finishReason() + ").");
        } catch (Exception e) {
            log.warn("Vertex AI connectivity smoke test failed: {}", e.getMessage(), e);
            return CloudConnectivityResult.failure("vertex-ai", "Vertex AI smoke test failed: " + e.getMessage());
        }
    }

    @Override
    public CloudConnectivityResult checkGcs() {
        if (!gcpConfig.gcsEnabled()) {
            return CloudConnectivityResult.skipped("gcs", "forgebrain.gcp.gcs-enabled is false.");
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("forgebrain-cloud-smoke-test", ".txt");
            Files.writeString(tempFile, "ForgeBrain cloud connectivity smoke test at " + Instant.now());
            String reference = outputStorage.store("cloud-smoke-test", tempFile);
            return CloudConnectivityResult.success("gcs", "Wrote smoke-test object to " + reference);
        } catch (IOException | RuntimeException e) {
            log.warn("GCS connectivity smoke test failed: {}", e.getMessage(), e);
            return CloudConnectivityResult.failure("gcs", "GCS smoke test failed: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a local temp file; not worth failing the check over.
                }
            }
        }
    }
}
