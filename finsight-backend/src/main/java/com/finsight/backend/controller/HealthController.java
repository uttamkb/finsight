package com.finsight.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "System Health", description = "Endpoints for monitoring API health status")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Get Health Status", description = "Public endpoint to check if the backend service is running and accessible.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "FinSight Backend",
            "timestamp", Instant.now().toString()
        ));
    }
}
