package com.finsight.backend.service;

import com.finsight.backend.entity.Vendor;
import com.finsight.backend.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorManagerTest {

    @Mock
    private VendorRepository vendorRepository;

    @InjectMocks
    private VendorManager vendorManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateVendorStats_NewVendor_NormalizesAndSaves() {
        // Arrange
        String tenantId = "tenant-1";
        String vendorName = " swiggy   ";
        BigDecimal amount = new BigDecimal("150.75");
        LocalDate date = LocalDate.of(2024, 3, 15);

        when(vendorRepository.findByTenantIdAndNameIgnoreCase(eq(tenantId), eq("SWIGGY")))
                .thenReturn(Optional.empty());

        // Act
        vendorManager.updateVendorStats(tenantId, vendorName, amount, date);

        // Assert
        ArgumentCaptor<Vendor> captor = ArgumentCaptor.forClass(Vendor.class);
        verify(vendorRepository).save(captor.capture());
        
        Vendor saved = captor.getValue();
        assertEquals("Swiggy", saved.getName()); // Title Case
        assertEquals(BigDecimal.valueOf(150.75), saved.getTotalSpent());
        assertEquals(1, saved.getTotalPayments());
        assertEquals(date.atStartOfDay(), saved.getLastPaymentDate());
    }

    @Test
    void updateVendorStats_ExistingVendor_UpdatesStats() {
        // Arrange
        String tenantId = "tenant-1";
        String vendorName = "Swiggy";
        BigDecimal amount = new BigDecimal("100.00");
        
        Vendor existing = new Vendor();
        existing.setName("Swiggy");
        existing.setTotalSpent(new BigDecimal("500.00"));
        existing.setTotalPayments(5);
        existing.setLastPaymentDate(LocalDate.of(2024, 1, 1).atStartOfDay());

        when(vendorRepository.findByTenantIdAndNameIgnoreCase(eq(tenantId), anyString()))
                .thenReturn(Optional.of(existing));

        // Act
        vendorManager.updateVendorStats(tenantId, vendorName, amount, LocalDate.of(2024, 3, 15));

        // Assert
        assertEquals(new BigDecimal("600.00"), existing.getTotalSpent());
        assertEquals(6, existing.getTotalPayments());
        assertEquals(LocalDate.of(2024, 3, 15).atStartOfDay(), existing.getLastPaymentDate());
        verify(vendorRepository).save(existing);
    }

    @Test
    void updateVendorStats_UnknownOrBlank_DoesNothing() {
        // Act
        vendorManager.updateVendorStats("t1", "UNKNOWN", new BigDecimal("10"), LocalDate.now());
        vendorManager.updateVendorStats("t1", "  ", new BigDecimal("10"), LocalDate.now());
        vendorManager.updateVendorStats(null, "Test", new BigDecimal("10"), LocalDate.now());

        // Assert
        verify(vendorRepository, never()).save(any());
    }
}
