package com.forgebrain.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link RuntimeReportWriterImpl} — per this mission's Part 6 ("write runtime
 * report").
 */
class RuntimeReportWriterImplTest {

    @TempDir
    Path tempDir;

    private static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private RuntimeReportWriterImpl writer() {
        LocalStorageConfig config = new LocalStorageConfig("unused", "unused", "unused", tempDir.toString());
        return new RuntimeReportWriterImpl(objectMapper(), config);
    }

    private static RuntimeReport sampleReport(String runtimeId) {
        Instant now = Instant.now();
        RuntimeReport.RuntimeConfigSnapshot configSnapshot = new RuntimeReport.RuntimeConfigSnapshot(1, 1, 1,
                "manual", 0.7, true, false, false, false, "flash", "pro", "flash", "pro");
        return new RuntimeReport(runtimeId, now, now, Duration.ofSeconds(5), 1, 1, 0, List.of(), "1 READY", null,
                List.of(), List.of(), List.of(), configSnapshot);
    }

    @Test
    void writesTheReportAsJsonUnderARuntimeSubdirectory() throws IOException {
        RuntimeReportWriterImpl writer = writer();
        RuntimeReport report = sampleReport("run-1");

        String path = writer.write(report);

        Path file = Path.of(path);
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(file.getParent().getFileName().toString()).isEqualTo("runtime");
        assertThat(file.getFileName().toString()).isEqualTo("run-1.json");
        assertThat(Files.readString(file)).contains("run-1").contains("manual");
    }

    @Test
    void theWrittenReportRoundTripsBackToAnEquivalentObject() throws IOException {
        RuntimeReportWriterImpl writer = writer();
        RuntimeReport report = sampleReport("run-2");

        String path = writer.write(report);
        RuntimeReport reloaded = objectMapper().readValue(Path.of(path).toFile(), RuntimeReport.class);

        assertThat(reloaded.runtimeId()).isEqualTo("run-2");
        assertThat(reloaded.reelsCompleted()).isEqualTo(1);
        assertThat(reloaded.configSnapshot().runtimeMode()).isEqualTo("manual");
    }
}
