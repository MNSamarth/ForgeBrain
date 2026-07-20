package com.forgebrain.backend.ai;

import java.util.Map;
import java.util.function.Consumer;

/**
 * One call a service asks {@link AiGateway} to make: which registered prompt to use, the fully
 * built prompt text (assembled by the calling stage's own {@code *PromptBuilder}, exactly as
 * before), the shape to deserialize the response into, and — optionally — the same validation
 * logic that stage already had, reused as-is via a method reference rather than duplicated.
 *
 * @param promptName  a name registered in {@link PromptRegistry}, e.g. {@code "research"}
 * @param promptText  the complete prompt text to send
 * @param variables   stage-specific values echoed back for logging/debugging, exactly like {@code
 *                    VertexAiPromptRequest.variables()}
 * @param responseType the type Jackson deserializes the raw JSON response into — this is this
 *                     gateway's schema/shape validation: a response that doesn't fit this shape
 *                     fails to parse and is treated as a retryable failure
 * @param validator   optional domain-specific validation beyond "it parsed" (e.g. "no field was
 *                     left blank") — called after a successful parse; throwing from it is treated
 *                     exactly like a parse failure (retried, then falls through to {@link
 *                     com.forgebrain.backend.exceptions.AiGatewayException})
 */
public record AiPromptExecution<T>(
        String promptName,
        String promptText,
        Map<String, Object> variables,
        Class<T> responseType,
        Consumer<T> validator
) {

    /** Convenience constructor for the common case: no extra validation beyond parsing. */
    public AiPromptExecution(String promptName, String promptText, Map<String, Object> variables,
            Class<T> responseType) {
        this(promptName, promptText, variables, responseType, null);
    }
}
