package com.finsight.backend.service.impl;

import com.finsight.backend.dto.ReconciliationResultDto;
import com.finsight.backend.dto.ReconciliationStatsDto;
import com.finsight.backend.entity.AuditTrail;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.entity.ReconciliationStatus;
import com.finsight.backend.entity.VendorAlias;
import com.finsight.backend.repository.AuditTrailRepository;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.repository.VendorAliasRepository;
import com.finsight.backend.service.ReconciliationService;
import com.finsight.backend.service.VendorManager;
import com.finsight.backend.service.reconciliation.ConfidenceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.finsight.backend.entity.ReconciliationRun;
import com.finsight.backend.repository.ReconciliationRunRepository;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private final BankTransactionRepository bankTransactionRepository;
    private final ReceiptRepository receiptRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ConfidenceCalculator confidenceCalculator;
    private final VendorAliasRepository vendorAliasRepository;
    private final VendorManager vendorManager;

    public ReconciliationServiceImpl(BankTransactionRepository bankTransactionRepository,
            ReceiptRepository receiptRepository,
            AuditTrailRepository auditTrailRepository,
            ReconciliationRunRepository reconciliationRunRepository,
            ConfidenceCalculator confidenceCalculator,
            VendorAliasRepository vendorAliasRepository,
            VendorManager vendorManager) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.receiptRepository = receiptRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.confidenceCalculator = confidenceCalculator;
        this.vendorAliasRepository = vendorAliasRepository;
        this.vendorManager = vendorManager;
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

        List<ReconciliationStatus> targetStatuses = List.of(ReconciliationStatus.PENDING,
                ReconciliationStatus.MANUAL_REVIEW);
        List<BankTransaction> transactions = bankTransactionRepository
                .findByTenantIdAndAccountTypeAndReconciliationStatusIn(
                        tenantId, accType, targetStatuses);

        int matchedCount = 0;
        int manualReviewCount = 0;
        List<String> logs = new ArrayList<>();

        for (BankTransaction tx : transactions) {
            // Only process DEBITS for matching with receipts
            if (tx.getType() == BankTransaction.TransactionType.CREDIT)
                continue;

            BigDecimal txAmount = tx.getAmount();
            BigDecimal minAmt = txAmount.multiply(BigDecimal.valueOf(0.99));
            BigDecimal maxAmt = txAmount.multiply(BigDecimal.valueOf(1.01));

            // Search for candidates in PENDING or MANUAL_REVIEW
            List<Receipt> candidates = receiptRepository.findCandidatesByAmountRange(tenantId, minAmt, maxAmt,
                    targetStatuses);

            Receipt bestMatch = null;
            double bestScore = 0;
            ConfidenceCalculator.MatchScoreResult bestDetailedScore = null;

            for (Receipt r : candidates) {
                ConfidenceCalculator.MatchScoreResult detailedScore = confidenceCalculator.computeScore(tx, r);
                double score = detailedScore.getTotalScore();
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = r;
                    bestDetailedScore = detailedScore;
                }
            }

            if (bestMatch != null && bestScore >= 90) {
                tx.setReceipt(bestMatch);
                tx.setReconciliationStatus(ReconciliationStatus.MATCHED);
                tx.setMatchScore(bestScore);
                tx.setMatchType("AUTO_FUZZY");
                tx.setAuditLog("Auto-matched with Receipt ID: " + bestMatch.getId() + " (Score: "
                        + String.format("%.2f", bestScore) + ")");

                bestMatch.setReconciliationStatus(ReconciliationStatus.MATCHED);
                bestMatch.setMatchedBankTransactionId(tx.getId());

                bankTransactionRepository.save(tx);
                receiptRepository.save(bestMatch);

                resolveExistingAuditTrails(tx.getId(), bestMatch.getId());

                // ── Vendor Intel: Receipt is the canonical source for vendor names ──
                // Bank statements give us the financial anchor (amount + date).
                // Receipts give us the clean, OCR-extracted vendor name.
                // When a match is confirmed, update vendor stats using the receipt's name.
                String canonicalVendor = (bestMatch.getVendor() != null && !bestMatch.getVendor().isBlank())
                        ? bestMatch.getVendor()
                        : tx.getVendor(); // fallback to bank's description if receipt vendor is missing
                vendorManager.updateVendorStats(tenantId, canonicalVendor, tx.getAmount(), tx.getTxDate());

                matchedCount++;
                logs.add("Matched Transaction " + tx.getId() + " with Receipt " + bestMatch.getId() + " (Score: "
                        + bestScore + ")");
            } else {
                manualReviewCount++;
                // If no match found, move to MANUAL_REVIEW if it was PENDING
                if (tx.getReconciliationStatus() == ReconciliationStatus.PENDING) {
                    tx.setReconciliationStatus(ReconciliationStatus.MANUAL_REVIEW);
                    bankTransactionRepository.save(tx);
                }
                // ── Vendor Intel fallback: no receipt found, use bank's vendor description ──
                // Bank statement remains the anchor for unmatched transactions.
                if (tx.getVendor() != null && !tx.getVendor().isBlank()) {
                    vendorManager.updateVendorStats(tenantId, tx.getVendor(), tx.getAmount(), tx.getTxDate());
                }
                createOrUpdateAuditTrail(tx, bestMatch, bestScore, bestDetailedScore);
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

        // ── Vendor Intel: use receipt's clean vendor name on manual linking too ──
        String canonicalVendor = (receipt.getVendor() != null && !receipt.getVendor().isBlank())
                ? receipt.getVendor()
                : tx.getVendor();
        vendorManager.updateVendorStats(tx.getTenantId(), canonicalVendor, tx.getAmount(), tx.getTxDate());

        // Dynamic Vendor Learning
        String txVendor = tx.getVendor() != null && !tx.getVendor().equals("Unknown") ? tx.getVendor()
                : tx.getDescription();
        String receiptVendor = receipt.getVendor();

        if (txVendor != null && !txVendor.trim().isEmpty() && receiptVendor != null
                && !receiptVendor.trim().isEmpty()) {
            String aliasName = confidenceCalculator.getVendorNormalizationService().normalize(txVendor);
            String canonicalName = confidenceCalculator.getVendorNormalizationService().normalize(receiptVendor);

            if (!aliasName.isEmpty() && !canonicalName.isEmpty() && !aliasName.equals(canonicalName)) {
                VendorAlias vendorAlias = vendorAliasRepository.findByTenantIdAndAliasName(tx.getTenantId(), aliasName)
                        .orElse(new VendorAlias());

                if (vendorAlias.getId() == null) {
                    vendorAlias.setTenantId(tx.getTenantId());
                    vendorAlias.setAliasName(aliasName);
                    vendorAlias.setCanonicalName(canonicalName);
                    vendorAlias.setApprovalCount(1);
                    if (tx.getCategory() != null) {
                        vendorAlias.setCategory(tx.getCategory());
                    }
                } else if (vendorAlias.getCanonicalName().equals(canonicalName)) {
                    vendorAlias.setApprovalCount(vendorAlias.getApprovalCount() + 1);
                } else {
                    // Changing target canonical name implies lower confidence, reset count
                    vendorAlias.setCanonicalName(canonicalName);
                    vendorAlias.setApprovalCount(1);
                }

                vendorAliasRepository.save(vendorAlias);
            }
        }
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

    private void createOrUpdateAuditTrail(BankTransaction tx, Receipt bestMatch, double score,
            ConfidenceCalculator.MatchScoreResult detailedScore) {
        // Only create if doesn't exist
        if (!auditTrailRepository.existsByTransactionIdAndIssueTypeAndResolvedFalse(tx.getId(),
                AuditTrail.IssueType.SUGGESTED_MATCH)) {
            AuditTrail trail = new AuditTrail();
            trail.setTenantId(tx.getTenantId());
            trail.setTransaction(tx);
            if (bestMatch != null) {
                trail.setReceipt(bestMatch);
                trail.setIssueType(AuditTrail.IssueType.SUGGESTED_MATCH);
                trail.setIssueDescription("Suggested match with Score: " + String.format("%.2f", score));
                trail.setSimilarityScore(score);
                trail.setMatchType("FUZZY");

                if (detailedScore != null) {
                    trail.setAmountScore(detailedScore.getAmountMatch().getScore());
                    trail.setAmountReasoning(detailedScore.getAmountMatch().getReasoning());
                    trail.setDateScore(detailedScore.getDateMatch().getScore());
                    trail.setDateReasoning(detailedScore.getDateMatch().getReasoning());
                    trail.setVendorScore(detailedScore.getVendorMatch().getScore());
                    trail.setVendorReasoning(detailedScore.getVendorMatch().getReasoning());
                }
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
}
