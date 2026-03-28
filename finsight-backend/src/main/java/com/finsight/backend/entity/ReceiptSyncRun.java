package com.finsight.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipt_sync_runs", indexes = {
    @Index(name = "idx_sync_run_tenant", columnList = "tenant_id")
})
public class ReceiptSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "status")
    private String status; // RUNNING, SUCCESS, ERROR

    @Column(name = "stage")
    private String stage; // SCANNING, OCR, COMPLETED

    @Column(name = "total_files")
    private Integer totalFiles = 0;

    @Column(name = "processed_files")
    private Integer processedFiles = 0;

    @Column(name = "failed_files")
    private Integer failedFiles = 0;

    @Column(name = "skipped_files")
    private Integer skippedFiles = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Integer getTotalFiles() { return totalFiles; }
    public void setTotalFiles(Integer totalFiles) { this.totalFiles = totalFiles; }
    public Integer getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(Integer processedFiles) { this.processedFiles = processedFiles; }
    public Integer getFailedFiles() { return failedFiles; }
    public void setFailedFiles(Integer failedFiles) { this.failedFiles = failedFiles; }
    public Integer getSkippedFiles() { return skippedFiles; }
    public void setSkippedFiles(Integer skippedFiles) { this.skippedFiles = skippedFiles; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
