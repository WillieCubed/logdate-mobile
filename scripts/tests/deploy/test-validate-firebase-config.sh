#!/usr/bin/env bash

set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

readonly VALIDATOR="scripts/validate-firebase-config.sh"
readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

write_android_config() {
    local path="$1" project_id="$2" app_id="$3" package_name="$4"
    jq -n \
        --arg project_id "$project_id" \
        --arg app_id "$app_id" \
        --arg package_name "$package_name" \
        '{
            project_info: {project_id: $project_id},
            client: [{
                client_info: {
                    mobilesdk_app_id: $app_id,
                    android_client_info: {package_name: $package_name}
                },
                api_key: [{current_key: "test-key"}]
            }]
        }' >"$path"
}

write_ios_config() {
    local path="$1" project_id="$2" app_id="$3" bundle_id="$4"
    plutil -create xml1 "$path"
    plutil -insert PROJECT_ID -string "$project_id" "$path"
    plutil -insert GOOGLE_APP_ID -string "$app_id" "$path"
    plutil -insert BUNDLE_ID -string "$bundle_id" "$path"
    plutil -insert API_KEY -string "test-key" "$path"
}

assert_rejected() {
    local expected_message="$1"
    shift
    local output
    if output=$("$@" 2>&1); then
        printf 'Expected command to fail: %s\n' "$*" >&2
        exit 1
    fi
    if [[ "$output" != *"$expected_message"* ]]; then
        printf 'Expected failure containing %q, got:\n%s\n' "$expected_message" "$output" >&2
        exit 1
    fi
}

debug_config="$TEMP_DIR/debug.json"
write_android_config \
    "$debug_config" \
    "logdate-dev" \
    "1:786734185325:android:d1d954e3ec8b414b23f864" \
    "studio.hypertext.logdate"
"$VALIDATOR" android-debug "$debug_config"

release_config="$TEMP_DIR/release.json"
write_android_config \
    "$release_config" \
    "logdate" \
    "1:216887423795:android:1d5cb98b3aaefc568ec446" \
    "studio.hypertext.logdate"
"$VALIDATOR" android-release "$release_config"

ios_config="$TEMP_DIR/GoogleService-Info.plist"
write_ios_config \
    "$ios_config" \
    "logdate" \
    "1:216887423795:ios:35aa0348aa4f6fcb8ec446" \
    "studio.hypertext.LogDate"
"$VALIDATOR" ios "$ios_config"

write_android_config \
    "$debug_config" \
    "logdate" \
    "1:786734185325:android:d1d954e3ec8b414b23f864" \
    "studio.hypertext.logdate"
assert_rejected "expected project logdate-dev" "$VALIDATOR" android-debug "$debug_config"

write_android_config \
    "$debug_config" \
    "logdate-dev" \
    "1:786734185325:android:d1d954e3ec8b414b23f864" \
    "co.reasonabletech.logdate"
assert_rejected "expected Android package studio.hypertext.logdate" "$VALIDATOR" android-debug "$debug_config"

write_android_config \
    "$release_config" \
    "logdate" \
    "1:216887423795:android:wrong" \
    "studio.hypertext.logdate"
assert_rejected "expected Firebase app 1:216887423795:android:1d5cb98b3aaefc568ec446" \
    "$VALIDATOR" android-release "$release_config"

write_android_config \
    "$release_config" \
    "logdate" \
    "1:216887423795:android:1d5cb98b3aaefc568ec446" \
    "studio.hypertext.logdate"
jq '(.client[] | select(.client_info.android_client_info.package_name == "studio.hypertext.logdate")) |= del(.api_key)' \
    "$release_config" >"$release_config.tmp"
mv "$release_config.tmp" "$release_config"
assert_rejected "missing API key" "$VALIDATOR" android-release "$release_config"

write_ios_config \
    "$ios_config" \
    "logdate" \
    "1:216887423795:ios:35aa0348aa4f6fcb8ec446" \
    "co.reasonabletech.logdate"
assert_rejected "expected iOS bundle studio.hypertext.LogDate" "$VALIDATOR" ios "$ios_config"

write_ios_config \
    "$ios_config" \
    "logdate" \
    "1:216887423795:ios:35aa0348aa4f6fcb8ec446" \
    "studio.hypertext.LogDate"
plutil -remove API_KEY "$ios_config"
assert_rejected "missing API_KEY" "$VALIDATOR" ios "$ios_config"

print_pass_summary "Firebase config validation"
