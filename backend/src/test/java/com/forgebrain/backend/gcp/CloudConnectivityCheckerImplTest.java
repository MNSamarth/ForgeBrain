package com.forgebrain.backend.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forgebrain.backend.config.GcpConfig;
import com.forgebrain.backend.config.VertexAiConfig;
import com.forgebrain.backend.job.OutputStorage;
import com.forgebrain.backend.vertex.VertexAiClient;
import com.forgebrain.backend.vertex.VertexAiPromptRequest;
import com.forgebrain.backend.vertex.VertexAiPromptResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CloudConnectivityCheckerImpl} — per this mission's Part 5 ("smoke-test
 * gating"). No real network call, no real credentials; {@link VertexAiClient}/{@link
 * OutputStorage} are mocked.
 */
class CloudConnectivityCheckerImplTest {

    private static GcpConfig config(boolean vertexAiEnabled, boolean gcsEnabled) {
        return new GcpConfig("forgebrain-prod", "us-central1", "forgebrain-artifacts", vertexAiEnabled, gcsEnabled);
    }

    private static VertexAiConfig vertexAiConfig() {
        return new VertexAiConfig("forgebrain-prod", "us-central1", "gemini-2.0-flash-001", "gemini-2.0-pro-001",
                "gemini-2.0-pro-001", "gemini-2.0-flash-001", 0.4, 2048, "application/json", 0.4, 2048,
                "application/json", 0.4, 2048, "application/json", "gemini-2.5-pro", 0.4, 2048, "application/json");
    }

    // ------------------------------------------------------------------------------ Vertex AI

    @Test
    void skipsTheVertexAiCheckWithoutTouchingTheClientWhenDisabled() {
        VertexAiClient client = mock(VertexAiClient.class);
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(false, false), client,
                vertexAiConfig(), mock(OutputStorage.class));

        CloudConnectivityResult result = checker.checkVertexAi();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.SKIPPED);
        assertThat(result.target()).isEqualTo("vertex-ai");
        verifyNoInteractions(client);
    }

    @Test
    void reportsSuccessWhenTheClientRespondsAndUsesACheapSmokeTestRequest() {
        VertexAiClient client = mock(VertexAiClient.class);
        when(client.generate(any(VertexAiPromptRequest.class)))
                .thenReturn(new VertexAiPromptResponse("OK", "gemini-2.0-flash-001", "STOP"));
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(true, false), client,
                vertexAiConfig(), mock(OutputStorage.class));

        CloudConnectivityResult result = checker.checkVertexAi();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.SUCCESS);
        assertThat(result.message()).contains("gemini-2.0-flash-001");
        org.mockito.ArgumentCaptor<VertexAiPromptRequest> captor =
                org.mockito.ArgumentCaptor.forClass(VertexAiPromptRequest.class);
        verify(client).generate(captor.capture());
        assertThat(captor.getValue().modelId()).isEqualTo("gemini-2.0-flash-001");
        assertThat(captor.getValue().maxOutputTokens()).isLessThanOrEqualTo(16);
    }

    @Test
    void reportsFailureWhenTheClientThrows() {
        VertexAiClient client = mock(VertexAiClient.class);
        when(client.generate(any(VertexAiPromptRequest.class))).thenThrow(new RuntimeException("no credentials"));
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(true, false), client,
                vertexAiConfig(), mock(OutputStorage.class));

        CloudConnectivityResult result = checker.checkVertexAi();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.FAILURE);
        assertThat(result.message()).contains("no credentials");
    }

    // ------------------------------------------------------------------------------------ GCS

    @Test
    void skipsTheGcsCheckWithoutTouchingOutputStorageWhenDisabled() {
        OutputStorage storage = mock(OutputStorage.class);
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(false, false),
                mock(VertexAiClient.class), vertexAiConfig(), storage);

        CloudConnectivityResult result = checker.checkGcs();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.SKIPPED);
        assertThat(result.target()).isEqualTo("gcs");
        verifyNoInteractions(storage);
    }

    @Test
    void reportsSuccessWhenOutputStorageWritesTheSmokeTestFile() {
        OutputStorage storage = mock(OutputStorage.class);
        when(storage.store(org.mockito.ArgumentMatchers.eq("cloud-smoke-test"), any(Path.class)))
                .thenReturn("gs://forgebrain-artifacts/cloud-smoke-test/forgebrain-cloud-smoke-test.txt");
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(false, true),
                mock(VertexAiClient.class), vertexAiConfig(), storage);

        CloudConnectivityResult result = checker.checkGcs();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.SUCCESS);
        assertThat(result.message()).contains("gs://forgebrain-artifacts");
    }

    @Test
    void reportsFailureWhenOutputStorageThrows() {
        OutputStorage storage = mock(OutputStorage.class);
        when(storage.store(org.mockito.ArgumentMatchers.anyString(), any(Path.class)))
                .thenThrow(new RuntimeException("bucket not found"));
        CloudConnectivityCheckerImpl checker = new CloudConnectivityCheckerImpl(config(false, true),
                mock(VertexAiClient.class), vertexAiConfig(), storage);

        CloudConnectivityResult result = checker.checkGcs();

        assertThat(result.status()).isEqualTo(CloudConnectivityResult.Status.FAILURE);
        assertThat(result.message()).contains("bucket not found");
    }
}
