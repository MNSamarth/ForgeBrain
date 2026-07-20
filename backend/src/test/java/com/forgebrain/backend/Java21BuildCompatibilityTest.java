package com.forgebrain.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms this build actually runs on Java 21 LTS — the project standard set in {@code
 * backend/pom.xml} ({@code java.version}/{@code maven.compiler.source}/{@code
 * maven.compiler.target}) and documented in backend/README.md. Also exercises a couple of
 * language features already used throughout this codebase (records, pattern matching in
 * {@code switch}) so a future accidental downgrade of the compiler target would fail here
 * first, with a clear message, rather than as a confusing compile error somewhere else.
 */
class Java21BuildCompatibilityTest {

    @Test
    void runsOnJava21OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }

    @Test
    void supportsRecordPatternMatchingInSwitch() {
        record Point(int x, int y) {
        }

        Object value = new Point(3, 4);
        String description = switch (value) {
            case Point(int x, int y) when x == y -> "diagonal";
            case Point p -> "point(" + p.x() + "," + p.y() + ")";
            default -> "unknown";
        };

        assertThat(description).isEqualTo("point(3,4)");
    }
}
