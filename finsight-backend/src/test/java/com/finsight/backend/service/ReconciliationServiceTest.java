package com.finsight.backend.service;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.entity.ReconciliationStatus;
import com.finsight.backend.repository.AuditTrailRepository;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private AuditTrailRepository auditTrailRepository;
    @Mock private com.finsight.backend.repository.ReconciliationRunRepository reconciliationRunRepository;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock save to return the object for ReconciliationRun
        lenient().when(reconciliationRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testManuallyLink_Success() {
        Long txnId = 1L, receiptId = 100L;
        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setReconciliationStatus(ReconciliationStatus.MANUAL_REVIEW);

        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(auditTrailRepository.findUnresolvedByTxnOrReceipt(txnId, receiptId)).thenReturn(Arrays.asList());

        reconciliationService.manuallyLink(txnId, receiptId);

        assertEquals(ReconciliationStatus.MATCHED, txn.getReconciliationStatus());
        assertEquals(receipt, txn.getReceipt());
        assertTrue(txn.getIsManualOverride());
        verify(bankTransactionRepository).save(txn);
        verify(receiptRepository).save(receipt);
    }

    @Test
    void testManuallyLink_AlreadyMatchedThrowsException() {
        Long txnId = 1L, receiptId = 100L;
        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setReconciliationStatus(ReconciliationStatus.MATCHED);

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(new Receipt()));

        assertThrows(IllegalStateException.class, () -> reconciliationService.manuallyLink(txnId, receiptId));
    }

    @Test
    void testRunReconciliation_AutoMatchSuccess() {
        String tenantId = "tenant1";
        BankTransaction txn = buildTxn(new BigDecimal("1000.00"), "Amazon", LocalDate.now());
        txn.setId(1L);
        txn.setTenantId(tenantId);
        txn.setType(BankTransaction.TransactionType.DEBIT);
        txn.setReconciliationStatus(ReconciliationStatus.PENDING);

        Receipt receipt = buildReceipt(new BigDecimal("1000.00"), "Amazon", LocalDate.now());
        receipt.setId(100L);
        receipt.setReconciliationStatus(ReconciliationStatus.PENDING);

        when(reconciliationRunRepository.findFirstByTenantIdAndAccountTypeAndStatus(anyString(), anyString(), eq("RUNNING")))
                .thenReturn(Optional.empty());
        when(bankTransactionRepository.findByTenantIdAndAccountTypeAndReconciliationStatusIn(anyString(), any(), anyList()))
                .thenReturn(Arrays.asList(txn));
        when(receiptRepository.findCandidatesByAmountRange(anyString(), any(), any(), anyList()))
                .thenReturn(Arrays.asList(receipt));

        reconciliationService.runReconciliation(tenantId, "MAINTENANCE");

        assertEquals(ReconciliationStatus.MATCHED, txn.getReconciliationStatus());
        assertEquals(receipt, txn.getReceipt());
        assertEquals(100.0, txn.getMatchScore());
        verify(bankTransactionRepository).save(txn);
        
        // Verify run record update
        verify(reconciliationRunRepository, atLeastOnce()).save(argThat(run -> 
            "COMPLETED".equals(run.getStatus()) && run.getMatchedCount() == 1
        ));
    }

    @Test
    void testRunReconciliation_ConcurrencyGuard() {
        String tenantId = "tenant1";
        com.finsight.backend.entity.ReconciliationRun activeRun = new com.finsight.backend.entity.ReconciliationRun();
        activeRun.setStatus("RUNNING");
        
        when(reconciliationRunRepository.findFirstByTenantIdAndAccountTypeAndStatus(anyString(), anyString(), eq("RUNNING")))
                .thenReturn(Optional.of(activeRun));

        assertThrows(IllegalStateException.class, () -> 
            reconciliationService.runReconciliation(tenantId, "MAINTENANCE")
        );
    }

    private BankTransaction buildTxn(BigDecimal amount, String desc, LocalDate date) {
        BankTransaction txn = new BankTransaction();
        txn.setAmount(amount);
        txn.setDescription(desc);
        txn.setTxDate(date);
        return txn;
    }

    private Receipt buildReceipt(BigDecimal amount, String vendor, LocalDate date) {
        Receipt r = new Receipt();
        r.setAmount(amount);
        r.setVendor(vendor);
        r.setDate(date);
        return r;
    }
}
