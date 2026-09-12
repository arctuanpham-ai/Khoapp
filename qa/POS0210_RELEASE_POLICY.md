# POS0210 Release Policy

## Principle
No APK is considered an official update until it passes the independent QA gate. Builder output is always a candidate artifact.

## Mandatory release gates
1. Build integrity
2. Package/version/signing compatibility
3. Android compatibility
4. DATA SURVIVAL GATE
5. Regression gate
6. New-feature acceptance tests
7. Security/permission review
8. Backup/restore verification

## DATA SURVIVAL GATE (P0)
Upgrade must be tested as an in-place update from the previous accepted version. Uninstall/reinstall is not an acceptable migration test.

Before update, seed representative data including:
- tables and active orders
- kitchen batches / delivery state
- payment/history/revenue records
- customers, points, member/VIP/VVIP state
- menu groups/items/prices/status/images/notes
- employees, PINs and permissions
- bank account / QR settings
- printer settings
- stock/import records and invoice images
- SharedPreferences/DataStore settings
- SAF/document URI grants and app-managed documents

After installing the candidate over the previous version, verify:
- no record loss unless explicitly documented by a migration
- referential integrity remains valid
- totals/revenue/history remain identical
- settings and permissions remain intact
- images/documents remain readable
- printer/bank/QR configuration remains usable
- app opens without crash and migration errors

Any destructive migration, database clearing, silent reset, package-name change, signing-key mismatch, or unintended document loss is an automatic FAIL.

## Regression gate (P0)
The stable operational path must continue to work:
Tables -> Order -> Kitchen -> Delivered -> Payment -> History -> Revenue -> Customer

Also test:
- cannot pay while required delivery confirmation is unresolved
- pending-delivery warnings clear after valid payment flow
- Today / 7 days / 30 days / All report filters
- role and permission behavior
- backup/restore paths

## Version discipline
Every candidate must have a unique versionCode and traceable commit SHA. Do not overwrite an already produced artifact with different bytes under the same identity.

Recommended naming:
- POS0210_vX.Y.Z-alphaNN-candidate.N.apk
- POS0210_vX.Y.Z-alphaNN.apk only after PASS

## QA outcomes
Independent QA may return only:
- PASS
- FAIL
- PASS WITH WARNING

P0 failures always block release.
