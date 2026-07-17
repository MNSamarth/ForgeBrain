package com.forgebrain.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single {@link ObjectMapper} used across the application to read and write every
 * pipeline artifact (curriculum, memory state, pipeline results). Configured once, here,
 * rather than each caller building its own mapper, so every JSON file this project reads or
 * writes uses the same field-naming convention as the JSON Schemas in {@code brain/},
 * {@code memory/}, and {@code renderer/} — snake_case field names, matching every
 * {@code *-schema.json} in this repository exactly.
 *
 * <p>Enum values are matched case-insensitively on read, which is sufficient for every enum
 * this application currently deserializes (curriculum {@code status}/{@code difficulty} and
 * memory {@code priority}/{@code status} all use underscore-separated JSON values that already
 * align with Java's {@code UPPER_SNAKE_CASE} enum constant names). Enums whose JSON schema uses
 * hyphens (e.g. {@code ContentStrategy.HookType}'s {@code "before-vs-after"}) are write-only in
 * this phase — see {@code NEXT_EXECUTION.md} for that known follow-up.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
    }
}
