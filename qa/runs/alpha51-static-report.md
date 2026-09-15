# POS0210 alpha51 — Independent QA Static Report

Status: **HOLD / NOT RELEASED**

Baseline: `POS0210_v1.0.0-alpha50.apk`
Candidate: `POS0210_v1.0.0-alpha51.apk`

## Artifact integrity

- alpha50 SHA-256: `2d8d9154d5a469819c01a7acd33764e1a2fbabe4905341a0c60ed8b4bee021c8`
- alpha51 SHA-256: `a976c74ab60f80634dd19524fb4bad9b8fa762e87096d04528ea35e06309b12d`
- Both APK ZIP structures pass integrity testing.
- Both contain 123 ZIP entries.

## Package / version

- Package observed in manifest strings: `vn.ecohome.pos0210`
- App label observed: `0210 POS`
- Baseline versionName: `1.0.0-alpha50`
- Candidate versionName: `1.0.0-alpha51`

## Signing gate

Both APKs contain APK Signature Scheme v2 block (`0x7109871a`).

Signing certificate SHA-256 fingerprint is identical on alpha50 and alpha51:

`F5:52:D0:F1:58:EF:B1:A3:57:30:60:CE:49:6B:3B:F4:56:AB:00:5D:20:DF:0F:7D:89:3D:9E:C3:BC:A1:1F:E3`

Certificate subject/issuer: `C=VN, O=Ecohome, CN=0210 Dev`

Result: **PASS** for same-signer update compatibility.

## APK binary delta

Changed ZIP entries between alpha50 and alpha51 include:

- `AndroidManifest.xml`
- `classes3.dex`
- `classes4.dex`

Core resources/native libraries remain unchanged in the static comparison performed.

## Database migration 10 -> 11

Source review confirms explicit Room migration `MIGRATION_10_11`.

Migration order is safe with respect to the new unique product code index:

1. Add `productCode TEXT NOT NULL DEFAULT ''`.
2. Add `description TEXT NOT NULL DEFAULT ''`.
3. Read all existing menu items/categories.
4. Generate and assign a non-empty productCode to each existing menu item.
5. Create unique index `index_MenuItemEntity_productCode` only after assignment.

Room database construction uses `.addMigrations(...)` including `MIGRATION_10_11`.
No `fallbackToDestructiveMigration` path was observed in `PosDatabase`.

Result: **PASS — static migration design**.

This does not replace runtime data-survival testing.

## Backup / restore hardening review

`DataBackup` includes the following safety mechanisms:

- WAL checkpoint before DB copy.
- SQLite header validation on backup and restore inputs.
- `LATEST`, `TEMP`, and `PREVIOUS` DB files.
- Existing valid latest DB is copied to previous before replacement.
- Restore first copies current DB to a rollback file.
- DB sidecar `-wal` and `-shm` files are cleared during replacement.
- Restored database is opened and passed through `DatabaseHealth.validate`.
- Failed restore attempts revert to the pre-restore DB.
- Managed media is backed up separately as a ZIP archive.
- Timestamped DB archive is paired with timestamped media archive; restore rejects a missing matching media archive rather than silently pairing unrelated data.

Result: **PASS — static backup/rollback design**.

## Configuration / media preservation review

`ConfigBackup` exports/restores areas, tables, menu categories, menu items, product codes, descriptions, combos, combo items, employees/PIN/permissions, purchase categories, pricing rules, selected app settings, and menu/combo images.

Some storage-location keys (`autoback_tree_uri`, `storage_root_uri`, `master_config_uri`) are intentionally excluded from exported config and re-bound to the selected root during restore.

Result: **PASS WITH RUNTIME VERIFICATION REQUIRED**.

## SAF / document permission persistence

Main UI source calls `takePersistableUriPermission` for:

- the selected POS0210 root tree URI with read + write permission;
- menu/combo image document URIs with read permission;
- invoice document/image URIs with read permission.

`SafPosStorage` uses `DocumentsContract` tree/document APIs rather than string-concatenating child URIs.

Result: **PASS — static SAF persistence review**.

## Test-environment limitation encountered

An attempted local SQLite migration simulation could not execute because the current Python runtime returned a disk-I/O error when creating SQLite tables. The system container does not provide the `sqlite3` CLI. No PASS claim is made from that failed simulation.

## Release decision

**HOLD / NOT RELEASED**.

Passed static gates:

- APK integrity.
- Same package / same signer compatibility.
- Explicit non-destructive Room migration design.
- Safe migration ordering for the new unique product code index.
- Backup / rollback design.
- Persistable SAF permissions.

Still required before release:

1. Real Android in-place update test: alpha50 -> alpha51 without uninstall.
2. Before/after data comparison on representative DB/config/media.
3. Runtime regression of Tables -> Order -> Kitchen -> Delivery -> Payment -> History -> Revenue -> Customer.
4. Runtime verification of backup/restore, images, invoice files, printer/bank/settings and SAF access after update/restart.

## Required next gate

Prepare a disposable Android test environment, install alpha50, populate representative records/config/media, capture before-state, install alpha51 over alpha50, then verify retained data and stable workflows. A clean install is not an acceptable substitute for this release gate.
