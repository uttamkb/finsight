package com.finsight.backend.dto;

import java.math.BigDecimal;

public class DashboardStatsDto {
    private long totalReceipts;
    private long totalBankTransactions;
    private long unreconciledItems;
    private BigDecimal currentMonthBurnRate;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;

    // Getters and Setters
    public long getTotalReceipts() { return totalReceipts; }
    public void setTotalReceipts(long totalReceipts) { this.totalReceipts = totalReceipts; }
    public long getTotalBankTransactions() { return totalBankTransactions; }
    public void setTotalBankTransactions(long totalBankTransactions) { this.totalBankTransactions = totalBankTransactions; }
    public long getUnreconciledItems() { return unreconciledItems; }
    public void setUnreconciledItems(long unreconciledItems) { this.unreconciledItems = unreconciledItems; }
    public BigDecimal getCurrentMonthBurnRate() { return currentMonthBurnRate; }
    public void setCurrentMonthBurnRate(BigDecimal currentMonthBurnRate) { this.currentMonthBurnRate = currentMonthBurnRate; }
    public BigDecimal getTotalIncome() { return totalIncome; }
    public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }
    public BigDecimal getTotalExpense() { return totalExpense; }
    public void setTotalExpense(BigDecimal totalExpense) { this.totalExpense = totalExpense; }
}
