package com.forgebrain.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.AiGatewayConfig;
import com.forgebrain.backend.exceptions.AiGatewayException;
import com.forgebrain.backend.exceptions.ConfigurationException;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiGatewayImpl} — per this mission's Part 7 ("routing", "retry", "cache",
 * "validation", "fallback", "metrics"). {@link VertexAiClient} is mocked; no real network call.
 */
class AiGatewayImplTest {

    private record TestContent(String value) {
    }

    private VertexAiClient vertexAiClient;
    private PromptRegistry registry;
    private InMemoryAiResponseCache cache;
    private InMemoryPromptMetricsRecorder metrics;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        vertexAiClient = mock(VertexAiClient.class);
        registry = mock(PromptRegistry.class);
        cache = new InMemoryAiResponseCache();
        metrics = new InMemoryPromptMetricsRecorder();
        objectMapper = new ObjectMapper();
    }

    private AiGatewayImpl gateway(AiGatewayConfig config) {
        return new AiGatewayImpl(vertexAiClient, registry, config, cache, metrics, objectMapper);
    }

    private static AiGatewayConfig config(int maxRetries, boolean cacheEnabled) {
        return new AiGatewayConfig(maxRetries, 1, 2.0, 5000, cacheEnabled);
    }

    private static PromptDefinition definition(String modelId) {
        return new PromptDefinition("test", "1.0.0-test", "purpose", modelId, 0.4, 100, "application/json");
    }

    private AiPromptExecution<TestContent> execution() {
        return new AiPromptExecution<>("test", "prompt text", Map.of("k", "v"), TestContent.class);
    }

    // -------------------------------------------------------------------------------- routing

    @Test
    void resolvesTheModelFromTheRegisteredPromptDefinitionAndSendsItToTheClient() {
        when(registry.get("test")).thenReturn(definition("routed-model"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"ok\"}", "routed-model", "STOP"));

        AiGatewayResult<TestContent> result = gateway(config(0, false)).execute(execution());

        assertThat(result.modelId()).isEqualTo("routed-model");
        assertThat(result.content().value()).isEqualTo("ok");
        org.mockito.ArgumentCaptor<VertexAiPromptRequest> captor =
                org.mockito.ArgumentCaptor.forClass(VertexAiPromptRequest.class);
        verify(vertexAiClient).generate(captor.capture());
        assertThat(captor.getValue().modelId()).isEqualTo("routed-model");
    }

    @Test
    void switchingTheRegisteredModelChangesRoutingWithoutAnyGatewayCodeChange() {
        when(registry.get("test")).thenReturn(definition("model-a"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"a\"}", "model-a", "STOP"));
        assertThat(gateway(config(0, false)).execute(execution()).modelId()).isEqualTo("model-a");

        when(registry.get("test")).thenReturn(definition("model-b"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"b\"}", "model-b", "STOP"));
        assertThat(gateway(config(0, false)).execute(execution()).modelId()).isEqualTo("model-b");
    }

    @Test
    void blankModelIdFailsFastAsConfigurationWithoutCallingTheClient() {
        when(registry.get("test")).thenReturn(definition(""));

        assertThatThrownBy(() -> gateway(config(2, false)).execute(execution()))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(e -> assertThat(((AiGatewayException) e).reason())
                        .isEqualTo(AiGatewayException.Reason.CONFIGURATION));
        verifyNoInteractions(vertexAiClient);
    }

    // ---------------------------------------------------------------------------------- retry

    @Test
    void retriesATransientClientFailureThenSucceeds() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        AtomicInteger calls = new AtomicInteger();
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class))).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException("transient failure");
            }
            return new VertexAiPromptResponse("{\"value\":\"ok\"}", "model-1", "STOP");
        });

        AiGatewayResult<TestContent> result = gateway(config(2, false)).execute(execution());

        assertThat(result.retries()).isEqualTo(1);
        assertThat(result.content().value()).isEqualTo("ok");
        verify(vertexAiClient, times(2)).generate(any());
    }

    @Test
    void aConfigurationExceptionFromTheClientIsNeverRetried() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new ConfigurationException("forgebrain.vertex-ai.project-id is not configured"));

        assertThatThrownBy(() -> gateway(config(3, false)).execute(execution()))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(e -> assertThat(((AiGatewayException) e).reason())
                        .isEqualTo(AiGatewayException.Reason.CONFIGURATION));
        verify(vertexAiClient, times(1)).generate(any());
    }

    @Test
    void exhaustsConfiguredRetriesThenThrowsExecutionFailed() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenThrow(new RuntimeException("always fails"));

        assertThatThrownBy(() -> gateway(config(2, false)).execute(execution()))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(e -> assertThat(((AiGatewayException) e).reason())
                        .isEqualTo(AiGatewayException.Reason.EXECUTION_FAILED));
        verify(vertexAiClient, times(3)).generate(any());
    }

    // ----------------------------------------------------------------------------- validation

    @Test
    void malformedJsonIsRetriedThenFails() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("not json at all", "model-1", "STOP"));

        assertThatThrownBy(() -> gateway(config(1, false)).execute(execution()))
                .isInstanceOf(AiGatewayException.class);
        verify(vertexAiClient, times(2)).generate(any());
    }

    @Test
    void aFailingValidatorIsTreatedTheSameAsAParseFailure() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"bad\"}", "model-1", "STOP"));
        AiPromptExecution<TestContent> execution = new AiPromptExecution<>("test", "prompt text", Map.of(),
                TestContent.class, content -> {
                    if ("bad".equals(content.value())) {
                        throw new IllegalArgumentException("bad content");
                    }
                });

        assertThatThrownBy(() -> gateway(config(1, false)).execute(execution))
                .isInstanceOf(AiGatewayException.class);
        verify(vertexAiClient, times(2)).generate(any());
    }

    @Test
    void aPassingValidatorLetsAValidResponseThrough() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"good\"}", "model-1", "STOP"));
        AiPromptExecution<TestContent> execution = new AiPromptExecution<>("test", "prompt text", Map.of(),
                TestContent.class, content -> assertThat(content.value()).isEqualTo("good"));

        AiGatewayResult<TestContent> result = gateway(config(0, false)).execute(execution);

        assertThat(result.content().value()).isEqualTo("good");
    }

    // ---------------------------------------------------------------------------------- cache

    @Test
    void anIdenticalSecondRequestIsServedFromCacheWithoutCallingTheClientAgain() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"ok\"}", "model-1", "STOP"));
        AiGatewayImpl gateway = gateway(config(0, true));

        AiGatewayResult<TestContent> first = gateway.execute(execution());
        AiGatewayResult<TestContent> second = gateway.execute(execution());

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.content().value()).isEqualTo("ok");
        verify(vertexAiClient, times(1)).generate(any());
    }

    @Test
    void cachingDisabledCallsTheClientEveryTime() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"ok\"}", "model-1", "STOP"));
        AiGatewayImpl gateway = gateway(config(0, false));

        gateway.execute(execution());
        gateway.execute(execution());

        verify(vertexAiClient, times(2)).generate(any());
    }

    @Test
    void aDifferentPromptTextIsNotServedFromTheOtherRequestsCacheEntry() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"ok\"}", "model-1", "STOP"));
        AiGatewayImpl gateway = gateway(config(0, true));

        gateway.execute(execution());
        AiGatewayResult<TestContent> second = gateway.execute(
                new AiPromptExecution<>("test", "a completely different prompt", Map.of(), TestContent.class));

        assertThat(second.cacheHit()).isFalse();
        verify(vertexAiClient, times(2)).generate(any());
    }

    // ------------------------------------------------------------------------------- fallback

    @Test
    void recordFallbackUsedIncrementsThatPromptsFallbackMetric() {
        AiGatewayImpl gateway = gateway(config(0, false));

        gateway.recordFallbackUsed("research");
        gateway.recordFallbackUsed("research");

        assertThat(metrics.snapshot("research").fallbacks()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------------- metrics

    @Test
    void metricsSnapshotReflectsSuccessesAndFailuresPerPrompt() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("{\"value\":\"ok\"}", "model-1", "STOP"))
                .thenThrow(new RuntimeException("boom"));
        AiGatewayImpl gateway = gateway(config(0, false));

        gateway.execute(execution());
        assertThatThrownBy(() -> gateway.execute(execution())).isInstanceOf(AiGatewayException.class);

        PromptMetrics snapshot = metrics.snapshot("test");
        assertThat(snapshot.invocations()).isEqualTo(2);
        assertThat(snapshot.failures()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------------- timeout

    @Test
    void aCallThatExceedsTheConfiguredTimeoutIsTreatedAsARetryableFailure() {
        when(registry.get("test")).thenReturn(definition("model-1"));
        when(vertexAiClient.generate(any(VertexAiPromptRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(2000);
            return new VertexAiPromptResponse("{\"value\":\"too-late\"}", "model-1", "STOP");
        });
        AiGatewayConfig shortTimeout = new AiGatewayConfig(0, 1, 2.0, 50, false);

        assertThatThrownBy(() -> gateway(shortTimeout).execute(execution()))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(e -> assertThat(((AiGatewayException) e).reason())
                        .isEqualTo(AiGatewayException.Reason.EXECUTION_FAILED));
    }
}
