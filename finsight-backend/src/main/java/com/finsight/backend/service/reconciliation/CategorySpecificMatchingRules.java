package com.finsight.backend.service.reconciliation;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CategorySpecificMatchingRules {

    /**
     * Gets the allowed tolerance based on the category.
     * e.g., allow 5% variance for generic repairs, but only 0% for fixed EMI
     * payments
     */
    public BigDecimal getAllowedTolerance(String categoryName) {
        if (categoryName == null) {
            return BigDecimal.valueOf(0.01); // Default 1%
        }

        return switch (categoryName) {
            case "Electricity", "Water Supply", "Internet" -> BigDecimal.valueOf(0.02); // 2%
            case "Maintenance", "Repair", "Plumbing" -> BigDecimal.valueOf(0.05); // 5%
            case "Loan EMI", "Insurance", "Investments" -> BigDecimal.ZERO; // 0% Strict
            default -> BigDecimal.valueOf(0.01); // Default 1%
        };
    }
}
