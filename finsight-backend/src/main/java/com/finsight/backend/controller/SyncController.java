package com.finsight.backend.controller;

import com.finsight.backend.dto.SyncStatus;
import com.finsight.backend.service.DriveSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name = "Drive Sync", description = "Endpoints for manually triggering Google Drive receipt ingestion and checking sync status")
public class SyncController {

    private static final String DEFAULT_TENANT = "local_tenant";

    private final DriveSyncService driveSyncService;

    public SyncController(DriveSyncService driveSyncService) {
        this.driveSyncService = driveSyncService;
    }

    /**
     * POST /api/sync/google-drive
     * Triggers an asynchronous, recursive, idempotent sync of Google Drive receipts.
     */
    @PostMapping("/google-drive")
    @Operation(summary = "Trigger Drive Sync", description = "Triggers an asynchronous, recursive, idempotent sync of Google Drive receipts.")
    public ResponseEntity<Void> syncGoogleDrive(
            @Parameter(description = "Tenant ID") @RequestHeader(value = "X-Tenant-Id", defaultValue = DEFAULT_TENANT) String tenantId) {
        driveSyncService.sync(tenantId);
        return ResponseEntity.accepted().build();
    }

    /**
     * GET /api/sync/google-drive/status
     * Returns the real-time status of the background sync process.
     */
    @GetMapping("/google-drive/status")
    @Operation(summary = "Check Sync Status", description = "Returns the real-time status of the background sync process.")
    public ResponseEntity<SyncStatus> getSyncStatus(
            @Parameter(description = "Tenant ID") @RequestHeader(value = "X-Tenant-Id", defaultValue = DEFAULT_TENANT) String tenantId) {
        return ResponseEntity.ok(driveSyncService.getStatus(tenantId));
    }

    /**
     * GET /api/sync/history
     * Returns the timestamp of the last successful synchronization.
     */
    @GetMapping("/history")
    @Operation(summary = "Get Sync History", description = "Returns the timestamp of the last successful synchronization.")
    public ResponseEntity<java.util.Map<String, Object>> getSyncHistory(
            @Parameter(description = "Tenant ID") @RequestHeader(value = "X-Tenant-Id", defaultValue = DEFAULT_TENANT) String tenantId) {
        SyncStatus status = driveSyncService.getStatus(tenantId);
        java.util.Map<String, Object> history = new java.util.HashMap<>();
        history.put("lastSync", status.getLastSyncAt());
        return ResponseEntity.ok(history);
    }
}
