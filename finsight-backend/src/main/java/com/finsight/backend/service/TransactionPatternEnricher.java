package com.finsight.backend.service;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to identify and enrich transactions based on structural patterns.
 * Patterns:
 * 1. Flat identification: AGF4621387 -> Block A, Floor GF, Flat 46.
 * 2. IMPS/UPI structural parsing: MMT/IMPS/602416673696/RepublicDaySwee/GAJANANDSW/UTIB0003270 
 *    -> Extracts Vendor (GAJANANDSW) and Purpose (RepublicDaySwee).
 */
@Service
public class TransactionPatternEnricher {

    private static final Logger log = LoggerFactory.getLogger(TransactionPatternEnricher.class);

    // Pattern for Residents/Flat-based transfers: 1 Letter (Block), 2 Letters (Floor), 2 Digits (Flat)
    private static final Pattern FLAT_PATTERN = Pattern.compile("^([A-Z])([A-Z]{2})(\\d{2})");

    // Pattern for IMPS/UPI based transfers with '/' separators
    // e.g., MMT/IMPS/602416673696/RepublicDaySwee/GAJANANDSW/UTIB0003270
    // or UPI/IMPS/602416673696/Sweets/VENDOR/IFSC
    private static final Pattern IMPS_STRUCTURAL_PATTERN = Pattern.compile("^[^/]+/IMPS/(\\d+)/([^/]+)/([^/]+)/([A-Z0-9]+)$");

    // Mapping for known "Purpose" strings to categories
    private static final Map<String, String> PURPOSE_CATEGORY_MAP = Map.of(
        "RepublicDaySwee", "Celebration",
        "Celebration", "Celebration",
        "Sweets", "Celebration",
        "Event", "Celebration",
        "Festival", "Celebration",
        "Catering", "Food & Dining"
    );

    private final BankTransactionCategorizationService categorizationService;

    public TransactionPatternEnricher(BankTransactionCategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    /**
     * Enriches a transaction if it matches known structural patterns.
     * 
     * @param transaction The transaction to enrich.
     */
    public void enrichIfMatches(BankTransaction transaction) {
        String vendor = transaction.getVendor();
        String description = transaction.getDescription();
        
        // Use the most raw/detailed string as the candidate for structural matching
        String candidate = (vendor != null && vendor.length() >= 5) ? vendor : (description != null ? description : "");
        if (candidate == null || candidate.isBlank()) return;

        // 1. Check for RESIDENT (Flat-based) transfers first
        if (tryEnrichFlat(transaction, candidate)) return;

        // 2. Check for Structural IMPS/UPI patterns
        if (tryEnrichImpsStructural(transaction, candidate)) return;
    }

    private boolean tryEnrichFlat(BankTransaction transaction, String candidate) {
        Matcher matcher = FLAT_PATTERN.matcher(candidate);
        if (matcher.find()) {
            String block = matcher.group(1);
            String floor = matcher.group(2);
            String flatNumber = matcher.group(3);
            String normalizedVendor = String.format("%s-%s-%s", block, floor, flatNumber);

            log.info("Detected flat-based transaction: {} -> {}", candidate, normalizedVendor);

            transaction.setVendorNormalized(normalizedVendor);
            transaction.setBlock(block);
            transaction.setFloor(floor);
            transaction.setFlatNumber(flatNumber);
            transaction.setVendorType("RESIDENT");
            transaction.setSubCategory("Per Flat Charge");

            // Override category to Maintenance if it's a credit
            if (transaction.getType() == BankTransaction.TransactionType.CREDIT) {
                Category maintenanceCategory = categorizationService.getOrCreateCategoryEntity("Maintenance", "CREDIT");
                transaction.setCategory(maintenanceCategory);
            }
            return true;
        }
        return false;
    }

    private boolean tryEnrichImpsStructural(BankTransaction transaction, String candidate) {
        Matcher matcher = IMPS_STRUCTURAL_PATTERN.matcher(candidate);
        if (matcher.find()) {
            String rrn = matcher.group(1);
            String purposeHint = matcher.group(2);
            String vendorName = matcher.group(3);
            String ifsc = matcher.group(4);

            log.info("Detected structural IMPS transaction. RRN: {}, Purpose: {}, Vendor: {}", rrn, purposeHint, vendorName);

            // Set the clean vendor name
            transaction.setVendor(vendorName);
            transaction.setVendorNormalized(vendorName);
            
            // Map the purpose hint to a category
            String categoryName = PURPOSE_CATEGORY_MAP.getOrDefault(purposeHint, "Uncategorized");
            if (categoryName.equals("Uncategorized") && !purposeHint.isBlank()) {
                // If it's a known Celebration-related keyword from the user request
                if (purposeHint.toLowerCase().contains("swee") || purposeHint.toLowerCase().contains("celebrat")) {
                    categoryName = "Celebration";
                }
            }

            if (!categoryName.equals("Uncategorized")) {
                Category celebrationCategory = categorizationService.getOrCreateCategoryEntity(categoryName, transaction.getType().name());
                transaction.setCategory(celebrationCategory);
            }
            
            // Store the RRN or IFSC if we have fields (optional, but good for tracking)
            if (transaction.getAiReasoning() == null) {
                transaction.setAiReasoning("Structural parse from: " + candidate);
            }
            
            return true;
        }
        return false;
    }
}
