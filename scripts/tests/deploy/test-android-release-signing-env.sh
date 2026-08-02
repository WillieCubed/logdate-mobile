#!/usr/bin/env bash
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

build_file="app/android-main/build.gradle.kts"

assert_file_contains '"storeFile" to "LOGDATE_RELEASE_STORE_FILE"' "$build_file"
assert_file_contains '"storePassword" to "LOGDATE_RELEASE_STORE_PASSWORD"' "$build_file"
assert_file_contains '"keyAlias" to "LOGDATE_RELEASE_KEY_ALIAS"' "$build_file"
assert_file_contains '"keyPassword" to "LOGDATE_RELEASE_KEY_PASSWORD"' "$build_file"
assert_file_not_contains '"LOGDATE_RELEASE_${prop.uppercase()}"' "$build_file"

print_pass_summary "Android release signing environment"
