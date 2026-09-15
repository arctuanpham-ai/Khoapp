# POS0210 Independent QA Checklist

## A. Artifact identity
- [ ] APK filename matches intended candidate version
- [ ] packageName/applicationId is `vn.ecohome.pos0210`
- [ ] versionName matches release plan
- [ ] versionCode is greater than previous accepted build
- [ ] SHA-256 recorded
- [ ] signing certificate matches previous accepted build

## B. Android compatibility
- [ ] minSdk documented
- [ ] targetSdk documented
- [ ] supported ABIs documented
- [ ] install/update behavior verified on representative Android versions
- [ ] any unsupported Android version is explicitly documented to the operator

## C. DATA SURVIVAL GATE — P0
Prepare previous accepted build with representative data, then install candidate over it.

Verify before/after equality or expected migration for:
- [ ] tables and table states
- [ ] active orders
- [ ] kitchen batches
- [ ] delivered/pending-delivery state
- [ ] payment records
- [ ] history
- [ ] revenue totals
- [ ] customers
- [ ] points/member/VIP/VVIP state
- [ ] menu groups/items/prices/status/descriptions
- [ ] menu images
- [ ] employees/PINs/roles/permissions
- [ ] bank account and QR settings
- [ ] printer settings
- [ ] stock/import history
- [ ] invoice images
- [ ] SharedPreferences/DataStore
- [ ] app-managed files/documents
- [ ] SAF/document URI access

Automatic FAIL:
- destructive migration
- data reset
- app database recreation without approved migration
- signing mismatch preventing update
- package change creating a second app
- inaccessible documents/images after update

## D. Stable-flow regression — P0
- [ ] Tables overview
- [ ] Order creation/edit
- [ ] Send to Kitchen
- [ ] kitchen queue/batch behavior
- [ ] delivery confirmation behavior
- [ ] payment block if required delivery confirmation is unresolved
- [ ] Payment
- [ ] History
- [ ] Revenue
- [ ] Customer synchronization
- [ ] report filters: Today / 7 days / 30 days / All

## E. Alpha51 product-management scope
- [ ] create item with name
- [ ] group/category
- [ ] price
- [ ] description/notes
- [ ] representative image
- [ ] active/paused selling state
- [ ] automatic item code generation
- [ ] edit existing item without corrupting order/history references
- [ ] deleting/pausing item does not corrupt historical orders

## F. Security and operational resilience
- [ ] no plaintext secrets accidentally packaged
- [ ] exported Android components reviewed
- [ ] permissions are necessary and minimal
- [ ] no debug-only behavior in release candidate
- [ ] backup/restore procedure tested
- [ ] crash-free launch after migration

## Verdict
Only one verdict is allowed:
- PASS
- FAIL
- PASS WITH WARNING

Any P0 failure => FAIL and release is blocked.
