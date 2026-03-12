package com.finsight.backend.dto;

import java.math.BigDecimal;

public class CategoryInsightDto {
    private String categoryName;
    private BigDecimal totalSpent;

    public CategoryInsightDto(String categoryName, BigDecimal totalSpent) {
        this.categoryName = categoryName;
        this.totalSpent = totalSpent;
    }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
}
