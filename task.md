# Task List — Workflow Decoupling & Production Hardening

## P0 — Critical (In Progress)
- [x] Move `/reconcile` endpoint from `BankStatementController` to `ReconciliationController`
  - [x] Remove `ReconciliationService` injection from `BankStatementController`
  - [x] Update frontend `StatementsPage.tsx` API call to `/api/v1/reconciliation/run`
- [x] Fix tenant isolation bug in `BankStatementService.getRecentUploads()`
  - [x] Add `findByTenantIdOrderByCreatedAtDesc()` to `StatementUploadRepository`
  - [x] Replace `findAll()` call

## P1 — High Priority (In Progress)
- [x] Expand `ReconciliationStatus` enum
  - [x] Add: `PENDING`, `UNMATCHED`, `NO_RECEIPT_REQUIRED`, `DISPUTED`
  - [x] Update `markAsNoReceiptNeeded()` to use `NO_RECEIPT_REQUIRED`
  - [x] Migrate existing defaults from `MANUAL_REVIEW` → `PENDING`
- [x] Add `ReconciliationRun` entity for run history
  - [x] Create entity with `runId`, `tenantId`, `accountType`, timestamps, counts, status
  - [x] Create `ReconciliationRunRepository`
  - [x] Update `ReconciliationServiceImpl.runReconciliation()` to create a run record
  - [x] Add `GET /reconciliation/runs` API endpoint

## P2 — Medium Priority
- [x] Add `ReceiptSyncRun` entity for persistent sync tracking
  - [x] Create entity mirroring `StatementUpload` lifecycle pattern
  - [x] Update `DriveSyncServiceImpl` to persist run records
- [x] Deprecate `BankTransaction.reconciled` boolean
  - [x] Replace all queries using `reconciled` with `reconciliationStatus`
  - [x] Add data migration script to sync existing rows

## P3 — Low Priority / Cleanup
- [x] Replace raw `new Thread()` with `@Async` in `ReceiptServiceImpl.updateReceipt()`
- [x] Add concurrency guard to `ReconciliationServiceImpl.runReconciliation()`
