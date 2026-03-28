package com.finsight.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transactions", indexes = {
    @Index(name = "idx_bank_tx_tenant", columnList = "tenant_id"),
    @Index(name = "idx_bank_tx_date", columnList = "tx_date")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "local_tenant";

    @Column(name = "tx_date", nullable = false)
    private LocalDate txDate;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "vendor")
    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Category category;

    @Deprecated
    @Column(name = "reconciled", nullable = false)
    private Boolean reconciled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Receipt receipt;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "vendor_normalized")
    private String vendorNormalized;

    @Column(name = "vendor_type")
    private String vendorType;

    @Column(name = "block")
    private String block;

    @Column(name = "floor")
    private String floor;

    @Column(name = "flat_number")
    private String flatNumber;

    @Column(name = "sub_category")
    private String subCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType = AccountType.MAINTENANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Column(name = "match_score")
    private Double matchScore = 0.0;

    @Column(name = "match_type")
    private String matchType; // EXACT, FUZZY, etc.

    @Column(name = "is_manual_override")
    private Boolean isManualOverride = false;

    @Column(name = "matched_receipt_ids")
    private String matchedReceiptIds; // JSON list of IDs

    @Column(name = "audit_log", columnDefinition = "TEXT")
    private String auditLog;

    @Column(name = "status")
    private String status; // AUTO_VALIDATED | LOW_CONFIDENCE | NEEDS_REVIEW | USER_VERIFIED

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(name = "original_snippet", columnDefinition = "TEXT")
    private String originalSnippet;

    @Column(name = "is_duplicate")
    private Boolean isDuplicate = false;

    public enum TransactionType { CREDIT, DEBIT }
    public enum AccountType { MAINTENANCE, CORPUS, SINKING_FUND }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    @Deprecated
    public Boolean getReconciled() { return reconciled; }
    @Deprecated
    public void setReconciled(Boolean reconciled) { this.reconciled = reconciled; }
    public Receipt getReceipt() { return receipt; }
    public void setReceipt(Receipt receipt) { this.receipt = receipt; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getVendorNormalized() { return vendorNormalized; }
    public void setVendorNormalized(String vendorNormalized) { this.vendorNormalized = vendorNormalized; }

    public String getVendorType() { return vendorType; }
    public void setVendorType(String vendorType) { this.vendorType = vendorType; }

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getFlatNumber() { return flatNumber; }
    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public Boolean getIsManualOverride() { return isManualOverride; }
    public void setIsManualOverride(Boolean isManualOverride) { this.isManualOverride = isManualOverride; }

    public String getMatchedReceiptIds() { return matchedReceiptIds; }
    public void setMatchedReceiptIds(String matchedReceiptIds) { this.matchedReceiptIds = matchedReceiptIds; }

    public String getAuditLog() { return auditLog; }
    public void setAuditLog(String auditLog) { this.auditLog = auditLog; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getAiReasoning() { return aiReasoning; }
    public void setAiReasoning(String aiReasoning) { this.aiReasoning = aiReasoning; }

    public String getOriginalSnippet() { return originalSnippet; }
    public void setOriginalSnippet(String originalSnippet) { this.originalSnippet = originalSnippet; }

    public Boolean getIsDuplicate() { return isDuplicate; }
    public void setIsDuplicate(Boolean isDuplicate) { this.isDuplicate = isDuplicate; }
}
