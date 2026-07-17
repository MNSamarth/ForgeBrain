package com.forgebrain.backend.exceptions;

/**
 * Thrown when required configuration (see docs/CONFIGURATION.md) is missing or invalid at
 * startup or at first use. Configuration failures are intended to surface immediately and
 * loudly, never as a silent {@code null} deep inside a pipeline stage — see
 * docs/CONFIGURATION.md Section 4.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
