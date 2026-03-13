package com.finsight.backend.service;

import com.finsight.backend.entity.AuditTrail;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.AuditTrailRepository;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    @Mock
    private BankTransactionRepository bankTransactionRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testManuallyLink_Success() {
        // Arrange
        Long txnId = 1L;
        Long receiptId = 100L;

        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setReconciled(false);

        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        AuditTrail audit1 = new AuditTrail();
        audit1.setId(10L);
        audit1.setTransaction(txn);
        audit1.setResolved(false);

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(auditTrailRepository.findByTenantId("local_tenant")).thenReturn(Arrays.asList(audit1));

        // Act
        reconciliationService.manuallyLink(txnId, receiptId);

        // Assert
        assertTrue(txn.getReconciled());
        assertEquals(receipt, txn.getReceipt());
        verify(bankTransactionRepository, times(1)).save(txn);

        // Verify audit trail resolution
        assertTrue(audit1.getResolved());
        assertNotNull(audit1.getResolvedAt());
        assertEquals("user_manual_link", audit1.getResolvedBy());
        verify(auditTrailRepository, times(1)).save(audit1);
    }

    @Test
    void testManuallyLink_AlreadyReconciledThrowsException() {
        // Arrange
        Long txnId = 2L;
        Long receiptId = 200L;

        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setReconciled(true); // Already reconciled

        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                reconciliationService.manuallyLink(txnId, receiptId));
        assertEquals("Transaction is already reconciled", exception.getMessage());
        
        verify(bankTransactionRepository, never()).save(any(BankTransaction.class));
    }

    @Test
    void testManuallyLink_TransactionNotFoundThrowsException() {
        // Arrange
        Long txnId = 3L;
        Long receiptId = 300L;

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                reconciliationService.manuallyLink(txnId, receiptId));
        assertEquals("Bank transaction not found", exception.getMessage());
    }
}
