package com.finsight.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DashboardStatsDto {
    private long totalReceipts;
    private long totalBankTransactions;
    private long unreconciledItems;
    private BigDecimal currentMonthBurnRate; // Daily average for current month
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    
    // New fields for the user request
    private List<CategoryInsightDto> expenseByCategory;
    private List<DailySummaryDto> last30DaysDailySpend;


    public static class DailySummaryDto {
        private String date;
        private BigDecimal spend;
        
        public DailySummaryDto(String date, BigDecimal spend) {
            this.date = date;
            this.spend = spend;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public BigDecimal getSpend() { return spend; }
        public void setSpend(BigDecimal spend) { this.spend = spend; }
    }

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
    public List<CategoryInsightDto> getExpenseByCategory() { return expenseByCategory; }
    public void setExpenseByCategory(List<CategoryInsightDto> expenseByCategory) { this.expenseByCategory = expenseByCategory; }
    public List<DailySummaryDto> getLast30DaysDailySpend() { return last30DaysDailySpend; }
    public void setLast30DaysDailySpend(List<DailySummaryDto> last30DaysDailySpend) { this.last30DaysDailySpend = last30DaysDailySpend; }
}
