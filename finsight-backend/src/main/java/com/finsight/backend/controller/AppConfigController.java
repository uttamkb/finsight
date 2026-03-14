package com.finsight.backend.controller;

import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.service.AppConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Application Settings", description = "Endpoints for managing application configuration")
public class AppConfigController {

    private final AppConfigService appConfigService;

    public AppConfigController(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @GetMapping
    @Operation(summary = "Get Application Configuration", description = "Retrieves the current application configuration settings.")
    public ResponseEntity<AppConfig> getConfig() {
        return ResponseEntity.ok(appConfigService.getConfig());
    }

    @PutMapping
    @Operation(summary = "Update Application Configuration", description = "Updates and saves the application configuration settings.")
    public ResponseEntity<AppConfig> saveConfig(@Valid @RequestBody AppConfig config) {
        return ResponseEntity.ok(appConfigService.saveConfig(config));
    }
}
