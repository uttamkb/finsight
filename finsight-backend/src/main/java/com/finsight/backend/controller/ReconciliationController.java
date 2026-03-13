package com.finsight.backend.controller;

import com.finsight.backend.dto.AuditTrailDto;
import com.finsight.backend.entity.AuditTrail;
import com.finsight.backend.repository.AuditTrailRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final AuditTrailRepository auditTrailRepository;
    private final com.finsight.backend.service.ReconciliationService reconciliationService;

    public ReconciliationController(AuditTrailRepository auditTrailRepository,
                                    com.finsight.backend.service.ReconciliationService reconciliationService) {
        this.auditTrailRepository = auditTrailRepository;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Returns the full audit trail with optional filtering by issue type.
     */
    @GetMapping("/audit-trail")
    public ResponseEntity<List<AuditTrailDto>> getAuditTrail(
            @RequestParam(value = "issueType", required = false) String issueType) {
        List<AuditTrail> results;
        if (issueType != null && !issueType.isBlank()) {
            try {
                AuditTrail.IssueType type = AuditTrail.IssueType.valueOf(issueType.toUpperCase());
                results = auditTrailRepository.findByTenantIdAndIssueType("local_tenant", type);
            } catch (IllegalArgumentException e) {
                results = auditTrailRepository.findByTenantId("local_tenant");
            }
        } else {
            results = auditTrailRepository.findByTenantId("local_tenant");
        }
        
        List<AuditTrailDto> dtos = results.stream()
                .map(AuditTrailDto::from)
                .toList();
                
        return ResponseEntity.ok(dtos);
    }

    /**
     * Returns summary statistics for the audit trail.
     */
    @GetMapping({"/audit-trail/statistics", "/audit-trail/stats"})
    public ResponseEntity<Map<String, Object>> getAuditStats() {
        List<AuditTrail> all = auditTrailRepository.findByTenantId("local_tenant");
        long total = all.size();
        long unresolvedCount = all.stream().filter(a -> !Boolean.TRUE.equals(a.getResolved())).count();
        long resolved = total - unresolvedCount;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("unresolvedCount", unresolvedCount);
        stats.put("unresolved", unresolvedCount); // Backward compatibility
        stats.put("resolved", resolved);
        return ResponseEntity.ok(stats);
    }

    /**
     * Marks an audit trail item as resolved.
     */
    @PostMapping("/audit-trail/{id}/resolve")
    public ResponseEntity<?> resolveAuditItem(@PathVariable Long id) {
        return auditTrailRepository.findById(id).map(entry -> {
            entry.setResolved(true);
            entry.setResolvedAt(LocalDateTime.now());
            entry.setResolvedBy("user"); // Could be extended with real auth later
            auditTrailRepository.save(entry);
            log.info("Audit trail entry {} marked as resolved.", id);
            Map<String, Object> res = new HashMap<>();
            res.put("message", "Audit trail item resolved.");
            res.put("id", id);
            return ResponseEntity.ok(res);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Manually links a bank transaction and a receipt.
     */
    @PostMapping("/link")
    public ResponseEntity<?> manuallyLink(@RequestBody Map<String, Long> payload) {
        Long txnId = payload.get("transactionId");
        Long receiptId = payload.get("receiptId");
        
        if (txnId == null || receiptId == null) {
            return ResponseEntity.badRequest().body("Both transactionId and receiptId must be provided.");
        }
        
        try {
            // Need reference to service to perform actual linking
            // Requires dependency injection update in this controller
            // Let's assume we have it or will add it via an explicit field
            // Note: I will update the constructor of ReconciliationController next to inject ReconciliationService
            reconciliationService.manuallyLink(txnId, receiptId);
            return ResponseEntity.ok(Map.of("message", "Manually linked successfully."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to manually link", e);
            return ResponseEntity.internalServerError().body("An error occurred during manual linking.");
        }
    }

    /**
     * Marks an audit trail item as ignored/done without linking.
     */
    @PostMapping("/audit-trail/{id}/ignore")
    public ResponseEntity<?> ignoreAuditItem(@PathVariable Long id) {
        return auditTrailRepository.findById(id).map(entry -> {
            entry.setResolved(true);
            entry.setResolvedAt(LocalDateTime.now());
            entry.setResolvedBy("user_ignored");
            auditTrailRepository.save(entry);
            log.info("Audit trail entry {} marked as ignored.", id);
            return ResponseEntity.ok(Map.of("message", "Issue marked as done/ignored.", "id", id));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Exports the audit trail as a CSV file download.
     */
    @GetMapping("/audit-trail/export")
    public void exportAuditTrail(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=audit_trail.csv");

        List<AuditTrail> all = auditTrailRepository.findByTenantId("local_tenant");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("ID,Issue Type,Description,Match Type,Similarity Score,Resolved,Resolved At,Created At");
            for (AuditTrail a : all) {
                writer.printf("%d,%s,\"%s\",%s,%.1f,%s,%s,%s%n",
                        a.getId(),
                        a.getIssueType(),
                        escape(a.getIssueDescription()),
                        a.getMatchType() != null ? a.getMatchType() : "N/A",
                        a.getSimilarityScore() != null ? a.getSimilarityScore() : 0.0,
                        Boolean.TRUE.equals(a.getResolved()) ? "Yes" : "No",
                        a.getResolvedAt() != null ? a.getResolvedAt().toString() : "",
                        a.getCreatedAt() != null ? a.getCreatedAt().toString() : ""
                );
            }
        }
    }

    private String escape(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
