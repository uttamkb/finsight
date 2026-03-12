package com.finsight.backend.dto;

import java.math.BigDecimal;

public class ProjectionDto {
    private String month;
    private BigDecimal projectedExpense;

    public ProjectionDto(String month, BigDecimal projectedExpense) {
        this.month = month;
        this.projectedExpense = projectedExpense;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public BigDecimal getProjectedExpense() { return projectedExpense; }
    public void setProjectedExpense(BigDecimal projectedExpense) { this.projectedExpense = projectedExpense; }
}
