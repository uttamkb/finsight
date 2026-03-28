package com.finsight.backend.service;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransactionPatternEnricherTest {

    @Mock
    private BankTransactionCategorizationService categorizationService;

    private TransactionPatternEnricher enricher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enricher = new TransactionPatternEnricher(categorizationService);
    }

    @Test
    void testEnrichIfMatches_WithValidFlatPattern() {
        // Arrange
        BankTransaction txn = new BankTransaction();
        txn.setVendor("AGF4621387");
        txn.setAmount(new BigDecimal(1000));
        txn.setType(BankTransaction.TransactionType.CREDIT);
        
        Category mockCategory = new Category();
        mockCategory.setName("Maintenance");
        when(categorizationService.getOrCreateCategoryEntity(eq("Maintenance"), anyString()))
                .thenReturn(mockCategory);

        // Act
        enricher.enrichIfMatches(txn);

        // Assert
        assertEquals("A-GF-46", txn.getVendorNormalized());
        assertEquals("A", txn.getBlock());
        assertEquals("GF", txn.getFloor());
        assertEquals("46", txn.getFlatNumber());
        assertEquals("RESIDENT", txn.getVendorType());
        assertEquals("Per Flat Charge", txn.getSubCategory());
        assertEquals("Maintenance", txn.getCategory().getName());
    }

    @Test
    void testEnrichIfMatches_WithImpsStructuralPattern() {
        // Arrange: MMT/IMPS/602416673696/RepublicDaySwee/GAJANANDSW/UTIB0003270
        BankTransaction txn = new BankTransaction();
        txn.setVendor("MMT/IMPS/602416673696/RepublicDaySwee/GAJANANDSW/UTIB0003270");
        txn.setType(BankTransaction.TransactionType.DEBIT);

        Category mockCategory = new Category();
        mockCategory.setName("Celebration");
        when(categorizationService.getOrCreateCategoryEntity(eq("Celebration"), anyString()))
                .thenReturn(mockCategory);

        // Act
        enricher.enrichIfMatches(txn);

        // Assert
        assertEquals("GAJANANDSW", txn.getVendor());
        assertEquals("GAJANANDSW", txn.getVendorNormalized());
        assertEquals("Celebration", txn.getCategory().getName());
        assertTrue(txn.getAiReasoning().contains("Structural parse"));
    }

    @Test
    void testEnrichIfMatches_WithNonMatchingVendor_Unchanged() {
        // Arrange
        BankTransaction txn = new BankTransaction();
        txn.setVendor("AMAZON PAY");
        Category originalCategory = new Category();
        originalCategory.setName("Shopping");
        txn.setCategory(originalCategory);

        // Act
        enricher.enrichIfMatches(txn);

        // Assert
        assertNull(txn.getVendorNormalized());
        assertEquals("Shopping", txn.getCategory().getName());
    }

    @Test
    void testEnrichIfMatches_WithNullVendor_NoCrash() {
        // Arrange
        BankTransaction txn = new BankTransaction();
        txn.setVendor(null);

        // Act & Assert
        assertDoesNotThrow(() -> enricher.enrichIfMatches(txn));
    }
}
