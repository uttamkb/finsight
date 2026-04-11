package com.finsight.backend.service.reconciliation;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AmountMatchingService {

    private final CategorySpecificMatchingRules matchingRules;

    public AmountMatchingService(CategorySpecificMatchingRules matchingRules) {
        this.matchingRules = matchingRules;
    }

    public MatchResult matchAmount(BigDecimal txAmount, BigDecimal receiptAmount, String categoryName) {
        double score = 0;
        String reasoning = "";

        if (txAmount == null || receiptAmount == null) {
            return new MatchResult(0, "Missing amount");
        }

        BigDecimal absTx = txAmount.abs();
        BigDecimal absReceipt = receiptAmount.abs();

        if (absTx.compareTo(absReceipt) == 0) {
            score = 95;
            reasoning = "Exact amount match";
        } else {
            BigDecimal allowedTolerance = matchingRules.getAllowedTolerance(categoryName);
            if (absTx.subtract(absReceipt).abs().divide(absTx, 4, RoundingMode.HALF_UP)
                    .compareTo(allowedTolerance) <= 0) {
                // If it's within tolerance, give 85 so it needs date/vendor to hit the 90 point auto-match threshold
                score = 85;
                reasoning = String.format("Amount within %.1f%% tolerance", allowedTolerance.doubleValue() * 100);
            } else {
                score = 0;
                reasoning = "Amount mismatch";
            }
        }

        return new MatchResult(score, reasoning);
    }
}
