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

Only these ZIP entries changed between alpha50 and alpha51:

- `AndroidManifest.xml`
- `classes3.dex`
- `classes4.dex`

`classes.dex`, resources, native libraries and other packaged entries are unchanged.

## Database migration evidence

Static DEX inspection of alpha51 reveals Room migration `MIGRATION_10_11` and schema additions to `MenuItemEntity`:

- `ALTER TABLE MenuItemEntity ADD COLUMN description TEXT NOT NULL DEFAULT ''`
- `ALTER TABLE MenuItemEntity ADD COLUMN productCode TEXT NOT NULL DEFAULT ''`
- creation of unique index `index_MenuItemEntity_productCode`
- migration-related logic that reads menu item/category data and assigns product codes

No destructive migration pattern was observed in the changed-string inspection performed for this report. However, static inspection alone does **not** prove data survival.

## Release decision

**HOLD / NOT RELEASED**.

Reasons:

1. Same-package/same-signer static gate is good.
2. Candidate includes an actual Room schema migration (10 -> 11), so the mandatory DATA SURVIVAL test must be executed before approval.
3. Runtime regression still must cover Tables -> Order -> Kitchen -> Delivery -> Payment -> History -> Revenue -> Customer plus configuration/media/backup behavior.
4. Update must be tested by installing alpha51 over an alpha50 state containing representative data. Uninstall/reinstall is not an acceptable substitute.

## Required next gate

Prepare a disposable alpha50 test state containing representative records and configuration, capture before-state counts/values, perform in-place update to alpha51, and verify all retained data and stable workflows after migration.
