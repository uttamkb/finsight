package com.finsight.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "statement_uploads", indexes = {
    @Index(name = "idx_upload_tenant", columnList = "tenant_id"),
    @Index(name = "idx_upload_hash", columnList = "file_hash"),
    @Index(name = "idx_upload_file_id", columnList = "file_id")
})
public class StatementUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, unique = true)
    private String fileId; // UUID

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "status")
    private String status; // UPLOADED, PROCESSING, COMPLETED, FAILED

    @Column(name = "account_type")
    private String accountType;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "gemini_calls_count")
    private Integer geminiCallsCount;

    @Column(name = "avg_confidence_score")
    private Double avgConfidenceScore;

    @Column(name = "source")
    private String source; // AI, USER, REPROCESSED

    @Column(name = "last_processed_at")
    private LocalDateTime lastProcessedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public Integer getGeminiCallsCount() { return geminiCallsCount; }
    public void setGeminiCallsCount(Integer geminiCallsCount) { this.geminiCallsCount = geminiCallsCount; }

    public Double getAvgConfidenceScore() { return avgConfidenceScore; }
    public void setAvgConfidenceScore(Double avgConfidenceScore) { this.avgConfidenceScore = avgConfidenceScore; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getLastProcessedAt() { return lastProcessedAt; }
    public void setLastProcessedAt(LocalDateTime lastProcessedAt) { this.lastProcessedAt = lastProcessedAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
