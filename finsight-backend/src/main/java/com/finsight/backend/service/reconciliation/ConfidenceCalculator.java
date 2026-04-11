package com.finsight.backend.service.reconciliation;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Receipt;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceCalculator {

    private final AmountMatchingService amountMatchingService;
    private final DateMatchingService dateMatchingService;
    private final VendorNormalizationService vendorNormalizationService;

    public ConfidenceCalculator(AmountMatchingService amountMatchingService,
            DateMatchingService dateMatchingService,
            VendorNormalizationService vendorNormalizationService) {
        this.amountMatchingService = amountMatchingService;
        this.dateMatchingService = dateMatchingService;
        this.vendorNormalizationService = vendorNormalizationService;
    }

    public VendorNormalizationService getVendorNormalizationService() {
        return vendorNormalizationService;
    }

    public MatchScoreResult computeScore(BankTransaction tx, Receipt receipt) {
        String categoryName = tx.getCategory() != null ? tx.getCategory().getName() : null;
        MatchResult amountMatch = amountMatchingService.matchAmount(tx.getAmount(), receipt.getAmount(), categoryName);
        MatchResult dateMatch = dateMatchingService.matchDate(tx.getTxDate(), receipt.getDate());

        String txVendorStr = tx.getVendor() != null && !tx.getVendor().equals("Unknown") ? tx.getVendor()
                : tx.getDescription();
        MatchResult vendorMatch = vendorNormalizationService.matchVendor(txVendorStr, receipt.getVendor(),
                tx.getTenantId());

        double totalScore = amountMatch.getScore() + dateMatch.getScore() + vendorMatch.getScore();

        // Cap at 100 so it looks good on the UI as a percentage
        totalScore = Math.min(totalScore, 100.0);

        return new MatchScoreResult(
                totalScore,
                amountMatch,
                dateMatch,
                vendorMatch);
    }

    public static class MatchScoreResult {
        private final double totalScore;
        private final MatchResult amountMatch;
        private final MatchResult dateMatch;
        private final MatchResult vendorMatch;

        public MatchScoreResult(double totalScore, MatchResult amountMatch, MatchResult dateMatch,
                MatchResult vendorMatch) {
            this.totalScore = totalScore;
            this.amountMatch = amountMatch;
            this.dateMatch = dateMatch;
            this.vendorMatch = vendorMatch;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public MatchResult getAmountMatch() {
            return amountMatch;
        }

        public MatchResult getDateMatch() {
            return dateMatch;
        }

        public MatchResult getVendorMatch() {
            return vendorMatch;
        }
    }
}
