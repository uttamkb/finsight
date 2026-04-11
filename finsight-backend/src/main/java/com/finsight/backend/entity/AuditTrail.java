package com.finsight.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_trail", indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_resolved", columnList = "resolved"),
        @Index(name = "idx_audit_txn_id", columnList = "transaction_id"),
        @Index(name = "idx_audit_receipt_id", columnList = "receipt_id")
})
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "local_tenant";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "receipt", "category", "tenantId", "createdAt",
            "reconciled", "referenceNumber" })
    private BankTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "tenantId", "driveFileId", "ocrConfidence",
            "ocrModeUsed", "createdAt", "category" })
    private Receipt receipt;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type")
    private IssueType issueType;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "match_type")
    private String matchType; // EXACT, FUZZY, NONE

    @Column(name = "amount_score")
    private Double amountScore;

    @Column(name = "date_score")
    private Double dateScore;

    @Column(name = "vendor_score")
    private Double vendorScore;

    @Column(name = "amount_reasoning", columnDefinition = "TEXT")
    private String amountReasoning;

    @Column(name = "date_reasoning", columnDefinition = "TEXT")
    private String dateReasoning;

    @Column(name = "vendor_reasoning", columnDefinition = "TEXT")
    private String vendorReasoning;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum IssueType {
        BANK_NO_RECEIPT, // Bank transaction has no matching receipt
        RECEIPT_NO_BANK, // Receipt has no matching bank transaction
        AMOUNT_MISMATCH, // Amount doesn't match between bank and receipt
        DATE_MISMATCH, // Date is outside acceptable range
        SUGGESTED_MATCH, // Recommended match waiting for user approval
        UNMATCHED // No match found
    }

    // Getters and setters
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

    public BankTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(BankTransaction transaction) {
        this.transaction = transaction;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public void setReceipt(Receipt receipt) {
        this.receipt = receipt;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }

    public IssueType getIssueType() {
        return issueType;
    }

    public void setIssueType(IssueType issueType) {
        this.issueType = issueType;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public Double getAmountScore() {
        return amountScore;
    }

    public void setAmountScore(Double amountScore) {
        this.amountScore = amountScore;
    }

    public Double getDateScore() {
        return dateScore;
    }

    public void setDateScore(Double dateScore) {
        this.dateScore = dateScore;
    }

    public Double getVendorScore() {
        return vendorScore;
    }

    public void setVendorScore(Double vendorScore) {
        this.vendorScore = vendorScore;
    }

    public String getAmountReasoning() {
        return amountReasoning;
    }

    public void setAmountReasoning(String amountReasoning) {
        this.amountReasoning = amountReasoning;
    }

    public String getDateReasoning() {
        return dateReasoning;
    }

    public void setDateReasoning(String dateReasoning) {
        this.dateReasoning = dateReasoning;
    }

    public String getVendorReasoning() {
        return vendorReasoning;
    }

    public void setVendorReasoning(String vendorReasoning) {
        this.vendorReasoning = vendorReasoning;
    }
}
