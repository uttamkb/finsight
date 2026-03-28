package com.finsight.backend.service;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Vendor;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorServiceTest {

    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private VendorRepository vendorRepository;

    private VendorService vendorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vendorService = new VendorService(bankTransactionRepository, receiptRepository, vendorRepository);
    }

    @Test
    void getTopVendors_ReturnsMappedDtos() {
        // Arrange
        Vendor v = new Vendor();
        v.setName("Swiggy");
        v.setTotalSpent(new BigDecimal("1000"));
        v.setTotalPayments(10);
        
        when(vendorRepository.findByTenantIdOrderByTotalSpentDesc("local_tenant"))
                .thenReturn(List.of(v));

        // Act
        List<VendorInsightDto> results = vendorService.getTopVendors(5);

        // Assert
        assertEquals(1, results.size());
        assertEquals("Swiggy", results.get(0).getVendorName());
        assertEquals(BigDecimal.valueOf(1000), results.get(0).getTotalSpent());
    }

    @Test
    void getSpendingByCategory_CallsRepository() {
        // Arrange
        CategoryInsightDto dto = new CategoryInsightDto("Food", BigDecimal.valueOf(500));
        when(bankTransactionRepository.getTopSpendingByCategory(anyString(), any()))
                .thenReturn(List.of(dto));

        // Act
        List<CategoryInsightDto> results = vendorService.getSpendingByCategory("t1");

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(bankTransactionRepository).getTopSpendingByCategory("t1", BankTransaction.AccountType.MAINTENANCE);
    }

    @Test
    void getOcrModeStats_ReturnsMap() {
        // Arrange
        List<Object[]> mockStats = java.util.Collections.singletonList(new Object[]{"GEMINI", 10L});
        when(receiptRepository.getOcrModeStats(anyString())).thenReturn(mockStats);

        // Act
        Map<String, Long> stats = vendorService.getOcrModeStats();

        // Assert
        assertEquals(1, stats.size());
        assertEquals(10L, stats.get("GEMINI"));
    }

    @Test
    void getCategoryDrilldown_ReturnsCombinedData() {
        // Arrange
        String tenantId = "t1";
        String category = "Food";
        VendorInsightDto v = new VendorInsightDto("Swiggy", BigDecimal.valueOf(100), 1L);
        BankTransaction txn = new BankTransaction();
        txn.setId(1L);

        when(bankTransactionRepository.getTopSpendingByCategoryAndVendor(eq(tenantId), eq(category), any(), any()))
                .thenReturn(List.of(v));
        when(bankTransactionRepository.findRecentByCategory(eq(tenantId), eq(category), any(), any()))
                .thenReturn(List.of(txn));

        // Act
        Map<String, Object> drilldown = vendorService.getCategoryDrilldown(category, tenantId);

        // Assert
        assertEquals(category, drilldown.get("categoryName"));
        assertEquals(1, ((List<?>)drilldown.get("topVendors")).size());
        assertEquals(1, ((List<?>)drilldown.get("recentTransactions")).size());
    }
}
