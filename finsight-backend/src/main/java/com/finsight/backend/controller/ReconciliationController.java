package com.finsight.backend.controller;

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

    public ReconciliationController(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    /**
     * Returns the full audit trail with optional filtering by issue type.
     */
    @GetMapping("/audit-trail")
    public ResponseEntity<List<AuditTrail>> getAuditTrail(
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
        return ResponseEntity.ok(results);
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
