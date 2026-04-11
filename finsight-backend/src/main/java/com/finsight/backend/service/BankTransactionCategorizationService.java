package com.finsight.backend.service;

import com.finsight.backend.entity.Category;
import com.finsight.backend.entity.CategoryKeywordMapping;
import com.finsight.backend.repository.CategoryKeywordMappingRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Categorization Engine for bank transactions.
 *
 * <p>
 * Pipeline:
 * <ol>
 * <li><b>Keyword Rules (fast, free)</b> — matches description/vendor against a
 * database-backed
 * rule map.</li>
 * <li><b>AI Fallback (accurate)</b> — only invoked when keyword rules produce
 * no match.
 * Delegates to {@link ClassificationService} which already calls Gemini.</li>
 * </ol>
 */
@Service
public class BankTransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionCategorizationService.class);

    private final ClassificationService classificationService;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordMappingRepository categoryKeywordMappingRepository;

    public BankTransactionCategorizationService(
            ClassificationService classificationService,
            CategoryRepository categoryRepository,
            CategoryKeywordMappingRepository categoryKeywordMappingRepository) {
        this.classificationService = classificationService;
        this.categoryRepository = categoryRepository;
        this.categoryKeywordMappingRepository = categoryKeywordMappingRepository;
    }

    /**
     * Assigns a category to a transaction.
     *
     * <ol>
     * <li>If Gemini already returned a non-empty category, accept it (parser
     * already ran AI).</li>
     * <li>Otherwise apply keyword rules.</li>
     * <li>If still unmatched, fall back to {@link ClassificationService} (AI via
     * Gemini).</li>
     * </ol>
     *
     * @param vendor      cleaned vendor/counterparty name from Gemini
     * @param description original narration text
     * @param aiCategory  category string already returned by the Gemini parsing
     *                    step (may be blank)
     * @param type        "DEBIT" or "CREDIT"
     * @param tenantId    current tenant id
     * @return resolved category name, never null
     */
    public String categorize(String vendor, String description, String aiCategory, String type, String tenantId) {
        // 1. Trust Gemini's parse-time category if it's specific enough
        if (aiCategory != null && !aiCategory.isBlank() && !"uncategorized".equalsIgnoreCase(aiCategory.trim())) {
            log.debug("Using Gemini parse-time category: '{}'", aiCategory);
            return aiCategory.trim();
        }

        // 2. Keyword rule engine (Database-backed)
        String combined = ((vendor != null ? vendor : "") + " " + (description != null ? description : ""))
                .toLowerCase();
        List<CategoryKeywordMapping> rules = categoryKeywordMappingRepository
                .findByTenantIdOrderByPriorityDesc(tenantId);

        for (CategoryKeywordMapping rule : rules) {
            if (combined.contains(rule.getKeyword().toLowerCase())) {
                log.debug("Keyword rule matched '{}' → '{}'", rule.getKeyword(), rule.getCategory().getName());
                return rule.getCategory().getName();
            }
        }

        // 3. AI classification fallback via ClassificationService (Gemini)
        try {
            String aiResult = classificationService.classify(description, vendor);
            if (aiResult != null && !aiResult.isBlank()) {
                log.debug("AI classification returned: '{}'", aiResult);
                return aiResult;
            }
        } catch (Exception e) {
            log.warn("AI fallback classification failed for '{}': {}", vendor, e.getMessage());
        }

        // 4. Default
        return "CREDIT".equalsIgnoreCase(type) ? "Incoming Transfer" : "Uncategorized";
    }

    // Keep legacy overloaded method for backward compatibility
    public String categorize(String vendor, String description, String aiCategory, String type) {
        return categorize(vendor, description, aiCategory, type, "local_tenant");
    }

    /**
     * Ensures the named category exists in the DB (creates if not found).
     * If the category is a known sub-category, also creates/links its parent.
     */
    public Category getOrCreateCategoryEntity(String name, String txType) {
        Category.CategoryType catType = "CREDIT".equalsIgnoreCase(txType)
                ? Category.CategoryType.INCOME
                : Category.CategoryType.EXPENSE;

        return categoryRepository.findByNameAndTenantId(name, "local_tenant")
                .orElseGet(() -> {
                    Category parent = resolveParent(name, catType);
                    Category c = new Category();
                    c.setName(name);
                    c.setType(catType);
                    c.setParentCategory(parent);
                    return categoryRepository.save(c);
                });
    }

    /**
     * Maps leaf categories to their logical parents. Extend as needed.
     * Returns null for top-level categories.
     */
    private Category resolveParent(String name, Category.CategoryType catType) {
        String parentName = switch (name) {
            case "Electricity", "Water Supply", "Gas", "Internet" -> "Utilities";
            case "Food & Dining", "Groceries" -> "Food";
            case "Transport", "Travel" -> "Transportation";
            case "Healthcare" -> "Health & Wellness";
            case "Shopping" -> "Shopping & E-commerce";
            case "Entertainment", "Subscriptions" -> "Leisure";
            case "Maintenance", "Lift Maintenance", "Security", "Society Charges" -> "Building & Maintenance";
            case "Loan EMI", "Insurance", "Investments" -> "Finance";
            case "Salary", "Dividend", "Interest Income" -> "Income";
            default -> null;
        };

        if (parentName == null)
            return null;

        return categoryRepository.findByNameAndTenantId(parentName, "local_tenant")
                .orElseGet(() -> {
                    Category parent = new Category();
                    parent.setName(parentName);
                    parent.setType(catType);
                    parent.setParentCategory(null); // top-level
                    return categoryRepository.save(parent);
                });
    }
}
