package com.finsight.backend.controller;

import com.finsight.backend.dto.BackupData;
import com.finsight.backend.service.DataManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/backup")
@CrossOrigin(origins = "*")
public class BackupController {

    private final DataManagementService dataManagementService;

    public BackupController(DataManagementService dataManagementService) {
        this.dataManagementService = dataManagementService;
    }

    @GetMapping("/export")
    public ResponseEntity<BackupData> exportBackup() {
        BackupData backup = dataManagementService.exportData();
        String filename = "finsight_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(backup);
    }

    @PostMapping("/import")
    public ResponseEntity<String> importBackup(@RequestBody BackupData data) {
        try {
            dataManagementService.importData(data);
            return ResponseEntity.ok("Backup restored successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to restore backup: " + e.getMessage());
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetDatabase() {
        try {
            dataManagementService.resetDatabase();
            return ResponseEntity.ok("Database reset successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to reset database: " + e.getMessage());
        }
    }
}
