package com.finsight.backend.service;

import com.finsight.backend.entity.AuditTrail;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.AuditTrailRepository;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String TENANT_ID = "local_tenant";

    // Scoring weights — must sum to 100
    private static final double AMOUNT_WEIGHT = 60.0;
    private static final double NAME_WEIGHT   = 30.0;
    private static final double DATE_WEIGHT   = 10.0;

    // ≥70 = auto-link, 40–69 = flag for human review (suggested match), <40 = no match
    private static final double AUTO_LINK_THRESHOLD  = 70.0;
    private static final double SUGGESTED_MATCH_THRESHOLD = 40.0;

    private final BankTransactionRepository bankTransactionRepository;
    private final ReceiptRepository receiptRepository;
    private final AuditTrailRepository auditTrailRepository;

    public ReconciliationService(BankTransactionRepository bankTransactionRepository,
                                 ReceiptRepository receiptRepository,
                                 AuditTrailRepository auditTrailRepository) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.receiptRepository = receiptRepository;
        this.auditTrailRepository = auditTrailRepository;
    }

    /**
     * Runs similarity-scored reconciliation.
     * Score: amount (50 pts) + date proximity (30 pts) + name Levenshtein (20 pts).
     * Auto-links ≥80, creates AMOUNT_MISMATCH audit at 50–79, BANK_NO_RECEIPT below.
     *
     * @return number of newly auto-linked pairs
     */
    @Transactional
    public int runReconciliation() {
        log.info("Starting Auto-Reconciliation Engine with Similarity Scoring...");

        // 1. Fetch unreconciled debit transactions
        List<BankTransaction> unlinkedBankTxns = bankTransactionRepository
                .findByTenantIdAndReconciledFalseAndType(TENANT_ID, BankTransaction.TransactionType.DEBIT);

        // 2. Fetch all processed receipts
        List<Receipt> allReceipts = receiptRepository.findByTenantId(TENANT_ID);

        // 3. Determine which receipt IDs are already linked — efficiently via a targeted repository call
        List<Long> linkedReceiptIds = bankTransactionRepository.findLinkedReceiptIds(TENANT_ID);

        // 4. Filter to unlinked, processed receipts (use ArrayList so remove() is O(1) mutability safe)
        List<Receipt> unlinkedReceipts = new ArrayList<>(
            allReceipts.stream()
                .filter(r -> "PROCESSED".equals(r.getStatus()) && !linkedReceiptIds.contains(r.getId()))
                .collect(Collectors.toList())
        );

        log.info("Found {} unlinked Bank DEBITS and {} unlinked Processed Receipts.",
                unlinkedBankTxns.size(), unlinkedReceipts.size());

        int matchCount = 0;

        for (BankTransaction bankTxn : unlinkedBankTxns) {
            Receipt bestMatch = null;
            double bestScore = 0.0;

            for (Receipt receipt : unlinkedReceipts) {
                // Skip receipts with missing required fields
                if (receipt.getAmount() == null || receipt.getDate() == null || bankTxn.getAmount() == null) continue;
                double score = computeScore(bankTxn, receipt);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = receipt;
                }
            }

            if (bestScore >= AUTO_LINK_THRESHOLD && bestMatch != null) {
                // Auto-link the pair
                String matchType = bestScore >= 99.0 ? "EXACT" : "AUTO_MATCH";
                bankTxn.setReceipt(bestMatch);
                bankTxn.setReconciled(true);
                bankTransactionRepository.save(bankTxn);
                unlinkedReceipts.remove(bestMatch); // prevent double-matching
                matchCount++;
                log.debug("Auto-Matched [{}] '{}' <-> '{}' (score: {})",
                        matchType, bankTxn.getDescription(), bestMatch.getVendor(), String.format("%.1f", bestScore));

            } else if (bestScore >= SUGGESTED_MATCH_THRESHOLD && bestMatch != null) {
                // Partial match — needs human review. H1: idempotency guard
                if (!auditTrailRepository.existsByTransactionIdAndIssueTypeAndResolvedFalse(
                        bankTxn.getId(), AuditTrail.IssueType.SUGGESTED_MATCH)) {
                    createAuditEntry(bankTxn, bestMatch,
                            AuditTrail.IssueType.SUGGESTED_MATCH,
                            String.format("Suggested match (score: %.0f/100). Review required. Bank: '%s' | Receipt: '%s'",
                                    bestScore, bankTxn.getDescription(), bestMatch.getVendor()),
                            bestScore, "SUGGESTED_MATCH");
                }
            } else {
                // No viable match found. H1: idempotency guard
                if (!auditTrailRepository.existsByTransactionIdAndIssueTypeAndResolvedFalse(
                        bankTxn.getId(), AuditTrail.IssueType.UNMATCHED)) {
                    createAuditEntry(bankTxn, null,
                            AuditTrail.IssueType.UNMATCHED,
                            "No matching receipt found for this bank transaction (score < 40).",
                            bestScore, "NONE");
                }
            }
        }

        // Reverse pass: receipts with no bank transaction match
        for (Receipt leftoverReceipt : unlinkedReceipts) {
            createAuditEntry(null, leftoverReceipt,
                    AuditTrail.IssueType.RECEIPT_NO_BANK,
                    "Receipt exists but no corresponding bank transaction was found.",
                    0.0, "NONE");
        }

        log.info("Reconciliation complete. Auto-linked {} pairs.", matchCount);
        return matchCount;
    }

    // -------------------------------------------------------
    // Scoring engine
    // -------------------------------------------------------

    /**
     * Computes a 100-point similarity score between a bank debit and a receipt.
     * Amount:  60 pts (exact match)
     * Vendor:  30 pts (similarity > 70%)
     * Date:    10 pts (within 3 days)
     */
    double computeScore(BankTransaction bankTxn, Receipt receipt) {
        double score = 0.0;

        // --- 1. Amount Match (60 pts, tiered — H2) ---
        BigDecimal bankAmt    = bankTxn.getAmount();
        BigDecimal receiptAmt = receipt.getAmount(); // now BigDecimal (C2 fix)
        if (bankAmt != null && receiptAmt != null && bankAmt.compareTo(BigDecimal.ZERO) > 0) {
            double pct = bankAmt.subtract(receiptAmt).abs()
                             .divide(bankAmt, 4, RoundingMode.HALF_UP)
                             .doubleValue();
            if (pct == 0.0)       score += 60; // exact
            else if (pct <= 0.02) score += 45; // within 2%
            else if (pct <= 0.05) score += 25; // within 5%
        }

        // --- 2. Date Proximity (10 pts) ---
        LocalDate bankDate    = bankTxn.getTxDate();
        LocalDate receiptDate = receipt.getDate();
        long daysDiff = Math.abs(bankDate.toEpochDay() - receiptDate.toEpochDay());

        if (daysDiff <= 3) {
            score += DATE_WEIGHT;
        }

        // --- 3. Vendor Similarity (30 pts) ---
        String bankVendor = bankTxn.getVendor() != null ? bankTxn.getVendor() : bankTxn.getDescription();
        double similarity = calculateVendorSimilarity(bankVendor, receipt.getVendor());
        if (similarity > 0.70) {
            score += NAME_WEIGHT;
        }

        return score;
    }

    /** Calculates fuzzy vendor similarity favoring substring overlap to handle "Amazon" vs "Amazon Pay India". */
    private double calculateVendorSimilarity(String bankVendor, String receiptVendor) {
        String a = normalize(bankVendor);
        String b = normalize(receiptVendor);

        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        // Exact
        if (a.equals(b)) return 1.0;

        // Substring / Overlap based on words
        String[] tokensA = a.split(" ");
        String[] tokensB = b.split(" ");
        int matchCount = 0;
        for (String tokenA : tokensA) {
            if (tokenA.isEmpty()) continue;
            for (String tokenB : tokensB) {
                if (tokenB.isEmpty()) continue;
                if (tokenA.equals(tokenB) || levenshteinSimilarity(tokenA, tokenB) > 0.8) {
                    matchCount++;
                    break;
                }
            }
        }

        int minTokens = Math.min(tokensA.length, tokensB.length);
        if (minTokens == 0) return 0.0;
        double overlapSim = (double) Math.min(matchCount, minTokens) / minTokens;
        
        // Also check overall character levenshtein
        double levSim = levenshteinSimilarity(a, b);

        return Math.max(overlapSim, levSim);
    }

    /** Normalized Levenshtein similarity in [0.0, 1.0]. */
    private double levenshteinSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen   = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                           Math.min(dp[i][j - 1] + 1,
                                    dp[i - 1][j - 1] + cost));
            }
        }
        return dp[a.length()][b.length()];
    }

    /** Lowercase, strip non-alphanumeric (keep spaces) for fair comparison. */
    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private void createAuditEntry(BankTransaction bankTxn, Receipt receipt,
                                  AuditTrail.IssueType issueType, String description,
                                  double score, String matchType) {
        AuditTrail audit = new AuditTrail();
        audit.setTransaction(bankTxn);
        audit.setReceipt(receipt);
        audit.setIssueType(issueType);
        audit.setIssueDescription(description);
        audit.setSimilarityScore(score);
        audit.setMatchType(matchType);
        auditTrailRepository.save(audit);
    }

    /**
     * Manually links a bank transaction to a receipt, bypassing similarity thresholds.
     * Also marks any associated audit trail entries as resolved.
     */
    @Transactional
    public void manuallyLink(Long transactionId, Long receiptId) {
        log.info("Attempting manual link: BankTxn[{}] <-> Receipt[{}]", transactionId, receiptId);
        
        BankTransaction txn = bankTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Bank transaction not found"));
        
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));

        if (Boolean.TRUE.equals(txn.getReconciled())) {
            throw new IllegalStateException("Transaction is already reconciled");
        }

        // Link entities
        txn.setReceipt(receipt);
        txn.setReconciled(true);
        bankTransactionRepository.save(txn);

        // H4 — Resolve associated audit trails using targeted DB query (no full table scan)
        List<AuditTrail> pendingAudits = auditTrailRepository.findUnresolvedByTxnOrReceipt(transactionId, receiptId);

        for (AuditTrail audit : pendingAudits) {
            audit.setResolved(true);
            audit.setResolvedAt(LocalDateTime.now());
            audit.setResolvedBy("user_manual_link");
            auditTrailRepository.save(audit);
        }
        
        log.info("Successfully manually linked Txn[{}] and Receipt[{}] and resolved {} audit records.", 
                transactionId, receiptId, pendingAudits.size());
    }
}
