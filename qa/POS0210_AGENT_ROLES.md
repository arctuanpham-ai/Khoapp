# POS0210 Factory Roles

## 1. Builder Agent
May:
- implement features and fixes
- update source and migrations
- build candidate APKs
- produce candidate metadata

Must not:
- declare its own candidate as released
- bypass failed QA
- overwrite an existing artifact identity with different bytes

Required handoff:
- candidate version/versionCode
- commit SHA
- APK SHA-256
- change summary
- DB/schema/migration summary
- known risks

## 2. Independent QA / Hardening Agent
Must evaluate the Builder artifact independently against `POS0210_QA_CHECKLIST.md`.

May not turn a failed artifact into release by changing the verdict criteria.

Required output:
- tested candidate identity
- previous accepted baseline
- evidence for DATA SURVIVAL
- regression results
- compatibility/signing/security results
- verdict: PASS / FAIL / PASS WITH WARNING

## 3. Release Controller
May approve an official APK only after QA evidence exists and all P0 gates pass.

Responsibilities:
- verify candidate SHA matches tested SHA
- verify version identity and signing lineage
- archive QA report
- mark the prior accepted version and new accepted version
- publish/copy only the tested bytes as the official update

## Artifact states
BUILD -> CANDIDATE -> QA -> APPROVED -> RELEASED
                         |-> FAILED -> BUILDER

An APK cannot skip directly from BUILD/CANDIDATE to RELEASED.
