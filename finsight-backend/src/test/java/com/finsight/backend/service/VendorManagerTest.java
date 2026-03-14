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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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
    void testUpdateVendorStats_NewVendor() {
        // Arrange
        String tenantId = "test-tenant";
        String vendorName = "New Vendor";
        BigDecimal amount = new BigDecimal("100.50");
        LocalDate date = LocalDate.now();

        when(vendorRepository.findByTenantIdAndName(tenantId, vendorName)).thenReturn(Optional.empty());

        // Act
        vendorManager.updateVendorStats(tenantId, vendorName, amount, date);

        // Assert
        ArgumentCaptor<Vendor> vendorCaptor = ArgumentCaptor.forClass(Vendor.class);
        verify(vendorRepository).save(vendorCaptor.capture());
        
        Vendor savedVendor = vendorCaptor.getValue();
        assertEquals(vendorName, savedVendor.getName());
        assertEquals(amount, savedVendor.getTotalSpent());
        assertEquals(1, savedVendor.getTotalPayments());
        assertEquals(date.atStartOfDay(), savedVendor.getLastPaymentDate());
    }

    @Test
    void testUpdateVendorStats_ExistingVendor() {
        // Arrange
        String tenantId = "test-tenant";
        String vendorName = "Existing Vendor";
        BigDecimal initialSpent = new BigDecimal("50.00");
        int initialPayments = 2;
        LocalDate oldDate = LocalDate.now().minusDays(10);
        
        Vendor existingVendor = new Vendor();
        existingVendor.setTenantId(tenantId);
        existingVendor.setName(vendorName);
        existingVendor.setTotalSpent(initialSpent);
        existingVendor.setTotalPayments(initialPayments);
        existingVendor.setLastPaymentDate(oldDate.atStartOfDay());

        when(vendorRepository.findByTenantIdAndName(tenantId, vendorName)).thenReturn(Optional.of(existingVendor));

        BigDecimal newAmount = new BigDecimal("150.00");
        LocalDate newDate = LocalDate.now();

        // Act
        vendorManager.updateVendorStats(tenantId, vendorName, newAmount, newDate);

        // Assert
        verify(vendorRepository).save(existingVendor);
        assertEquals(initialSpent.add(newAmount), existingVendor.getTotalSpent());
        assertEquals(initialPayments + 1, existingVendor.getTotalPayments());
        assertEquals(newDate.atStartOfDay(), existingVendor.getLastPaymentDate());
    }

    @Test
    void testUpdateVendorStats_NullInputs() {
        // Should not throw exception, just skip
        vendorManager.updateVendorStats(null, "Vendor", new BigDecimal("10"), LocalDate.now());
        vendorManager.updateVendorStats("Tenant", null, new BigDecimal("10"), LocalDate.now());
        vendorManager.updateVendorStats("Tenant", "Vendor", null, LocalDate.now());
        
        verify(vendorRepository, never()).save(any());
    }
}
