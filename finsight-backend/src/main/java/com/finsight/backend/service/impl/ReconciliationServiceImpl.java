package com.finsight.backend.service.impl;

import com.finsight.backend.dto.ReconciliationResultDto;
import com.finsight.backend.dto.ReconciliationStatsDto;
import com.finsight.backend.entity.AuditTrail;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.entity.ReconciliationStatus;
import com.finsight.backend.repository.AuditTrailRepository;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.ReconciliationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.finsight.backend.entity.ReconciliationRun;
import com.finsight.backend.repository.ReconciliationRunRepository;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ReceiptRepository receiptRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;

    public ReconciliationServiceImpl(BankTransactionRepository bankTransactionRepository, 
                                     ReceiptRepository receiptRepository,
                                     AuditTrailRepository auditTrailRepository,
                                     ReconciliationRunRepository reconciliationRunRepository) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.receiptRepository = receiptRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
    }

    @Override
    @Transactional
    public ReconciliationResultDto runReconciliation(String tenantId, String accountType) {
        BankTransaction.AccountType accType = BankTransaction.AccountType.valueOf(accountType.toUpperCase());
        
        // 0. Concurrency Guard
        reconciliationRunRepository.findFirstByTenantIdAndAccountTypeAndStatus(tenantId, accountType, "RUNNING")
            .ifPresent(activeRun -> {
                throw new IllegalStateException("A reconciliation run is already in progress for this account.");
            });

        // 1. Initialize Run Tracking
        ReconciliationRun run = new ReconciliationRun();
        run.setTenantId(tenantId);
        run.setAccountType(accountType);
        run.setStatus("RUNNING");
        run = reconciliationRunRepository.save(run);

        List<ReconciliationStatus> targetStatuses = List.of(ReconciliationStatus.PENDING, ReconciliationStatus.MANUAL_REVIEW);
        List<BankTransaction> transactions = bankTransactionRepository.findByTenantIdAndAccountTypeAndReconciliationStatusIn(
                tenantId, accType, targetStatuses);

        int matchedCount = 0;
        int manualReviewCount = 0;
        List<String> logs = new ArrayList<>();

        for (BankTransaction tx : transactions) {
            // Only process DEBITS for matching with receipts
            if (tx.getType() == BankTransaction.TransactionType.CREDIT) continue;

            BigDecimal txAmount = tx.getAmount();
            BigDecimal minAmt = txAmount.multiply(BigDecimal.valueOf(0.99));
            BigDecimal maxAmt = txAmount.multiply(BigDecimal.valueOf(1.01));

            // Search for candidates in PENDING or MANUAL_REVIEW
            List<Receipt> candidates = receiptRepository.findCandidatesByAmountRange(tenantId, minAmt, maxAmt, targetStatuses);

            Receipt bestMatch = null;
            double bestScore = 0;

            for (Receipt r : candidates) {
                double score = computeScore(tx, r);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = r;
                }
            }

            if (bestMatch != null && bestScore >= 90) {
                tx.setReceipt(bestMatch);
                tx.setReconciliationStatus(ReconciliationStatus.MATCHED);
                tx.setMatchScore(bestScore);
                tx.setMatchType("AUTO_FUZZY");
                tx.setAuditLog("Auto-matched with Receipt ID: " + bestMatch.getId() + " (Score: " + String.format("%.2f", bestScore) + ")");

                bestMatch.setReconciliationStatus(ReconciliationStatus.MATCHED);
                bestMatch.setMatchedBankTransactionId(tx.getId());

                bankTransactionRepository.save(tx);
                receiptRepository.save(bestMatch);
                
                resolveExistingAuditTrails(tx.getId(), bestMatch.getId());
                
                matchedCount++;
                logs.add("Matched Transaction " + tx.getId() + " with Receipt " + bestMatch.getId() + " (Score: " + bestScore + ")");
            } else {
                manualReviewCount++;
                // If no match found, move to MANUAL_REVIEW if it was PENDING
                if (tx.getReconciliationStatus() == ReconciliationStatus.PENDING) {
                    tx.setReconciliationStatus(ReconciliationStatus.MANUAL_REVIEW);
                    bankTransactionRepository.save(tx);
                }
                createOrUpdateAuditTrail(tx, bestMatch, bestScore);
            }
        }

        // 2. Finalize Run Tracking
        run.setMatchedCount(matchedCount);
        run.setManualReviewCount(manualReviewCount);
        run.setStatus("COMPLETED");
        run.setCompletedAt(LocalDateTime.now());
        reconciliationRunRepository.save(run);

        return new ReconciliationResultDto(matchedCount, manualReviewCount, logs);
    }

    @Override
    @Transactional
    public void manuallyLink(Long bankTransactionId, Long receiptId) {
        BankTransaction tx = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        if (tx.getReconciliationStatus() == ReconciliationStatus.MATCHED) {
            throw new IllegalStateException("Transaction is already reconciled");
        }

        tx.setReceipt(receipt);
        tx.setReconciliationStatus(ReconciliationStatus.MATCHED);
        tx.setIsManualOverride(true);
        tx.setMatchType("MANUAL");

        receipt.setReconciliationStatus(ReconciliationStatus.MATCHED);
        receipt.setMatchedBankTransactionId(tx.getId());

        bankTransactionRepository.save(tx);
        receiptRepository.save(receipt);
        
        resolveExistingAuditTrails(bankTransactionId, receiptId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationStatsDto getAuditStatistics(String tenantId) {
        long unresolvedCount = auditTrailRepository.countByTenantIdAndResolved(tenantId, false);
        long totalMatched = bankTransactionRepository.countByTenantIdAndAccountTypeAndReconciliationStatus(
                tenantId, BankTransaction.AccountType.MAINTENANCE, ReconciliationStatus.MATCHED);
        return new ReconciliationStatsDto(unresolvedCount, totalMatched);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditTrail> getAuditTrail(String tenantId, String issueType) {
        if (issueType != null && !issueType.isEmpty()) {
            try {
                AuditTrail.IssueType type = AuditTrail.IssueType.valueOf(issueType.toUpperCase());
                return auditTrailRepository.findByTenantIdAndIssueType(tenantId, type);
            } catch (IllegalArgumentException e) {
                // Return all if issueType is invalid
            }
        }
        return auditTrailRepository.findByTenantId(tenantId);
    }

    @Override
    @Transactional
    public void resolveAnomaly(Long id) {
        AuditTrail trail = auditTrailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit trail entry not found"));
        trail.setResolved(true);
        trail.setResolvedAt(LocalDateTime.now());
        trail.setResolvedBy("manual_operator");
        auditTrailRepository.save(trail);
    }

    private void resolveExistingAuditTrails(Long txnId, Long receiptId) {
        List<AuditTrail> trails = auditTrailRepository.findUnresolvedByTxnOrReceipt(txnId, receiptId);
        for (AuditTrail trail : trails) {
            trail.setResolved(true);
            trail.setResolvedAt(LocalDateTime.now());
            trail.setResolvedBy("system_reconciliation");
            auditTrailRepository.save(trail);
        }
    }

    private void createOrUpdateAuditTrail(BankTransaction tx, Receipt bestMatch, double score) {
        // Only create if doesn't exist
        if (!auditTrailRepository.existsByTransactionIdAndIssueTypeAndResolvedFalse(tx.getId(), AuditTrail.IssueType.SUGGESTED_MATCH)) {
            AuditTrail trail = new AuditTrail();
            trail.setTenantId(tx.getTenantId());
            trail.setTransaction(tx);
            if (bestMatch != null) {
                trail.setReceipt(bestMatch);
                trail.setIssueType(AuditTrail.IssueType.SUGGESTED_MATCH);
                trail.setIssueDescription("Suggested match with Score: " + String.format("%.2f", score));
                trail.setSimilarityScore(score);
                trail.setMatchType("FUZZY");
            } else {
                trail.setIssueType(AuditTrail.IssueType.BANK_NO_RECEIPT);
                trail.setIssueDescription("No matching receipt found for this transaction.");
                trail.setMatchType("NONE");
            }
            auditTrailRepository.save(trail);
        }
    }

    @Override
    @Transactional
    public void markAsNoReceiptNeeded(Long bankTransactionId) {
        BankTransaction tx = bankTransactionRepository.findById(bankTransactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        tx.setReconciliationStatus(ReconciliationStatus.NO_RECEIPT_REQUIRED);
        tx.setIsManualOverride(true);
        tx.setMatchType("NO_RECEIPT_REQUIRED");
        bankTransactionRepository.save(tx);
        
        resolveExistingAuditTrails(bankTransactionId, null);
    }

    private double computeScore(BankTransaction tx, Receipt receipt) {
        double score = 0;
        BigDecimal txAmount = tx.getAmount().abs();
        BigDecimal rAmount = receipt.getAmount().abs();
        if (txAmount.compareTo(rAmount) == 0) score += 50;
        else if (txAmount.subtract(rAmount).abs().divide(txAmount, 4, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(0.01)) <= 0) score += 40;

        if (tx.getTxDate() != null && receipt.getDate() != null) {
            long days = Math.abs(ChronoUnit.DAYS.between(tx.getTxDate(), receipt.getDate()));
            if (days == 0) score += 30;
            else if (days <= 3) score += 20;
            else if (days <= 7) score += 10;
        }

        String v1 = normalize(tx.getVendor() != null && !tx.getVendor().equals("Unknown") ? tx.getVendor() : tx.getDescription());
        String v2 = normalize(receipt.getVendor());
        score += getSimilarity(v1, v2) * 20;

        return score;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toUpperCase().replaceAll("[^A-Z0-9 ]", "").trim();
    }

    private double getSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) return 0;
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / Math.max(s1.length(), s2.length());
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
