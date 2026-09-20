#!/usr/bin/env bash
set -euo pipefail
rules_file="${1:-firestore.rules}"

private_rule='match /users/{ownerUid}/stores/{storeId}/privateBackup/{document=**}'
grep -Fq "$private_rule" "$rules_file"
private_block=$(awk '/match \/users\/\{ownerUid\}\/stores\/\{storeId\}\/privateBackup\/\{document=\*\*\}/,/^    }/' "$rules_file")
grep -Fq 'allow read, write: if isStoreOwner(ownerUid);' <<<"$private_block"
store_block=$(awk '/match \/users\/\{ownerUid\}\/stores\/\{storeId\}\/\{document=\*\*\}/,/^    }/' "$rules_file")
! grep -Fq 'hasManagerReadAccess' <<<"$store_block"
echo "private backup rule shape: PASS"
