Goal:
Implement a robust Reconciliation Engine to match bank statement transactions with receipt transactions using intelligent matching, with a simplified 2-state outcome model.

---

Context:
- Receipts are parsed weekly via OCR (local + Gemini hybrid)
- Bank statements are parsed monthly
- User manually triggers reconciliation
- System is local-first (Spring Boot + SQLite)
- Matching must be accurate, scalable, and idempotent

---

FINAL STATUS MODEL (MANDATORY):

reconciliation_status:
  MATCHED
  MANUAL_REVIEW

- Only HIGH confidence matches → MATCHED
- All other cases → MANUAL_REVIEW

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

---

Matching Strategy:

1. Preprocessing (MANDATORY)
- Normalize vendor:
  - uppercase
  - remove special chars
  - trim spaces
- Index transactions by amount
- Ignore already MATCHED records (idempotent run)

---

2. Candidate Selection
- For each bank debit transaction:
  - Fetch receipt candidates with:
    amount == OR within tolerance (±1%)

---

3. Matching Rules

A. Strong Match (HIGH confidence)
- amount == exact
AND
(
   date within ±3 days
   OR vendor similarity ≥ 90%
)

→ reconciliation_status = MATCHED
→ match_score ≥ 90

---

B. Medium Match
- amount == exact
AND vendor similarity ≥ 80%

→ reconciliation_status = MANUAL_REVIEW
→ match_score 70–89

---

C. Weak Match
- amount within tolerance
AND vendor similarity ≥ 70%

→ reconciliation_status = MANUAL_REVIEW
→ match_score 50–69

---

D. No Match
→ reconciliation_status = MANUAL_REVIEW
→ match_score < 50

---

4. Vendor Matching
- Use fuzzy matching (Levenshtein or Jaro-Winkler)
- Use threshold-based scoring

---

5. Date Tolerance
- Allow ±3 days

---

6. Amount Tolerance
- Default exact match
- Configurable tolerance (±1 or %)

---

Advanced Matching (CRITICAL):

7. One-to-Many Matching
- One bank transaction = sum of multiple receipts
→ mark as MANUAL_REVIEW unless exact confidence

---

8. Many-to-One Matching
- Multiple bank entries = one receipt
→ MANUAL_REVIEW

---

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

4. Re-run reconciliation:
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

---

Audit Trail (MANDATORY):

- Store every reconciliation run
- Store:
  - matched pairs
  - manual overrides
  - unmatched records

---

Performance:

- Use indexing on amount and date
- Avoid N² comparisons (group by amount first)
- Ensure reconciliation is idempotent

---

Validation Criteria:

- ≥85% auto-match rate
- No duplicate matching
- Stable results across re-runs

---

UI Requirements:

- Show only:
  MATCHED (green)
  NEEDS REVIEW (red)

- Sort MANUAL_REVIEW by match_score descending

- DO NOT expose internal match_type

---

Non-Functional:

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
- Clean UX (2-state model)
- Scalable and production-ready engine

                ┌──────────────────────────────┐
                │   User Triggers Reconcile    │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Load Data                    │
                │ - Bank Transactions (Month)  │
                │ - Receipt Transactions       │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Preprocessing                │
                │ - Normalize vendor           │
                │ - Index by amount            │
                │ - Skip already MATCHED       │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Candidate Selection          │
                │ - Find receipts by amount    │
                │   (exact or ± tolerance)     │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Matching Engine              │
                │                              │
                │ Check:                       │
                │ 1. Amount match              │
                │ 2. Date tolerance (±3d)      │
                │ 3. Vendor similarity         │
                │                              │
                └───────┬─────────┬────────────┘
                        │         │
        ┌───────────────┘         └───────────────┐
        ▼                                         ▼
┌──────────────────────┐               ┌────────────────────────┐
│ HIGH CONFIDENCE      │               │ LOW / NO CONFIDENCE     │
│ (Strong match)       │               │                        │
│                      │               │                        │
│ amount == AND        │               │ anything else          │
│ (date OR vendor)     │               │                        │
└────────────┬─────────┘               └────────────┬───────────┘
             │                                      │
             ▼                                      ▼
┌──────────────────────┐               ┌────────────────────────┐
│ Mark as MATCHED      │               │ Mark as MANUAL_REVIEW  │
│ match_score ≥ 90     │               │ match_score < 90       │
└────────────┬─────────┘               └────────────┬───────────┘
             │                                      │
             └──────────────┬───────────────────────┘
                            ▼
                ┌──────────────────────────────┐
                │ Advanced Matching Check      │
                │ - One-to-many               │
                │ - Many-to-one               │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Update Results               │
                │ - status                    │
                │ - match_score               │
                │ - matched_ids               │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Audit Logging                │
                │ - matched pairs              │
                │ - unmatched items            │
                │ - overrides                  │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ UI Layer                     │
                │                              │
                │ Show:                        │
                │ ✅ MATCHED                   │
                │ ⚠️ MANUAL_REVIEW            │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Manual Actions               │
                │ - Edit (date/vendor/amount)  │
                │ - Upload receipt             │
                │ - Match manually             │
                │ - Mark no receipt needed     │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ Re-run Reconciliation        │
                │ (ONLY pending records)       │
                └──────────────────────────────┘