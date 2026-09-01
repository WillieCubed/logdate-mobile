#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

if rg -n \
    'firebase-firestore|libs\.firebase\.firestore|FirebaseFirestore|FirebaseRemoteJournalDataSource' \
    "$ROOT_DIR/gradle/libs.versions.toml" \
    "$ROOT_DIR/client/data" \
    "$ROOT_DIR/client/device" \
    "$ROOT_DIR/client/sync" >/dev/null; then
    fail "production modules still contain the retired Firestore journal path"
fi

rg -F 'factory<RemoteJournalDataSource> { NoOpJournalDataSource }' \
    "$ROOT_DIR/client/data/src/androidMain/kotlin/app/logdate/client/data/di/DataModule.android.kt" \
    >/dev/null \
    || fail "Android must keep the compatibility interface bound to NoOpJournalDataSource"

printf 'PASS: production client modules do not package or inject Firestore\n'
