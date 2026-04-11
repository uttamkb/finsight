package com.finsight.backend.service.reconciliation;

import com.finsight.backend.entity.VendorAlias;
import com.finsight.backend.repository.VendorAliasRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class VendorNormalizationService {

    private final VendorAliasRepository vendorAliasRepository;

    public VendorNormalizationService(VendorAliasRepository vendorAliasRepository) {
        this.vendorAliasRepository = vendorAliasRepository;
    }

    public MatchResult matchVendor(String txVendorStr, String receiptVendorStr, String tenantId) {
        String v1 = normalize(txVendorStr);
        String v2 = normalize(receiptVendorStr);

        // 1. Check for Learned Aliases first
        if (!v1.isEmpty() && !v2.isEmpty()) {
            Optional<VendorAlias> aliasOpt = vendorAliasRepository.findByTenantIdAndAliasName(tenantId, v1);
            if (aliasOpt.isPresent() && aliasOpt.get().getApprovalCount() >= 2) {
                if (aliasOpt.get().getCanonicalName().equals(v2)) {
                    return new MatchResult(20.0, "Learned vendor alias match (High Confidence)");
                }
            }
        }

        // 2. Fallback to fuzzy or subset matching handling noisy remarks
        double similarity = getSimilarity(v1, v2);

        // If the bank remark contains the receipt vendor name (handling noise)
        if (v1.length() > 3 && v2.length() > 3) {
            if (v1.contains(v2) || v2.contains(v1)) {
                // Boost similarity if one is a substring of another
                similarity = Math.max(similarity, 0.9);
            }
        }

        // Since Amount is 95%, we scale Vendor down to max 5 (acting as a tie-breaker/confidence booster)
        double score = similarity * 5;

        String reasoning = String.format("Vendor similarity %.0f%%", similarity * 100);
        if (similarity == 0) {
            reasoning = "No vendor match";
        } else if (score >= 4.5) {
            reasoning = "Vendor match (Sub-string/Clean)";
        }

        return new MatchResult(score, reasoning);
    }

    public String normalize(String s) {
        if (s == null)
            return "";
        // Replicating exactly what was in ReconciliationServiceImpl
        return s.toUpperCase().replaceAll("[^A-Z0-9 ]", "").trim();
    }

    private double getSimilarity(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty())
            return 0;
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / Math.max(s1.length(), s2.length());
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++)
            dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++)
            dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
