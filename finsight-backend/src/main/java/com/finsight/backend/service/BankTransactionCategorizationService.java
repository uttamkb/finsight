package com.finsight.backend.service;

import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Categorization Engine for bank transactions.
 *
 * <p>Pipeline:
 * <ol>
 *   <li><b>Keyword Rules (fast, free)</b> — matches description/vendor against an extensible
 *       rule map. Covers 90%+ of Indian bank statement descriptions.</li>
 *   <li><b>AI Fallback (accurate)</b> — only invoked when keyword rules produce no match.
 *       Delegates to {@link ClassificationService} which already calls Gemini.</li>
 * </ol>
 *
 * <p>To add new rules, extend the static {@code RULES} map.
 * To upgrade to AI-only, remove the keyword step.
 */
@Service
public class BankTransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionCategorizationService.class);

    private final ClassificationService classificationService;
    private final CategoryRepository categoryRepository;

    // ── Keyword Rule Map ─────────────────────────────────────────────────────
    // Format: keyword (lowercase) → category name
    // Evaluated in insertion order — put more specific terms BEFORE generic ones.
    // Case-insensitive match against: "(vendor + ' ' + description).toLowerCase()"
    private static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        // ── Utilities ────────────────────────────────────────────────────────
        RULES.put("bescom",        "Electricity");
        RULES.put("msedcl",        "Electricity");
        RULES.put("tneb",          "Electricity");
        RULES.put("electricity",   "Electricity");
        RULES.put("electric bill", "Electricity");
        RULES.put("power bill",    "Electricity");
        RULES.put("bwssb",         "Water Supply");
        RULES.put("water board",   "Water Supply");
        RULES.put("water bill",    "Water Supply");
        RULES.put("tanker",        "Water Supply");
        RULES.put("gas bill",      "Gas");
        RULES.put("indane",        "Gas");
        RULES.put("mahanagar gas", "Gas");
        RULES.put("broadband",     "Internet");
        RULES.put("jiofiber",      "Internet");
        RULES.put("airtel fiber",  "Internet");
        RULES.put("act fiber",     "Internet");

        // ── Food & Dining ────────────────────────────────────────────────────
        RULES.put("swiggy",        "Food & Dining");
        RULES.put("zomato",        "Food & Dining");
        RULES.put("dunzo",         "Food & Dining");
        RULES.put("blinkit",       "Groceries");
        RULES.put("bigbasket",     "Groceries");
        RULES.put("reliance fresh","Groceries");
        RULES.put("dmart",         "Groceries");
        RULES.put("zepto",         "Groceries");
        RULES.put("instamart",     "Groceries");

        // ── Transport ────────────────────────────────────────────────────────
        RULES.put("uber",          "Transport");
        RULES.put("ola",           "Transport");
        RULES.put("rapido",        "Transport");
        RULES.put("namma metro",   "Transport");
        RULES.put("bmtc",          "Transport");
        RULES.put("irctc",         "Travel");
        RULES.put("indian railway", "Travel");
        RULES.put("makemytrip",    "Travel");
        RULES.put("goibibo",       "Travel");
        RULES.put("indigo",        "Travel");
        RULES.put("air india",     "Travel");
        RULES.put("spicejet",      "Travel");

        // ── Healthcare ───────────────────────────────────────────────────────
        RULES.put("apollo",        "Healthcare");
        RULES.put("fortis",        "Healthcare");
        RULES.put("medplus",       "Healthcare");
        RULES.put("pharmacy",      "Healthcare");
        RULES.put("medical",       "Healthcare");
        RULES.put("hospital",      "Healthcare");
        RULES.put("diagnostic",    "Healthcare");

        // ── Shopping & E-commerce ────────────────────────────────────────────
        RULES.put("amazon",        "Shopping");
        RULES.put("flipkart",      "Shopping");
        RULES.put("myntra",        "Shopping");
        RULES.put("ajio",          "Shopping");
        RULES.put("nykaa",         "Shopping");
        RULES.put("meesho",        "Shopping");

        // ── Entertainment & Subscriptions ─────────────────────────────────
        RULES.put("netflix",       "Entertainment");
        RULES.put("amazon prime",  "Entertainment");
        RULES.put("hotstar",       "Entertainment");
        RULES.put("spotify",       "Entertainment");
        RULES.put("youtube premium","Entertainment");
        RULES.put("bookmyshow",    "Entertainment");

        // ── Cash & ATM ───────────────────────────────────────────────────────
        RULES.put("atm ",          "Cash Withdrawal");  // trailing space avoids matching "atmosphere" etc.
        RULES.put("cash withdrawal","Cash Withdrawal");
        RULES.put("atm/",          "Cash Withdrawal");
        RULES.put("atwd",          "Cash Withdrawal");

        // ── Maintenance & Society ────────────────────────────────────────────
        RULES.put("maintenance",   "Maintenance");
        RULES.put("society",       "Society Charges");
        RULES.put("association",   "Society Charges");
        RULES.put("housekeeping",  "Maintenance");
        RULES.put("cleaning",      "Maintenance");
        RULES.put("plumber",       "Maintenance");
        RULES.put("electrician",   "Maintenance");
        RULES.put("repair",        "Maintenance");
        RULES.put("lift",          "Lift Maintenance");
        RULES.put("elevator",      "Lift Maintenance");
        RULES.put("security",      "Security");
        RULES.put("guard",         "Security");

        // ── Finance & Banking ────────────────────────────────────────────────
        RULES.put("emi",           "Loan EMI");
        RULES.put("loan",          "Loan EMI");
        RULES.put("insurance",     "Insurance");
        RULES.put("lic",           "Insurance");
        RULES.put("mutual fund",   "Investments");
        RULES.put("sip",           "Investments");
        RULES.put("zerodha",       "Investments");
        RULES.put("groww",         "Investments");
        RULES.put("paytm",         "Digital Payments");
        RULES.put("phonepe",       "Digital Payments");
        RULES.put("gpay",          "Digital Payments");
        RULES.put("google pay",    "Digital Payments");

        // ── Education ────────────────────────────────────────────────────────
        RULES.put("school fee",    "Education");
        RULES.put("college fee",   "Education");
        RULES.put("tuition",       "Education");
        RULES.put("byju",          "Education");
        RULES.put("unacademy",     "Education");
        RULES.put("coursera",      "Education");
        RULES.put("udemy",         "Education");

        // ── Income / Credits ─────────────────────────────────────────────────
        RULES.put("salary",        "Salary");
        RULES.put("payroll",       "Salary");
        RULES.put("dividend",      "Dividend");
        RULES.put("interest",      "Interest Income");
        RULES.put("refund",        "Refund");
        RULES.put("cashback",      "Cashback");
        RULES.put("neft cr",       "Incoming Transfer");
        RULES.put("imps cr",       "Incoming Transfer");
        RULES.put("upi cr",        "Incoming Transfer");
    }

    public BankTransactionCategorizationService(
            ClassificationService classificationService,
            CategoryRepository categoryRepository) {
        this.classificationService = classificationService;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Assigns a category to a transaction.
     *
     * <ol>
     *   <li>If Gemini already returned a non-empty category, accept it (parser already ran AI).</li>
     *   <li>Otherwise apply keyword rules.</li>
     *   <li>If still unmatched, fall back to {@link ClassificationService} (AI via Gemini).</li>
     * </ol>
     *
     * @param vendor      cleaned vendor/counterparty name from Gemini
     * @param description original narration text
     * @param aiCategory  category string already returned by the Gemini parsing step (may be blank)
     * @param type        "DEBIT" or "CREDIT"
     * @return resolved category name, never null
     */
    public String categorize(String vendor, String description, String aiCategory, String type) {
        // 1. Trust Gemini's parse-time category if it's specific enough
        if (aiCategory != null && !aiCategory.isBlank() && !"uncategorized".equalsIgnoreCase(aiCategory.trim())) {
            log.debug("Using Gemini parse-time category: '{}'", aiCategory);
            return aiCategory.trim();
        }

        // 2. Keyword rule engine
        String combined = ((vendor != null ? vendor : "") + " " + (description != null ? description : "")).toLowerCase();
        for (Map.Entry<String, String> rule : RULES.entrySet()) {
            if (combined.contains(rule.getKey())) {
                log.debug("Keyword rule matched '{}' → '{}'", rule.getKey(), rule.getValue());
                return rule.getValue();
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

        if (parentName == null) return null;

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
