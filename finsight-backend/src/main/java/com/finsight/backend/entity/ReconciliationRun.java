package com.finsight.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_runs", indexes = {
    @Index(name = "idx_recon_run_tenant", columnList = "tenant_id")
})
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "matched_count")
    private Integer matchedCount = 0;

    @Column(name = "manual_review_count")
    private Integer manualReviewCount = 0;

    @Column(name = "status")
    private String status; // RUNNING, COMPLETED, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }
    public Integer getManualReviewCount() { return manualReviewCount; }
    public void setManualReviewCount(Integer manualReviewCount) { this.manualReviewCount = manualReviewCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
