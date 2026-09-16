# POS0210 Financial Ledger V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the primary monthly finance result with cash-based `revenue - expenses`, keep payer debt separate, track initial/additional investment and depreciation separately, and add reusable cost-code analytics without touching unrelated POS flows.

**Architecture:** Extend the existing purchase/accounting model additively. Keep `PurchaseEntity` as the source for expense documents, `FinancialMovementEntity` for non-expense cash/balance movements, add `CostCodeEntity` and purchase linkage, and centralize pure calculations in `FinancialLedgerV2.kt`. UI changes are limited to purchase entry/detail/edit and financial reports.

**Tech Stack:** Kotlin, Jetpack Compose, Room, JUnit, existing POS0210 GitHub Actions/Gradle build.

**Spec:** GitHub issue #10.

## Global Constraints
- Base commit: `9ac43dbbcea016fa7dcbf4f88c07487d56140a22` (candidate15 / versionCode 76).
- No destructive Room migration.
- Do not modify printer, order, table, QR/payment, bank notification, customer, menu, media or unrelated UI behavior.
- Depreciation is analytic only and never reduces distributable cash a second time.
- Reimbursement reduces payer debt but is not a second expense.
- Additional investment is a current-period cash outflow but reported separately from operating expense.

### Task 1: Pure financial calculations
- [ ] Add failing tests for cash result, additional investment split, depreciation exclusion, payback, payer outstanding and cost variance.
- [ ] Run targeted unit tests and confirm RED.
- [ ] Add `FinancialLedgerV2.kt` with pure calculation functions.
- [ ] Re-run targeted tests and confirm GREEN.

### Task 2: Additive finance data model
- [ ] Add `CostCodeEntity`, `PurchaseEntity.costCodeId`, `PurchaseEntity.updatedAt`, and payer reimbursement movement semantics.
- [ ] Add Room migration 18→19 and DAO queries/upserts.
- [ ] Preserve candidate15 payer data and all existing records.

### Task 3: Purchase entry/edit and cost codes
- [ ] Allow selecting/creating reusable cost codes; keep manual uncoded entry available.
- [ ] Persist quantity/unit/unit price already present with the selected cost code.
- [ ] Add edit flow for classification, cost code, payer, supplier, quantity/unit price, note and date.
- [ ] Preserve creator; update `updatedAt`; write UPDATE audit.

### Task 4: Payer debt and reimbursement
- [ ] Derive outstanding by payer from advanced expense documents minus reimbursement movements.
- [ ] Add reimbursement entry that never creates a duplicate expense.
- [ ] Show advanced/reimbursed/outstanding totals in finance report.

### Task 5: Reports
- [ ] Primary month result = revenue - all period expense documents.
- [ ] Show operating result before additional investment and net cash result after it.
- [ ] Show depreciation as reference only, initial investment/payback progress, additional investment totals.
- [ ] Add cost-code filter/history with quantity and unit-price variance.

### Task 6: Compatibility and build
- [ ] Update backup/cloud mapping only for new finance fields already in synced entities.
- [ ] Bump exactly once to candidate16 / versionCode 77.
- [ ] Run `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.
- [ ] Verify package/version/signature/SHA-256 and upload APK artifact.
- [ ] Scope-guard changed files and commit only validated finance-related source/test files.
