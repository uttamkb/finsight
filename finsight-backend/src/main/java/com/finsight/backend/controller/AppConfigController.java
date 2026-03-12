package com.finsight.backend.controller;

import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.service.AppConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class AppConfigController {

    private final AppConfigService appConfigService;

    public AppConfigController(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @GetMapping
    public ResponseEntity<AppConfig> getConfig() {
        return ResponseEntity.ok(appConfigService.getConfig());
    }

    @PutMapping
    public ResponseEntity<AppConfig> saveConfig(@Valid @RequestBody AppConfig config) {
        return ResponseEntity.ok(appConfigService.saveConfig(config));
    }
}
