Goal:
Implement a robust Reconciliation Engine to match bank statement transactions with receipt transactions using intelligent matching, with a modular multi‑strategy matching pipeline and extended status model.

---

Context:
- Receipts are parsed weekly via OCR (local + Gemini hybrid)
- Bank statements are parsed monthly
- User manually triggers reconciliation
- System is local-first (Spring Boot + SQLite)
- Matching must be accurate, scalable, and idempotent

---

STATUS MODEL:

reconciliation_status:
  PENDING (default)
  MATCHED
  UNMATCHED
  NO_RECEIPT_REQUIRED
  MANUAL_REVIEW
  DISPUTED

- HIGH confidence matches → MATCHED
- LOW confidence / missing receipts → MANUAL_REVIEW
- Explicitly flagged as no receipt needed → NO_RECEIPT_REQUIRED
- Unmatched after exhaustive search → UNMATCHED
- User‑flagged disputes → DISPUTED

---

Internal Fields (DO NOT expose to UI):

match_type:
  EXACT
  DATE_TOLERANCE
  VENDOR_FUZZY
  AMOUNT_TOLERANCE
  ONE_TO_MANY
  MANY_TO_ONE
  NO_MATCH
  LOW_CONFIDENCE

match_score: (0–100)

is_manual_override: boolean

vendor_alias: (learned vendor mapping)

category: (expense category)

sub_category: (optional)

---

Matching Strategy:

1. Preprocessing (MANDATORY)
- Normalize vendor (VendorNormalizationService)
  - uppercase, remove special chars, trim spaces
  - apply learned vendor aliases (VendorAlias)
- Index transactions by amount and date
- Skip already MATCHED records (idempotent run)

---

2. Candidate Selection
- For each bank debit transaction:
  - Fetch receipt candidates with:
    amount == OR within tolerance (±1%) (AmountMatchingService)

---

3. Multi‑Strategy Matching Pipeline

A. Vendor + Amount Match (HIGH confidence)
- amount exact (± tolerance) AND vendor similarity ≥ 90%
- Uses VendorNormalizationService + AmountMatchingService
- → reconciliation_status = MATCHED
- → match_score ≥ 90

B. Amount + Date Match (MEDIUM confidence)
- amount exact (± tolerance) AND date within ±3 days
- Uses AmountMatchingService + DateMatchingService
- → reconciliation_status = MATCHED (if score ≥ 90) else MANUAL_REVIEW
- → match_score 70–89

C. Fuzzy Match (LOW confidence)
- amount within tolerance AND vendor similarity ≥ 70%
- Uses ConfidenceCalculator with weighted scoring
- → reconciliation_status = MANUAL_REVIEW
- → match_score 50–69

D. No Match
- No candidate meets minimum thresholds
- → reconciliation_status = UNMATCHED
- → match_score < 50

---

4. Vendor Matching
- Use fuzzy matching (Levenshtein or Jaro‑Winkler)
- Use threshold‑based scoring
- Dynamic vendor‑alias learning (VendorAlias entity)

---

5. Date Tolerance
- Allow ±3 days (configurable)
- DateMatchingService computes proximity score

---

6. Amount Tolerance
- Default exact match
- Configurable tolerance (±1% or absolute)
- AmountMatchingService handles banking fee detection

---

Advanced Matching (CRITICAL):

7. One‑to‑Many Matching
- One bank transaction = sum of multiple receipts
- → mark as MANUAL_REVIEW unless exact confidence

8. Many‑to‑One Matching
- Multiple bank entries = one receipt
- → MANUAL_REVIEW

9. Duplicate Prevention
- One receipt cannot match multiple bank entries (unless manual override)

---

Bidirectional Matching:

10. Perform BOTH:
- Bank → Receipt
- Receipt → Bank

---

Exception Handling (MANDATORY):

All these MUST go to MANUAL_REVIEW:

- Missing receipt
- Missing bank entry
- OCR errors
- Duplicate transactions
- Partial payments
- Combined payments
- Date mismatch beyond tolerance
- Vendor mismatch
- Bank charges / fees (no receipt)
- Incorrect amount

---

Manual Reconciliation Flow:

1. UI must show:
- Unmatched bank transactions
- Unmatched receipts

2. For each item, allow user to:
- Edit:
  - date
  - vendor
  - amount

3. Provide actions:
- "Match manually"
- "Mark as No Receipt Required"
- "Upload / Link Receipt (OneDrive)"

4. Re‑run reconciliation:
- Only for pending MANUAL_REVIEW records

---

Data Model Updates:

Add fields:

- reconciliation_status
- match_score
- match_type
- matched_transaction_ids (JSON)
- is_manual_override
- audit_log
- vendor_alias
- category
- sub_category

---

Audit Trail (MANDATORY):

- Store every reconciliation run (ReconciliationRun entity)
- Store:
  - matched pairs
  - manual overrides
  - unmatched records
- Vendor‑alias learning logs

---

Performance:

- Use indexing on amount and date
- Avoid N² comparisons (group by amount first)
- Ensure reconciliation is idempotent

---

Validation Criteria:

- ≥85% auto‑match rate
- No duplicate matching
- Stable results across re‑runs

---

UI Requirements:

- Show only:
  MATCHED (green)
  NEEDS REVIEW (red)
  NO RECEIPT REQUIRED (gray)
  UNMATCHED (orange)

- Sort MANUAL_REVIEW by match_score descending

- DO NOT expose internal match_type

---

Non‑Functional:

- Do not break existing parsing logic
- Maintain backward compatibility
- Use transactional integrity

---

Future Enhancement (Optional):

- Learning engine:
  - store manual corrections
  - improve vendor matching automatically

---

Expected Outcome:

- High accuracy reconciliation
- Minimal manual effort
- Clean UX (extended status model)
- Scalable and production‑ready engine