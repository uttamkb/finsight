package com.finsight.backend.controller;

import com.finsight.backend.dto.ReconciliationResultDto;
import com.finsight.backend.dto.ReconciliationStatsDto;
import com.finsight.backend.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.finsight.backend.entity.ReconciliationRun;
import com.finsight.backend.repository.ReconciliationRunRepository;

@RestController
@RequestMapping("/api/v1/reconciliation")
@CrossOrigin(origins = "*")
@Tag(name = "Reconciliation", description = "Endpoints for bank-to-receipt matching and audit trail management")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationRunRepository reconciliationRunRepository;

    public ReconciliationController(ReconciliationService reconciliationService,
                                     ReconciliationRunRepository reconciliationRunRepository) {
        this.reconciliationService = reconciliationService;
        this.reconciliationRunRepository = reconciliationRunRepository;
    }

    @GetMapping("/runs")
    @Operation(summary = "Get Reconciliation Runs", description = "Retrieves a history of reconciliation engine executions.")
    public ResponseEntity<List<ReconciliationRun>> getReconciliationRuns(
            @RequestParam(defaultValue = "local_tenant") String tenantId) {
        return ResponseEntity.ok(reconciliationRunRepository.findByTenantIdOrderByStartedAtDesc(tenantId));
    }

    @PostMapping("/run")
    @Operation(summary = "Run Reconciliation", description = "Triggers the auto-matching engine for the specified account type.")
    public ResponseEntity<ReconciliationResultDto> runReconciliation(
            @RequestParam(defaultValue = "local_tenant") String tenantId,
            @RequestParam String accountType) {
        return ResponseEntity.ok(reconciliationService.runReconciliation(tenantId, accountType));
    }

    @GetMapping("/audit-trail")
    @Operation(summary = "Get Audit Trail", description = "Retrieves a list of reconciliation anomalies and logs.")
    public ResponseEntity<List<com.finsight.backend.entity.AuditTrail>> getAuditTrail(
            @RequestParam(required = false) String issueType) {
        return ResponseEntity.ok(reconciliationService.getAuditTrail("local_tenant", issueType));
    }

    @GetMapping("/audit-trail/statistics")
    @Operation(summary = "Get Audit Statistics", description = "Retrieves counts of unresolved anomalies and matched items.")
    public ResponseEntity<ReconciliationStatsDto> getAuditStatistics() {
        return ResponseEntity.ok(reconciliationService.getAuditStatistics("local_tenant"));
    }

    @PostMapping("/audit-trail/{id}/resolve")
    @Operation(summary = "Resolve Anomaly", description = "Marks an audit trail entry as resolved.")
    public ResponseEntity<Void> resolveAnomaly(@PathVariable Long id) {
        reconciliationService.resolveAnomaly(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/audit-trail/{id}/ignore")
    @Operation(summary = "Ignore Anomaly", description = "Marks an audit trail entry as resolved (ignored) without action.")
    public ResponseEntity<Void> ignoreAnomaly(@PathVariable Long id) {
        reconciliationService.resolveAnomaly(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-trail/export")
    @Operation(summary = "Export Audit Trail", description = "Generates a downloadable report of the audit trail.")
    public ResponseEntity<byte[]> exportAuditTrail() {
        // Placeholder for export logic
        return ResponseEntity.ok(new byte[0]);
    }

    @PostMapping("/manual-match")
    @Operation(summary = "Manual Match", description = "Manually links a bank transaction with a specific receipt.")
    public ResponseEntity<Void> manualMatch(
            @RequestParam Long bankTransactionId,
            @RequestParam Long receiptId) {
        reconciliationService.manuallyLink(bankTransactionId, receiptId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/no-receipt")
    @Operation(summary = "Mark as No Receipt", description = "Flags a transaction as not requiring a matching receipt.")
    public ResponseEntity<Void> markAsNoReceiptNeeded(
            @RequestParam Long bankTransactionId) {
        reconciliationService.markAsNoReceiptNeeded(bankTransactionId);
        return ResponseEntity.ok().build();
    }
}
