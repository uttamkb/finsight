package com.finsight.backend.service;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.DashboardStatsDto;
import com.finsight.backend.dto.MonthlySummaryDto;
import com.finsight.backend.dto.ProjectionDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ReceiptRepository receiptRepository;
    private final BankTransactionRepository bankTransactionRepository;

    public DashboardService(ReceiptRepository receiptRepository, BankTransactionRepository bankTransactionRepository) {
        this.receiptRepository = receiptRepository;
        this.bankTransactionRepository = bankTransactionRepository;
    }

    public DashboardStatsDto getStats() {
        DashboardStatsDto stats = new DashboardStatsDto();
        String tenantId = "local_tenant";

        stats.setTotalReceipts(receiptRepository.count());
        stats.setTotalBankTransactions(bankTransactionRepository.count());
        stats.setUnreconciledItems(bankTransactionRepository.countUnreconciledByTenantId(tenantId));

        LocalDate now = LocalDate.now();
        LocalDate firstDayOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDayOfMonth = now.with(TemporalAdjusters.lastDayOfMonth());

        // Check if current month has data
        BigDecimal monthIncome = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRange(
                tenantId, BankTransaction.TransactionType.CREDIT, firstDayOfMonth, lastDayOfMonth);
        BigDecimal monthExpense = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRange(
                tenantId, BankTransaction.TransactionType.DEBIT, firstDayOfMonth, lastDayOfMonth);

        // Fallback: If current month is empty, use the most recent available transaction month
        if ((monthIncome == null || monthIncome.compareTo(BigDecimal.ZERO) == 0) &&
            (monthExpense == null || monthExpense.compareTo(BigDecimal.ZERO) == 0)) {
            
            List<BankTransaction> latest = bankTransactionRepository.findAllByTenantIdOrderByTxDateDesc(tenantId, PageRequest.of(0, 1));
            if (!latest.isEmpty()) {
                LocalDate latestDate = latest.get(0).getTxDate();
                firstDayOfMonth = latestDate.with(TemporalAdjusters.firstDayOfMonth());
                lastDayOfMonth = latestDate.with(TemporalAdjusters.lastDayOfMonth());
                
                monthIncome = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRange(
                        tenantId, BankTransaction.TransactionType.CREDIT, firstDayOfMonth, lastDayOfMonth);
                monthExpense = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRange(
                        tenantId, BankTransaction.TransactionType.DEBIT, firstDayOfMonth, lastDayOfMonth);
            }
        }

        stats.setTotalIncome(monthIncome != null ? monthIncome : BigDecimal.ZERO);
        stats.setTotalExpense(monthExpense != null ? monthExpense : BigDecimal.ZERO);

        // Daily Burn rate for the selected month
        int daysToDivide = (firstDayOfMonth.getMonthValue() == now.getMonthValue()) ? now.getDayOfMonth() : lastDayOfMonth.getDayOfMonth();
        if (stats.getTotalExpense().compareTo(BigDecimal.ZERO) > 0) {
            stats.setCurrentMonthBurnRate(stats.getTotalExpense().divide(BigDecimal.valueOf(daysToDivide), 2, RoundingMode.HALF_UP));
        } else {
            stats.setCurrentMonthBurnRate(BigDecimal.ZERO);
        }

        // 1. Expense by Category
        List<CategoryInsightDto> categoryInsights = bankTransactionRepository.getTopSpendingByCategory(tenantId);
        stats.setExpenseByCategory(categoryInsights);

        // 2. Last 30 Days Daily Spend (Trend)
        LocalDate thirtyDaysAgo = now.minusDays(30);
        List<BankTransaction> recentTxns = bankTransactionRepository.findAllByTenantIdOrderByTxDateDesc(tenantId, PageRequest.of(0, 5000))
                .stream()
                .filter(t -> !t.getTxDate().isBefore(thirtyDaysAgo))
                .filter(t -> t.getType() == BankTransaction.TransactionType.DEBIT)
                .collect(Collectors.toList());

        stats.setLast30DaysDailySpend(recentTxns.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTxDate().toString(),
                        Collectors.mapping(BankTransaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ))
                .entrySet().stream()
                .map(e -> new DashboardStatsDto.DailySummaryDto(e.getKey(), e.getValue()))
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList()));

        return stats;
    }

    public List<MonthlySummaryDto> getMonthlyHistory() {
        String tenantId = "local_tenant";
        // Fetch last 1000 transactions to be safe for 6 months
        List<BankTransaction> txns = bankTransactionRepository.findAllByTenantIdOrderByTxDateDesc(tenantId, PageRequest.of(0, 1000));
        
        return txns.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTxDate().getYear() + "-" + String.format("%02d", t.getTxDate().getMonthValue()),
                        Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> {
                    BigDecimal income = entry.getValue().stream()
                            .filter(t -> t.getType() == BankTransaction.TransactionType.CREDIT)
                            .map(BankTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal expense = entry.getValue().stream()
                            .filter(t -> t.getType() == BankTransaction.TransactionType.DEBIT)
                            .map(BankTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new MonthlySummaryDto(entry.getKey(), income, expense);
                })
                .sorted((a, b) -> b.getMonth().compareTo(a.getMonth()))
                .limit(6)
                .collect(Collectors.toList());
    }

    public List<ProjectionDto> getProjections() {
        List<MonthlySummaryDto> history = getMonthlyHistory();
        List<ProjectionDto> projections = new ArrayList<>();
        
        if (history.isEmpty()) return projections;

        // Simple projection: Average of history months
        BigDecimal totalExp = history.stream()
                .map(MonthlySummaryDto::getExpense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgExpense = totalExp.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);

        LocalDate nextMonth = LocalDate.now().plusMonths(1);
        for (int i = 0; i < 3; i++) {
            LocalDate target = nextMonth.plusMonths(i);
            String monthLabel = target.getYear() + "-" + String.format("%02d", target.getMonthValue());
            projections.add(new ProjectionDto(monthLabel, avgExpense));
        }

        return projections;
    }
}
