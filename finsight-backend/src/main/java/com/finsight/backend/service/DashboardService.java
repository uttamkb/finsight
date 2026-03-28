package com.finsight.backend.service;

import com.finsight.backend.dto.DashboardStatsDto;
import com.finsight.backend.dto.MonthlySummaryDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ReceiptRepository receiptRepository;

    public DashboardService(BankTransactionRepository bankTransactionRepository, ReceiptRepository receiptRepository) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.receiptRepository = receiptRepository;
    }

    public DashboardStatsDto getStats(String tenantId, String accountType) {
        BankTransaction.AccountType accType = parseAccountType(accountType);
        
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        BigDecimal monthlyInflow = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
                tenantId, BankTransaction.TransactionType.CREDIT, startOfMonth, endOfMonth, accType);
        BigDecimal monthlyOutflow = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
                tenantId, BankTransaction.TransactionType.DEBIT, startOfMonth, endOfMonth, accType);

        monthlyInflow = monthlyInflow != null ? monthlyInflow : BigDecimal.ZERO;
        monthlyOutflow = monthlyOutflow != null ? monthlyOutflow : BigDecimal.ZERO;

        long pendingReconciliation = bankTransactionRepository.countUnreconciledByTenantIdAndAccountType(tenantId, accType);
        long pendingReceipts = receiptRepository.countByTenantIdAndStatus(tenantId, "PENDING");

        BigDecimal burnRate = calculateBurnRate(tenantId, accType);

        DashboardStatsDto stats = new DashboardStatsDto();
        stats.setTotalIncome(monthlyInflow);
        stats.setTotalExpense(monthlyOutflow);
        stats.setUnreconciledItems(pendingReconciliation);
        stats.setTotalReceipts(pendingReceipts);
        stats.setCurrentMonthBurnRate(burnRate);
        
        stats.setExpenseByCategory(bankTransactionRepository.getTopSpendingByCategory(tenantId, accType));

        return stats;
    }

    public List<MonthlySummaryDto> getMonthlyHistory(String tenantId, int months, String accountType) {
        BankTransaction.AccountType accType = parseAccountType(accountType);
        List<MonthlySummaryDto> history = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();

        for (int i = 0; i < months; i++) {
            YearMonth targetMonth = currentMonth.minusMonths(i);
            LocalDate start = targetMonth.atDay(1);
            LocalDate end = targetMonth.atEndOfMonth();

            BigDecimal inflow = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
                    tenantId, BankTransaction.TransactionType.CREDIT, start, end, accType);
            BigDecimal outflow = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
                    tenantId, BankTransaction.TransactionType.DEBIT, start, end, accType);

            MonthlySummaryDto dto = new MonthlySummaryDto(
                targetMonth.toString(),
                inflow != null ? inflow : BigDecimal.ZERO,
                outflow != null ? outflow : BigDecimal.ZERO
            );
            history.add(0, dto); // Add to beginning to maintain chronological order
        }
        return history;
    }

    private BigDecimal calculateBurnRate(String tenantId, BankTransaction.AccountType accType) {
        // Average monthly outflow for the last 3 months
        LocalDate end = LocalDate.now().withDayOfMonth(1).minusDays(1);
        LocalDate start = end.minusMonths(3).withDayOfMonth(1);

        BigDecimal totalOutflow = bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
                tenantId, BankTransaction.TransactionType.DEBIT, start, end, accType);
        
        if (totalOutflow == null || totalOutflow.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalOutflow.divide(new BigDecimal(3), 2, RoundingMode.HALF_UP);
    }

    private BankTransaction.AccountType parseAccountType(String accountType) {
        if (accountType == null) return BankTransaction.AccountType.MAINTENANCE;
        try {
            return BankTransaction.AccountType.valueOf(accountType.toUpperCase());
        } catch (Exception e) {
            return BankTransaction.AccountType.MAINTENANCE;
        }
    }
}
