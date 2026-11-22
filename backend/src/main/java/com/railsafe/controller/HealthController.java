package com.railsafe.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, dependency-free liveness probe for platform health checks (Render,
 * Kubernetes, reverse proxies). It deliberately does not touch the database so
 * a transient database problem does not make the process look dead.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "railsafe");
    }
}
