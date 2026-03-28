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
        dashboardService = new DashboardService(bankTransactionRepository, receiptRepository);
    }

    @Test
    void testGetStats_PopulatesRequiredMetrics() {
        String tenantId = "local_tenant";
        String accountType = "MAINTENANCE";
        BankTransaction.AccountType accType = BankTransaction.AccountType.MAINTENANCE;

        when(receiptRepository.countByTenantIdAndStatus(eq(tenantId), anyString())).thenReturn(10L);
        when(bankTransactionRepository.countUnreconciledByTenantIdAndAccountType(eq(tenantId), eq(accType))).thenReturn(5L);
        when(bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(anyString(), any(), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(5000));
        
        // Mock category insights
        CategoryInsightDto foodInsight = new CategoryInsightDto("Food", BigDecimal.valueOf(1000));
        when(bankTransactionRepository.getTopSpendingByCategory(eq(tenantId), eq(accType)))
                .thenReturn(List.of(foodInsight));

        DashboardStatsDto stats = dashboardService.getStats(tenantId, accountType);

        assertNotNull(stats);
        assertEquals(10, stats.getTotalReceipts());
        assertEquals(5, stats.getUnreconciledItems());
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(stats.getTotalIncome()));
        
        // Check category insights
        assertNotNull(stats.getExpenseByCategory());
        assertEquals(1, stats.getExpenseByCategory().size());
        assertEquals("Food", stats.getExpenseByCategory().get(0).getCategoryName());
    }

    @Test
    void testGetMonthlyHistory_GroupsCorrectly() {
        String tenantId = "local_tenant";
        String accountType = "MAINTENANCE";
        
        when(bankTransactionRepository.sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(anyString(), any(), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(100));

        var history = dashboardService.getMonthlyHistory(tenantId, 6, accountType);

        assertEquals(6, history.size()); // Should return 6 months of history
        // Check the most recent month
        assertEquals(0, BigDecimal.valueOf(100).compareTo(history.get(5).getExpense()));
    }
}
