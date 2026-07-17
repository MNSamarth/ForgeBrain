package com.forgebrain.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the ForgeBrain backend.
 *
 * Phase 1 scope: this application currently wires no pipeline business logic, no Google Cloud
 * clients, and no authentication. It exists to establish a real, buildable Spring Boot project
 * shape — packages, interfaces, DTOs, and configuration placeholders — for the content pipeline
 * described in docs/PIPELINE.md. See TODO.md for what remains before any stage is functional.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
