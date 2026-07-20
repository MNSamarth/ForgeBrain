package com.forgebrain.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.AiGatewayConfig;
import com.forgebrain.backend.exceptions.AiGatewayException;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real {@link AiGateway}. Composes {@link PromptRegistry} (model routing), a plain {@link
 * RetryExecutor} it owns directly (mirrors {@code QualityScorer}'s "plain logic class owned by
 * its orchestrator" convention), a bounded-wait timeout around the unmodified {@link
 * VertexAiClient}, response parsing/validation via the shared {@link ObjectMapper}, a pluggable
 * {@link AiResponseCache}, and a pluggable {@link PromptMetricsRecorder}.
 *
 * <p>Failure classification mirrors what every {@code Vertex*ServiceImpl} used to do itself: a
 * blank model id or a {@link ConfigurationException} from {@code VertexAiClient} (blank project
 * id, or any other "not usable at all" signal) is never retried and surfaces as {@link
 * AiGatewayException.Reason#CONFIGURATION}; everything else (a thrown exception, a timeout,
 * malformed JSON, or a failed {@link AiPromptExecution#validator()}) is retried per {@link
 * AiGatewayConfig}, then surfaces as {@link AiGatewayException.Reason#EXECUTION_FAILED}.
 */
@Component
public class AiGatewayImpl implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayImpl.class);

    private final VertexAiClient vertexAiClient;
    private final PromptRegistry promptRegistry;
    private final AiGatewayConfig config;
    private final AiResponseCache cache;
    private final PromptMetricsRecorder metrics;
    private final ObjectMapper objectMapper;
    private final RetryExecutor retryExecutor;
    private final ExecutorService callExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AiGatewayImpl(VertexAiClient vertexAiClient, PromptRegistry promptRegistry, AiGatewayConfig config,
            AiResponseCache cache, PromptMetricsRecorder metrics, ObjectMapper objectMapper) {
        this.vertexAiClient = vertexAiClient;
        this.promptRegistry = promptRegistry;
        this.config = config;
        this.cache = cache;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.retryExecutor = new RetryExecutor(config);
    }

    @Override
    public <T> AiGatewayResult<T> execute(AiPromptExecution<T> execution) {
        PromptDefinition definition = resolveDefinition(execution.promptName());
        Instant start = Instant.now();

        if (isBlank(definition.modelId())) {
            metrics.recordFailure(definition.name(), Duration.between(start, Instant.now()), 0);
            throw new AiGatewayException(AiGatewayException.Reason.CONFIGURATION, definition.name(),
                    "AI gateway prompt '" + definition.name() + "' has no model configured.", null);
        }

        if (config.cacheEnabled()) {
            Optional<T> cached = tryCache(definition, execution, start);
            if (cached.isPresent()) {
                return new AiGatewayResult<>(cached.get(), definition.modelId(), definition.name(),
                        definition.version(), true, 0, Duration.between(start, Instant.now()));
            }
        }

        RetryExecutor.Outcome<Attempt<T>> outcome = retryExecutor.execute(() -> attemptOnce(definition, execution),
                failure -> !(failure instanceof ConfigurationException));
        Duration duration = Duration.between(start, Instant.now());

        if (!outcome.success()) {
            metrics.recordFailure(definition.name(), duration, outcome.retries());
            Exception failure = outcome.failure();
            if (failure instanceof ConfigurationException) {
                throw new AiGatewayException(AiGatewayException.Reason.CONFIGURATION, definition.name(),
                        "AI gateway prompt '" + definition.name() + "' is not usable: " + failure.getMessage(),
                        failure);
            }
            throw new AiGatewayException(AiGatewayException.Reason.EXECUTION_FAILED, definition.name(),
                    "AI gateway prompt '" + definition.name() + "' failed after " + (outcome.retries() + 1)
                            + " attempt(s): " + (failure != null ? failure.getMessage() : "unknown error"), failure);
        }

        Attempt<T> attempt = outcome.result();
        if (config.cacheEnabled()) {
            cache.put(cacheKey(definition, execution.promptText()), attempt.rawText());
        }
        metrics.recordSuccess(definition.name(), duration, outcome.retries(),
                estimateTokens(execution.promptText(), attempt.rawText()), false);
        return new AiGatewayResult<>(attempt.content(), attempt.modelId(), definition.name(), definition.version(),
                false, outcome.retries(), duration);
    }

    @Override
    public void recordFallbackUsed(String promptName) {
        metrics.recordFallback(promptName);
    }

    @Override
    public java.util.List<PromptMetrics> metricsSnapshot() {
        return metrics.snapshotAll();
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdown();
    }

    private PromptDefinition resolveDefinition(String promptName) {
        try {
            return promptRegistry.get(promptName);
        } catch (RuntimeException e) {
            throw new AiGatewayException(AiGatewayException.Reason.CONFIGURATION, promptName,
                    "AI gateway could not resolve prompt '" + promptName + "': " + e.getMessage(), e);
        }
    }

    /** Best-effort cache lookup; a corrupt/stale cached entry is logged and ignored, never fatal. */
    private <T> Optional<T> tryCache(PromptDefinition definition, AiPromptExecution<T> execution, Instant start) {
        Optional<String> cached = cache.get(cacheKey(definition, execution.promptText()));
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            T content = parseAndValidate(cached.get(), execution);
            metrics.recordSuccess(definition.name(), Duration.between(start, Instant.now()), 0,
                    estimateTokens(execution.promptText(), cached.get()), true);
            log.debug("AI gateway cache hit for prompt '{}'.", definition.name());
            return Optional.of(content);
        } catch (Exception e) {
            log.warn("Cached AI gateway response for prompt '{}' failed to parse/validate; ignoring the cache"
                    + " entry and calling Vertex AI.", definition.name(), e);
            return Optional.empty();
        }
    }

    private <T> Attempt<T> attemptOnce(PromptDefinition definition, AiPromptExecution<T> execution) throws Exception {
        VertexAiPromptRequest request = new VertexAiPromptRequest(definition.modelId(), execution.promptText(),
                execution.variables(), definition.temperature(), definition.maxOutputTokens(),
                definition.responseMimeType());
        VertexAiPromptResponse response = callWithTimeout(request);
        T content = parseAndValidate(response.rawText(), execution);
        return new Attempt<>(content, response.rawText(), response.modelId());
    }

    private <T> T parseAndValidate(String rawText, AiPromptExecution<T> execution) throws Exception {
        T content = objectMapper.readValue(rawText, execution.responseType());
        if (execution.validator() != null) {
            execution.validator().accept(content);
        }
        return content;
    }

    private VertexAiPromptResponse callWithTimeout(VertexAiPromptRequest request) throws Exception {
        Future<VertexAiPromptResponse> future = callExecutor.submit(() -> vertexAiClient.generate(request));
        try {
            return future.get(config.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private static long estimateTokens(String promptText, String responseText) {
        // A documented proxy, not a real tokenizer — roughly 4 characters per token, the same
        // rough-order-of-magnitude heuristic commonly used when no real tokenizer is wired up.
        int promptLength = promptText != null ? promptText.length() : 0;
        int responseLength = responseText != null ? responseText.length() : 0;
        return Math.round((promptLength + responseLength) / 4.0);
    }

    private static String cacheKey(PromptDefinition definition, String promptText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((definition.name() + ' ' + definition.modelId() + ' ' + promptText)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest is not available", e);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record Attempt<T>(T content, String rawText, String modelId) {
    }
}
