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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private AuditTrailRepository auditTrailRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---- manuallyLink tests ----

    @Test
    void testManuallyLink_Success() {
        Long txnId = 1L, receiptId = 100L;

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
        // H4: now uses targeted query instead of full table scan
        when(auditTrailRepository.findUnresolvedByTxnOrReceipt(txnId, receiptId)).thenReturn(Arrays.asList(audit1));

        reconciliationService.manuallyLink(txnId, receiptId);

        assertTrue(txn.getReconciled());
        assertEquals(receipt, txn.getReceipt());
        verify(bankTransactionRepository, times(1)).save(txn);
        assertTrue(audit1.getResolved());
        assertNotNull(audit1.getResolvedAt());
        assertEquals("user_manual_link", audit1.getResolvedBy());
        verify(auditTrailRepository, times(1)).save(audit1);
    }

    @Test
    void testManuallyLink_AlreadyReconciledThrowsException() {
        Long txnId = 2L, receiptId = 200L;

        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setReconciled(true);

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(new Receipt()));

        assertThrows(IllegalStateException.class, () -> reconciliationService.manuallyLink(txnId, receiptId));
        verify(bankTransactionRepository, never()).save(any(BankTransaction.class));
    }

    @Test
    void testManuallyLink_TransactionNotFoundThrowsException() {
        when(bankTransactionRepository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> reconciliationService.manuallyLink(3L, 300L));
    }

    // ---- H2: partial amount scoring regression tests ----

    @Test
    void testComputeScore_ExactAmount_VendorMatch_RecentDate() {
        // A perfect match should score 100 (60 + 10 + 30)
        BankTransaction txn = buildTxn(new BigDecimal("1000.00"), "Amazon Pay India", LocalDate.of(2025, 1, 10));
        Receipt receipt = buildReceipt(new BigDecimal("1000.00"), "Amazon", LocalDate.of(2025, 1, 10));

        double score = reconciliationService.computeScore(txn, receipt);

        // Amount exact (60) + date exact (10) + vendor similarity > 70% (30) = 100
        assertEquals(100.0, score, 0.1, "Perfect match should score 100");
    }

    @Test
    void testComputeScore_PartialAmount_Within2Pct_ShouldScoreAtLeast45() {
        // 999 vs 1000 is ~0.1% difference — should get 45 pts for amount
        BankTransaction txn = buildTxn(new BigDecimal("1000.00"), "Uber", LocalDate.of(2025, 1, 10));
        Receipt receipt = buildReceipt(new BigDecimal("999.00"), "Ola", LocalDate.of(2025, 1, 11));

        double score = reconciliationService.computeScore(txn, receipt);

        // Amount within 2% (45) + date within 3 days (10) + vendor similarity low (<70%) = 55
        assertTrue(score >= 45.0, "Should score at least 45 for amount within 2%");
    }

    @Test
    void testComputeScore_NoMatch_ShouldBeLessThan40() {
        // Completely different amount, vendor, and date
        BankTransaction txn = buildTxn(new BigDecimal("5000.00"), "HDFC Bank Fee", LocalDate.of(2025, 1, 1));
        Receipt receipt = buildReceipt(new BigDecimal("100.00"), "Swiggy", LocalDate.of(2025, 3, 15));

        double score = reconciliationService.computeScore(txn, receipt);

        assertTrue(score < 40.0, "Clearly unrelated transaction should score < 40");
    }

    // ---- Helpers ----

    private BankTransaction buildTxn(BigDecimal amount, String vendor, LocalDate date) {
        BankTransaction txn = new BankTransaction();
        txn.setAmount(amount);
        txn.setVendor(vendor);
        txn.setDescription(vendor);
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
