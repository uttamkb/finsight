package com.finsight.backend.controller;

import com.finsight.backend.dto.BackupData;
import com.finsight.backend.service.DataManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/backup")
@CrossOrigin(origins = "*")
@Tag(name = "Data Management & Backups", description = "Endpoints for exporting, importing, and resetting database records")
public class BackupController {

    private final DataManagementService dataManagementService;

    public BackupController(DataManagementService dataManagementService) {
        this.dataManagementService = dataManagementService;
    }

    @GetMapping("/export")
    @Operation(summary = "Export Database Backup", description = "Exports all relevant database records into a single JSON structured file.")
    public ResponseEntity<BackupData> exportBackup() {
        BackupData backup = dataManagementService.exportData();
        String filename = "finsight_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(backup);
    }

    @PostMapping("/import")
    @Operation(summary = "Import Database Backup", description = "Restores database records from a previously exported JSON backup file.")
    public ResponseEntity<String> importBackup(@RequestBody BackupData data) {
        try {
            dataManagementService.importData(data);
            return ResponseEntity.ok("Backup restored successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to restore backup: " + e.getMessage());
        }
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset Database", description = "Truncates and wipes all user transaction data securely.")
    public ResponseEntity<String> resetDatabase() {
        try {
            dataManagementService.resetDatabase();
            return ResponseEntity.ok("Database reset successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to reset database: " + e.getMessage());
        }
    }
}
