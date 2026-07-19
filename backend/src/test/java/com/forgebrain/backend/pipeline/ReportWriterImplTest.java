package com.forgebrain.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgebrain.backend.config.JacksonConfig;
import com.forgebrain.backend.config.LocalStorageConfig;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterImplTest {

    @TempDir
    Path tempDir;

    @Test
    void writesExecutionReportAsJsonWithTimestampedFilename() throws Exception {
        File reportsDir = new File(tempDir.toFile(), "reports");
        ReportWriterImpl writer = new ReportWriterImpl(
                new JacksonConfig().objectMapper(),
                new LocalStorageConfig("unused", "unused", "unused", reportsDir.getAbsolutePath()));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T00:00:03Z");
        PipelineExecutionReport report = new PipelineExecutionReport(
                "pipeline-1",
                start,
                end,
                Duration.between(start, end),
                "java-what-is-java",
                List.of(new StageExecutionSummary(
                        "RESEARCH",
                        Duration.ofSeconds(1),
                        true,
                        "topic_id=java-what-is-java",
                        "research_version=1.0.0-heuristic",
                        true,
                        "MEDIUM",
                        null)),
                "SUCCESS",
                List.of("RESEARCH used deterministic fallback path."),
                List.of());

        writer.write(report);

        assertThat(reportsDir).isDirectory();
        File[] files = reportsDir.listFiles();
        assertThat(files).hasSize(1);
        assertThat(files[0].getName()).startsWith("pipeline-report-").endsWith(".json");

        PipelineExecutionReport reloaded = new JacksonConfig().objectMapper()
                .readValue(files[0], PipelineExecutionReport.class);
        assertThat(reloaded.pipelineId()).isEqualTo("pipeline-1");
        assertThat(reloaded.selectedTopic()).isEqualTo("java-what-is-java");
        assertThat(reloaded.stageResults()).hasSize(1);
    }
}
