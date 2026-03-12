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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String TENANT_ID = "local_tenant";

    // Scoring weights — must sum to 100
    private static final double AMOUNT_WEIGHT = 50.0;
    private static final double DATE_WEIGHT   = 30.0;
    private static final double NAME_WEIGHT   = 20.0;

    // ≥80 = auto-link, 50–79 = flag for human review, <50 = no match
    private static final double AUTO_LINK_THRESHOLD  = 80.0;
    private static final double FUZZY_AUDIT_THRESHOLD = 50.0;

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
                String matchType = bestScore >= 95.0 ? "EXACT" : "FUZZY";
                bankTxn.setReceipt(bestMatch);
                bankTxn.setReconciled(true);
                bankTransactionRepository.save(bankTxn);
                unlinkedReceipts.remove(bestMatch); // prevent double-matching
                matchCount++;
                log.debug("Auto-Matched [{}] '{}' <-> '{}' (score: {})",
                        matchType, bankTxn.getDescription(), bestMatch.getVendor(), String.format("%.1f", bestScore));

            } else if (bestScore >= FUZZY_AUDIT_THRESHOLD && bestMatch != null) {
                // Partial match — needs human review
                createAuditEntry(bankTxn, bestMatch,
                        AuditTrail.IssueType.AMOUNT_MISMATCH,
                        String.format("Partial match (score: %.0f/100). Review required. Bank: '%s' | Receipt: '%s'",
                                bestScore, bankTxn.getDescription(), bestMatch.getVendor()),
                        bestScore, "FUZZY");
            } else {
                // No viable match found
                createAuditEntry(bankTxn, null,
                        AuditTrail.IssueType.BANK_NO_RECEIPT,
                        "No matching receipt found for this bank transaction.",
                        bestScore, "NONE");
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
     * Amount:  up to 50 pts (exact=50, within 0.5%=40, within 5%=25, else=0)
     * Date:    up to 30 pts (0 days=30, 1 day=24, 2 days=18, ≤5 days=9, else=0)
     * Vendor:  up to 20 pts (normalized Levenshtein similarity × 20)
     */
    private double computeScore(BankTransaction bankTxn, Receipt receipt) {
        double score = 0.0;

        // --- Amount ---
        BigDecimal bankAmt    = bankTxn.getAmount();
        BigDecimal receiptAmt = BigDecimal.valueOf(receipt.getAmount());
        double amtDiff = bankAmt.subtract(receiptAmt).abs().doubleValue();
        double avgAmt  = (bankAmt.doubleValue() + receiptAmt.doubleValue()) / 2.0;
        double amtPct  = avgAmt > 0 ? (amtDiff / avgAmt) : 1.0;

        if      (amtPct == 0.0)   score += AMOUNT_WEIGHT;
        else if (amtPct <= 0.005) score += AMOUNT_WEIGHT * 0.8;
        else if (amtPct <= 0.05)  score += AMOUNT_WEIGHT * 0.5;
        // else: 0 pts

        // --- Date ---
        LocalDate bankDate    = bankTxn.getTxDate();
        LocalDate receiptDate = receipt.getDate();
        long daysDiff = Math.abs(bankDate.toEpochDay() - receiptDate.toEpochDay());

        if      (daysDiff == 0) score += DATE_WEIGHT;
        else if (daysDiff <= 1) score += DATE_WEIGHT * 0.8;
        else if (daysDiff <= 2) score += DATE_WEIGHT * 0.6;
        else if (daysDiff <= 5) score += DATE_WEIGHT * 0.3;
        // else: 0 pts

        // --- Vendor name similarity ---
        String bankDesc     = normalize(bankTxn.getDescription());
        String receiptVendor = normalize(receipt.getVendor());
        score += NAME_WEIGHT * levenshteinSimilarity(bankDesc, receiptVendor);

        return score;
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
}
