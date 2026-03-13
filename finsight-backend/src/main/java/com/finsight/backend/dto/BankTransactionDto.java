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
    private Boolean reconciled;
    private String referenceNumber;
    private LocalDateTime createdAt;

    // Nested DTOs for UI compatibility
    private CategoryDto category;
    private ReceiptDto receipt;

    public static class CategoryDto {
        public Long id;
        public String name;
        public String type;

        public static CategoryDto from(Category c) {
            if (c == null) return null;
            CategoryDto dto = new CategoryDto();
            dto.id = c.getId();
            dto.name = c.getName();
            dto.type = c.getType() != null ? c.getType().name() : null;
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
        dto.referenceNumber = txn.getReferenceNumber();
        dto.createdAt       = txn.getCreatedAt();

        // Populate nested DTOs (only simple fields, no lazy proxies leak)
        dto.category = CategoryDto.from(txn.getCategory());
        dto.receipt  = ReceiptDto.from(txn.getReceipt());

        return dto;
    }

    // Getters
    public Long getId()                  { return id; }
    public String getTenantId()          { return tenantId; }
    public LocalDate getTxDate()         { return txDate; }
    public String getDescription()       { return description; }
    public String getVendor()            { return vendor; }
    public String getType()              { return type; }
    public BigDecimal getAmount()        { return amount; }
    public Boolean getReconciled()       { return reconciled; }
    public String getReferenceNumber()   { return referenceNumber; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public CategoryDto getCategory()     { return category; }
    public ReceiptDto getReceipt()       { return receipt; }
}
