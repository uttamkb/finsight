package com.finsight.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "drive_file_id", unique = true, nullable = false)
    private String driveFileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "vendor")
    private String vendor;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "ocr_mode_used")
    private String ocrModeUsed;

    @Column(name = "status")
    private String status; // PROCESSED, FAILED, PENDING

    @Column(name = "category")
    private String category;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "google_drive_link")
    private String googleDriveLink;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.MANUAL_REVIEW;

    @Column(name = "matched_bank_transaction_id")
    private Long matchedBankTransactionId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDriveFileId() {
        return driveFileId;
    }

    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public void setOcrConfidence(Double ocrConfidence) {
        this.ocrConfidence = ocrConfidence;
    }

    public String getOcrModeUsed() {
        return ocrModeUsed;
    }

    public void setOcrModeUsed(String ocrModeUsed) {
        this.ocrModeUsed = ocrModeUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getGoogleDriveLink() {
        return googleDriveLink;
    }

    public void setGoogleDriveLink(String googleDriveLink) {
        this.googleDriveLink = googleDriveLink;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }

    public Long getMatchedBankTransactionId() { return matchedBankTransactionId; }
    public void setMatchedBankTransactionId(Long matchedBankTransactionId) { this.matchedBankTransactionId = matchedBankTransactionId; }
}
