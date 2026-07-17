package com.forgebrain.backend.controllers;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal, real health-check endpoint — standard Spring Boot convention, not pipeline
 * business logic, so it's exempt from this phase's "no fake implementations" boundary. Useful
 * to confirm the application actually starts and serves traffic once deployed to Cloud Run.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
