package com.forgebrain.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.forgebrain.backend.config.LocalStorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ProductionReadinessReportWriterImpl} — per this mission's Goal 3
 * ("produce a production-readiness report").
 */
class ProductionReadinessReportWriterImplTest {

    @TempDir
    Path tempDir;

    private static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private ProductionReadinessReportWriterImpl writer() {
        LocalStorageConfig config = new LocalStorageConfig("unused", "unused", "unused", tempDir.toString());
        return new ProductionReadinessReportWriterImpl(objectMapper(), config);
    }

    @Test
    void writesAPassingReportUnderAValidationSubdirectory() throws IOException {
        ProductionReadinessReport report = ProductionReadinessReport.of(Map.of("checkA", List.of()));

        String path = writer().write(report);

        Path file = Path.of(path);
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(file.getParent().getFileName().toString()).isEqualTo("validation");
        assertThat(Files.readString(file)).contains("\"passed\" : true");
    }

    @Test
    void writesAFailingReportWithItsViolations() throws IOException {
        ProductionReadinessReport report = ProductionReadinessReport.of(
                Map.of("checkA", List.of("something is wrong")));

        String path = writer().write(report);

        assertThat(Files.readString(Path.of(path))).contains("something is wrong").contains("\"passed\" : false");
    }

    @Test
    void theWrittenReportRoundTripsBackToAnEquivalentObject() throws IOException {
        ProductionReadinessReport report = ProductionReadinessReport.of(Map.of("checkA", List.of()));

        String path = writer().write(report);
        ProductionReadinessReport reloaded = objectMapper().readValue(Path.of(path).toFile(),
                ProductionReadinessReport.class);

        assertThat(reloaded.validationId()).isEqualTo(report.validationId());
        assertThat(reloaded.passed()).isTrue();
    }
}
