package com.finsight.backend.dto;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.entity.Receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing a BankTransaction for API responses.
 */
public class BankTransactionDto {

    private Long id;
    private String tenantId;
    private LocalDate txDate;
    private String description;
    private String vendor;
    private String type;
    private BigDecimal amount;
    @Deprecated
    private Boolean reconciled;
    private String reconciliationStatus;
    private String referenceNumber;
    private LocalDateTime createdAt;
    private String status;
    private Double confidenceScore;
    private String aiReasoning;
    private String originalSnippet;
    private Boolean isDuplicate;

    // Nested DTOs for UI compatibility
    private CategoryDto category;
    private ReceiptDto receipt;

    public static class CategoryDto {
        public Long id;
        public String name;
        public String type;
        public String parentName;

        public static CategoryDto from(Category c) {
            if (c == null) return null;
            CategoryDto dto = new CategoryDto();
            dto.id = c.getId();
            dto.name = c.getName();
            dto.type = c.getType() != null ? c.getType().name() : null;
            if (c.getParentCategory() != null) {
                dto.parentName = c.getParentCategory().getName();
            }
            return dto;
        }
    }

    public static class ReceiptDto {
        public Long id;
        public String fileName;

        public static ReceiptDto from(Receipt r) {
            if (r == null) return null;
            ReceiptDto dto = new ReceiptDto();
            dto.id = r.getId();
            dto.fileName = r.getFileName();
            return dto;
        }
    }

    public static BankTransactionDto from(BankTransaction txn) {
        BankTransactionDto dto = new BankTransactionDto();
        dto.id              = txn.getId();
        dto.tenantId        = txn.getTenantId();
        dto.txDate          = txn.getTxDate();
        dto.description     = txn.getDescription();
        dto.vendor          = txn.getVendor();
        dto.type            = txn.getType() != null ? txn.getType().name() : null;
        dto.amount          = txn.getAmount();
        dto.reconciled      = txn.getReconciled();
        dto.reconciliationStatus = txn.getReconciliationStatus() != null ? txn.getReconciliationStatus().name() : null;
        dto.referenceNumber = txn.getReferenceNumber();
        dto.createdAt       = txn.getCreatedAt();
        dto.status          = txn.getStatus();
        dto.confidenceScore = txn.getConfidenceScore();
        dto.aiReasoning      = txn.getAiReasoning();
        dto.originalSnippet = txn.getOriginalSnippet();
        dto.isDuplicate     = txn.getIsDuplicate();

        // Populate nested DTOs (only simple fields, no lazy proxies leak)
        dto.category = CategoryDto.from(txn.getCategory());
        dto.receipt  = ReceiptDto.from(txn.getReceipt());

        return dto;
    }

    // Getters and Setters
    public Long getId()                  { return id; }
    public void setId(Long id)           { this.id = id; }
    
    public String getTenantId()          { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    
    public LocalDate getTxDate()         { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }
    
    public String getDescription()       { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getVendor()            { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    
    public String getType()              { return type; }
    public void setType(String type)     { this.type = type; }
    
    public BigDecimal getAmount()        { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    @Deprecated
    public Boolean getReconciled()       { return reconciled; }
    @Deprecated
    public void setReconciled(Boolean reconciled) { this.reconciled = reconciled; }
    
    public String getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(String reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }
    
    public String getReferenceNumber()   { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public CategoryDto getCategory()     { return category; }
    public void setCategory(CategoryDto category) { this.category = category; }
    
    public ReceiptDto getReceipt()       { return receipt; }
    public void setReceipt(ReceiptDto receipt) { this.receipt = receipt; }

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
