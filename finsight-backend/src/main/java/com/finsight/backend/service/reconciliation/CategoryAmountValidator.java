package com.finsight.backend.service.reconciliation;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class CategoryAmountValidator {

    /**
     * Checks if the amount is within typical ranges for the given category.
     * Returns true if it's within expected ranges or if no range is defined.
     * Returns false if it's an anomaly.
     */
    public boolean isAmountTypicalForCategory(String categoryName, BigDecimal amount) {
        if (categoryName == null || amount == null) {
            return true;
        }

        double val = amount.doubleValue();

        return switch (categoryName) {
            case "Electricity" -> val >= 1000 && val <= 50000;
            case "Water Supply" -> val >= 500 && val <= 30000;
            case "Internet" -> val >= 500 && val <= 5000;
            case "Security", "Housekeeping", "Maintenance" -> val >= 5000 && val <= 500000;
            case "Lift Maintenance" -> val >= 2000 && val <= 200000;
            case "Salary" -> val >= 5000 && val <= 100000;
            case "Bank Fees" -> val >= 1 && val <= 2000;
            default -> true; // For unconfigured categories, assume it's typical
        };
    }
}
