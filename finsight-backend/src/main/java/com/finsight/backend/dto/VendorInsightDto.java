package com.finsight.backend.dto;

import java.math.BigDecimal;

public class VendorInsightDto {
    private String vendorName;
    private BigDecimal totalSpent;
    private Long transactionCount;

    public VendorInsightDto(String vendorName, BigDecimal totalSpent, Long transactionCount) {
        this.vendorName = vendorName;
        this.totalSpent = totalSpent;
        this.transactionCount = transactionCount;
    }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
    public Long getTransactionCount() { return transactionCount; }
    public void setTransactionCount(Long transactionCount) { this.transactionCount = transactionCount; }
}
