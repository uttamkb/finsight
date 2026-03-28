# Task List — Workflow Decoupling & Production Hardening

## P0 — Critical (In Progress)
- [/] Move `/reconcile` endpoint from `BankStatementController` to `ReconciliationController`
  - [ ] Remove `ReconciliationService` injection from `BankStatementController`
  - [ ] Update frontend `StatementsPage.tsx` API call to `/api/v1/reconciliation/run`
- [/] Fix tenant isolation bug in `BankStatementService.getRecentUploads()`
  - [ ] Add `findByTenantIdOrderByCreatedAtDesc()` to `StatementUploadRepository`
  - [ ] Replace `findAll()` call

## P1 — High Priority (In Progress)
- [/] Expand `ReconciliationStatus` enum
  - [ ] Add: `PENDING`, `UNMATCHED`, `NO_RECEIPT_REQUIRED`, `DISPUTED`
  - [ ] Update `markAsNoReceiptNeeded()` to use `NO_RECEIPT_REQUIRED`
  - [ ] Migrate existing defaults from `MANUAL_REVIEW` → `PENDING`
- [/] Add `ReconciliationRun` entity for run history
  - [ ] Create entity with `runId`, `tenantId`, `accountType`, timestamps, counts, status
  - [ ] Create `ReconciliationRunRepository`
  - [ ] Update `ReconciliationServiceImpl.runReconciliation()` to create a run record
  - [ ] Add `GET /reconciliation/runs` API endpoint

## P2 — Medium Priority
- [ ] Add `ReceiptSyncRun` entity for persistent sync tracking
  - [ ] Create entity mirroring `StatementUpload` lifecycle pattern
  - [ ] Update `DriveSyncServiceImpl` to persist run records
- [ ] Deprecate `BankTransaction.reconciled` boolean
  - [ ] Replace all queries using `reconciled` with `reconciliationStatus`
  - [ ] Add data migration script to sync existing rows

## P3 — Low Priority / Cleanup
- [ ] Replace raw `new Thread()` with `@Async` in `ReceiptServiceImpl.updateReceipt()`
- [ ] Add concurrency guard to `ReconciliationServiceImpl.runReconciliation()`
