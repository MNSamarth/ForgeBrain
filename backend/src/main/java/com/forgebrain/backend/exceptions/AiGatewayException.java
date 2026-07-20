package com.forgebrain.backend.exceptions;

/**
 * Thrown by {@link com.forgebrain.backend.ai.AiGateway} when a prompt could not be executed —
 * either because Vertex AI is not usable at all ({@link Reason#CONFIGURATION}, never retried) or
 * because every retry attempt failed ({@link Reason#EXECUTION_FAILED} — a call failure, a
 * timeout, malformed JSON, or a failed response validation). Every caller is expected to catch
 * this and fall back to a deterministic path, exactly as callers previously caught {@link
 * ConfigurationException} directly from {@code VertexAiClient}.
 */
public class AiGatewayException extends RuntimeException {

    public enum Reason {
        /** Vertex AI is not configured/usable at all (blank project id or model id) — never retried. */
        CONFIGURATION,
        /** Every retry attempt failed (call failure, timeout, malformed JSON, or validation failure). */
        EXECUTION_FAILED
    }

    private final Reason reason;
    private final String promptName;

    public AiGatewayException(Reason reason, String promptName, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.promptName = promptName;
    }

    public Reason reason() {
        return reason;
    }

    public String promptName() {
        return promptName;
    }
}
