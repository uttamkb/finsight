package com.finsight.backend.service;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.DashboardStatsDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private BankTransactionRepository bankTransactionRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardService(receiptRepository, bankTransactionRepository);
    }

    @Test
    void testGetStats_PopulatesRequiredMetrics() {
        String tenantId = "local_tenant";
        when(receiptRepository.count()).thenReturn(10L);
        when(bankTransactionRepository.count()).thenReturn(100L);
        when(bankTransactionRepository.countUnreconciledByTenantId(tenantId)).thenReturn(5L);
        when(bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRange(anyString(), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(5000));

        // Mock category insights
        CategoryInsightDto foodInsight = new CategoryInsightDto("Food", BigDecimal.valueOf(1000));
        when(bankTransactionRepository.getTopSpendingByCategory(tenantId))
                .thenReturn(List.of(foodInsight));

        // Mock recent transactions for daily burn trend
        BankTransaction tx = new BankTransaction();
        tx.setTxDate(LocalDate.now());
        tx.setAmount(BigDecimal.valueOf(100));
        tx.setType(BankTransaction.TransactionType.DEBIT);
        when(bankTransactionRepository.findAllByTenantIdOrderByTxDateDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(List.of(tx));

        DashboardStatsDto stats = dashboardService.getStats();

        assertNotNull(stats);
        assertEquals(10, stats.getTotalReceipts());
        assertEquals(100, stats.getTotalBankTransactions());
        assertEquals(5, stats.getUnreconciledItems());
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(stats.getTotalIncome()));
        
        // Check category insights
        assertNotNull(stats.getExpenseByCategory());
        assertEquals(1, stats.getExpenseByCategory().size());
        assertEquals("Food", stats.getExpenseByCategory().get(0).getCategoryName());

        // Check daily spend
        assertNotNull(stats.getLast30DaysDailySpend());
        assertFalse(stats.getLast30DaysDailySpend().isEmpty());
    }

    @Test
    void testGetMonthlyHistory_GroupsCorrectly() {
        String tenantId = "local_tenant";
        BankTransaction tx = new BankTransaction();
        tx.setTxDate(LocalDate.of(2024, 1, 15));
        tx.setAmount(BigDecimal.valueOf(100));
        tx.setType(BankTransaction.TransactionType.DEBIT);
        
        when(bankTransactionRepository.findAllByTenantIdOrderByTxDateDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(List.of(tx));

        var history = dashboardService.getMonthlyHistory();

        assertEquals(1, history.size());
        assertEquals("2024-01", history.get(0).getMonth());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(history.get(0).getExpense()));
    }
}
