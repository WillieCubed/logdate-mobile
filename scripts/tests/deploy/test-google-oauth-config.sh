#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly ACTION="$ROOT_DIR/.github/actions/setup-firebase-configs/action.yml"

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

rg -F 'environmentVariable("LOGDATE_GOOGLE_SERVER_CLIENT_ID")' \
    "$ROOT_DIR/shared/config/build.gradle.kts" >/dev/null \
    || fail "shared config does not read the Google server client ID from the environment"

rg -F 'google-server-client-id:' "$ACTION" >/dev/null \
    || fail "Firebase setup action does not accept a Google server client ID"
rg -F 'LOGDATE_GOOGLE_SERVER_CLIENT_ID=' "$ACTION" >/dev/null \
    || fail "Firebase setup action does not export the Google server client ID"

rg -F 'vars.LOGDATE_GOOGLE_SERVER_CLIENT_ID_DEBUG' \
    "$ROOT_DIR/.github/workflows/ci.yml" \
    "$ROOT_DIR/.github/workflows/screenshot-test.yml" >/dev/null \
    || fail "debug workflows do not provide the staging Google OAuth client ID"

rg -F 'vars.LOGDATE_GOOGLE_SERVER_CLIENT_ID_RELEASE' \
    "$ROOT_DIR/.github/workflows/publish-android-play.yml" \
    "$ROOT_DIR/.github/workflows/publish-ios-app-store.yml" >/dev/null \
    || fail "release workflows do not provide the production Google OAuth client ID"

printf 'PASS: mobile builds receive environment-specific Google OAuth client IDs\n'
