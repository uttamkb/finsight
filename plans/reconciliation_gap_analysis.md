# FinSight Reconciliation Engine - Gap Analysis

## Executive Summary
This document provides a comprehensive gap analysis between the currently implemented FinSight reconciliation system and the proposed "robust multi-strategy reconciliation engine." The current system provides a solid foundational monolithic matching loop (via `ReconciliationServiceImpl`) with basic fuzzy matching, keyword-based categorization, and an established audit trail. However, to meet the target robustness, the system needs to evolve into a modular, multi-strategy engine with deeper intelligence, dynamic vendor learning, and category-specific validation heuristics.

---

## 1. Current Reconciliation Logic
- **How transactions match:** The system uses a single-pass loop over `PENDING` or `MANUAL_REVIEW` bank transactions. It filters for `DEBIT` transactions and queries for receipts within a +/- 1% amount tolerance window.
- **Matching criteria:** A single `computeScore` method calculates a score out of 110:
  - Amount: Exact match (+50), within 1% (+40).
  - Date: Exact match (+30), within 3 days (+20), within 7 days (+10).
  - Vendor: Levenshtein distance similarity on cleaned strings (+20).
- **Confidence/Accuracy:** Matches automatically if the total score is `>= 90`. It creates an `AuditTrail` entry (`SUGGESTED_MATCH`) if the score is below 90 but the highest among candidates.

**Gap:** The logic is tightly coupled in one class. It lacks modular multi-strategy approaches (e.g., Vendor+Amount strategy vs. Amount+Date strategy) and specialized components like `AmountMatchingService` or `DateMatchingService`.

---

## 2. Vendor Handling
- **Normalization:** `NormalizationUtils` provides basic alphanumeric stripping and uppercase conversion. `ReconciliationServiceImpl.normalize()` also strips non-alphanumeric characters.
- **Existing Mappings:** A `VendorDictionary` entity exists, populated via `VendorDictionaryService.addVendor`, but it functions more as an autocomplete list rather than a dynamic alias mapping system.
- **Processing:** `BankTransactionCategorizationService` attempts to extract or clean the vendor name using hardcoded keywords, falling back to Gemini AI classification if keywords fail.

**Gap:** True dynamic learning (`VendorAlias` entity) where manual approvals teach the system that "SRR BLR" equals "SRR Supermarket" is missing. A dedicated `VendorNormalizationService` is needed.

---

## 3. Category Management
- **Implementation:** Categories exist via the `Category` entity (supporting parent-child hierarchy).
- **Categorization:** Handled by `BankTransactionCategorizationService`. It uses a hardcoded static `RULES` map (e.g., "bescom" -> "Electricity"). If no rule matches, it falls back to Gemini.
- **Category-specific rules:** Currently **none**. The matching algorithm applies the exact same logic (e.g., 1% tolerance) to a utility bill as it does to a maintenance repair.

**Gap:** Need to migrate hardcoded keyword rules to database-backed configurations. Missing intelligence to apply category-specific heuristics (e.g., utility bills matched differently than food, or amount validation by expense category).

---

## 4. Exception Handling
- **Unmatched Transactions:** Handled effectively. Unmatched transactions fall into `MANUAL_REVIEW` status.
- **Manual Review Process:** Fully implemented. The `AuditTrail` table tracks issues like `SUGGESTED_MATCH` or `BANK_NO_RECEIPT`. The frontend (`/reconciliation` page) allows users to manually approve suggested matches, manually link specific IDs, or mark a transaction as "No Receipt Required".
- **Resolution:** Resolving an anomaly automatically updates the `AuditTrail` to `resolved = true` and updates the `BankTransaction` and `Receipt` statuses.

**Gap:** The exception handling flow is solid. The primary gap is that resolving an exception manually does not currently trigger a feedback loop to train the vendor alias system.

---

## 5. Reporting Capabilities
- **Reconciliation Reports:** High-level stats exist via `DashboardService` and `ReconciliationController.getAuditStatistics()`.
- **Audit Trail Detail:** The `AuditTrail` entity logs the exact score, the `issueType`, and the `matchType`.
- **Analytics/Insights:** `InsightsController` provides top vendors, category spending, and anomaly history.

**Gap:** The frontend visualizes the total score, but lacks a breakdown visualization of *why* the score was generated (e.g., "Amount: 50/50, Date: 20/30, Vendor: 10/20"). A `ReconciliationResult` entity dedicated to granular confidence scoring is missing.

---

## Implementation Roadmap

### Phase 1: Engine Modularization (Weeks 1-2)
*Goal: Decouple the monolithic logic without changing outward behavior.*
1. Create `AmountMatchingService`, `DateMatchingService`, and `VendorNormalizationService`.
2. Extract the scoring logic from `ReconciliationServiceImpl` into a `ConfidenceCalculator` component.
3. Establish the multi-strategy framework (Vendor+Amount, Amount+Date) injected into the main service.

### Phase 2: Dynamic Intelligence & Aliasing (Weeks 3-4)
*Goal: Introduce learning and remove hardcoded logic.*
1. Create `VendorAlias` entity and repository.
2. Update the `ReconciliationController`'s manual link/resolve endpoints to feed data into the `VendorAlias` table.
3. Migrate the static `RULES` map in `BankTransactionCategorizationService` to a configurable database table (e.g., `CategoryKeywordMapping`).

### Phase 3: Category-Specific Heuristics (Week 5)
*Goal: Apply different matching rules based on financial context.*
1. Introduce category-specific tolerances (e.g., allow 5% variance for generic repairs, but only 0% for fixed EMI payments).
2. Implement frequency-based anomaly detection (e.g., flagging duplicate electricity payments in the same month).

### Phase 4: UI & Reporting Enhancements (Week 6)
*Goal: Improve transparency.*
1. Update `AuditTrailDto` and frontend components to display the granular breakdown of confidence scores.
2. Add dashboard panels for Vendor Alias insights.

---

## Resource Requirements & Timeline
- **Backend Engineer (Java/Spring Boot)**: 1 Resource (Full Time, 6 Weeks)
- **Frontend Engineer (React/Next.js)**: 1 Resource (Part Time, 2 Weeks for Phase 4)
- **Total Timeline**: 6 Weeks.

## Risk Mitigation
- **Risk:** Modularizing the engine could temporarily break the existing auto-match accuracy.
  - **Mitigation:** Write extensive unit tests for the new modular services (`AmountMatchingService`, `DateMatchingService`) against the existing logic baseline *before* swapping them into the main service.
- **Risk:** Incorrect learned vendor aliases causing false positive auto-matches.
  - **Mitigation:** Implement an "approval count" threshold for aliases. A manual link must be performed 2-3 times for a specific alias before it is trusted by the auto-reconciliation engine.
