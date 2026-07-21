package com.forgebrain.backend.gcp;

import java.time.Instant;

/**
 * The outcome of one {@link CloudConnectivityChecker} call — never thrown, always returned, so a
 * caller (a test, a guarded {@code CommandLineRunner}) never needs a try/catch to find out
 * whether live cloud connectivity actually works.
 */
public record CloudConnectivityResult(String target, Status status, String message, Instant checkedAt) {

    public enum Status {
        /** The check ran and the live call/write succeeded. */
        SUCCESS,
        /** The check ran but the live call/write failed. */
        FAILURE,
        /** The check was never attempted because that target isn't enabled. */
        SKIPPED
    }

    public static CloudConnectivityResult success(String target, String message) {
        return new CloudConnectivityResult(target, Status.SUCCESS, message, Instant.now());
    }

    public static CloudConnectivityResult failure(String target, String message) {
        return new CloudConnectivityResult(target, Status.FAILURE, message, Instant.now());
    }

    public static CloudConnectivityResult skipped(String target, String message) {
        return new CloudConnectivityResult(target, Status.SKIPPED, message, Instant.now());
    }
}
