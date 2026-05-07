#!/usr/bin/env bash
# install-ios-signing.sh
#
# Decodes the iOS distribution certificate (.p12) and provisioning
# profile (.mobileprovision) supplied via base64-encoded environment
# variables, installs them into a per-run keychain on the macOS runner,
# and registers the provisioning profile where xcodebuild expects it.
# Intended to be invoked once at the top of every iOS publish job.
#
# Required env (typically GitHub Secrets):
#   IOS_DISTRIBUTION_CERT_P12_BASE64    base64 of the .p12 file
#   IOS_DISTRIBUTION_CERT_PASSWORD      password to unlock the .p12
#   IOS_PROVISIONING_PROFILE_BASE64     base64 of the .mobileprovision
#
# The keychain is ephemeral — its name carries the runner's PID so two
# concurrent jobs on the same runner can't collide, and it's added to
# the user's keychain search list with prepend semantics. After the job
# finishes the keychain file is in $RUNNER_TEMP which GitHub deletes.
#
# Mirrors the secret-injection pattern in
# scripts/prepare-android-play-publishing.sh.

set -euo pipefail

log_error() {
    printf '[error] %s\n' "$1" >&2
}

die() {
    log_error "$1"
    exit 1
}

require_env() {
    local name="$1"
    [[ -n "${!name:-}" ]] || die "$name must be configured before iOS signing can be installed."
}

require_macos() {
    if [[ "$(uname -s)" != "Darwin" ]]; then
        die "iOS signing setup must run on macOS (uname says: $(uname -s))."
    fi
}

write_output() {
    local key="$1"
    local value="$2"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
    fi
}

write_env() {
    local key="$1"
    local value="$2"
    if [[ -n "${GITHUB_ENV:-}" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$GITHUB_ENV"
    fi
}

main() {
    require_macos
    require_env "IOS_DISTRIBUTION_CERT_P12_BASE64"
    require_env "IOS_DISTRIBUTION_CERT_PASSWORD"
    require_env "IOS_PROVISIONING_PROFILE_BASE64"

    local work_dir
    work_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/logdate-ios-signing"
    mkdir -p "$work_dir"

    local cert_path profile_path
    cert_path="${work_dir}/distribution.p12"
    profile_path="${work_dir}/distribution.mobileprovision"

    printf '%s' "$IOS_DISTRIBUTION_CERT_P12_BASE64" | base64 --decode > "$cert_path"
    printf '%s' "$IOS_PROVISIONING_PROFILE_BASE64" | base64 --decode > "$profile_path"
    chmod 600 "$cert_path" "$profile_path"

    # Per-run keychain name. RUNNER_TEMP is per-job on GitHub-hosted
    # runners, but the keychain is created in ~/Library/Keychains so we
    # carry the PID to defuse concurrent-job collisions.
    local keychain_name keychain_path keychain_password
    keychain_name="logdate-ios-publish-$$.keychain-db"
    keychain_path="$HOME/Library/Keychains/${keychain_name}"
    keychain_password="$(openssl rand -hex 16)"

    # Create + unlock the keychain. -lut 21600 keeps it unlocked for 6h,
    # which is longer than any single iOS publish job needs.
    security create-keychain -p "$keychain_password" "$keychain_name"
    security set-keychain-settings -lut 21600 "$keychain_name"
    security unlock-keychain -p "$keychain_password" "$keychain_name"

    # Prepend our keychain so xcodebuild finds the cert before the
    # default login keychain. List-keychains takes the order it's given.
    local existing_keychains
    existing_keychains="$(security list-keychains -d user | sed -e 's/"//g' -e 's/^[[:space:]]*//')"
    # shellcheck disable=SC2086
    security list-keychains -d user -s "$keychain_name" $existing_keychains

    # Import the cert. -A allows codesign + xcodebuild to use the
    # private key without prompting; safe inside an ephemeral keychain.
    security import "$cert_path" \
        -k "$keychain_name" \
        -P "$IOS_DISTRIBUTION_CERT_PASSWORD" \
        -T /usr/bin/codesign \
        -T /usr/bin/security \
        -T /usr/bin/xcodebuild

    # Allow tools to access the imported key without UI prompts.
    security set-key-partition-list \
        -S apple-tool:,apple:,codesign: \
        -s -k "$keychain_password" "$keychain_name" \
        > /dev/null

    # Install the provisioning profile where Xcode expects to find it.
    # Apple deprecated ~/Library/MobileDevice/Provisioning Profiles in
    # Xcode 16 in favor of ~/Library/Developer/Xcode/UserData/Provisioning\ Profiles
    # but accepts both, so write to both for forward + back compat.
    local legacy_dir modern_dir
    legacy_dir="$HOME/Library/MobileDevice/Provisioning Profiles"
    modern_dir="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
    mkdir -p "$legacy_dir" "$modern_dir"

    local profile_uuid
    profile_uuid="$(/usr/libexec/PlistBuddy -c 'Print :UUID' /dev/stdin <<< "$(security cms -D -i "$profile_path")")"
    if [[ -z "$profile_uuid" ]]; then
        die "Could not read UUID from provisioning profile."
    fi

    cp "$profile_path" "${legacy_dir}/${profile_uuid}.mobileprovision"
    cp "$profile_path" "${modern_dir}/${profile_uuid}.mobileprovision"

    write_output "keychain_path" "$keychain_path"
    write_output "keychain_name" "$keychain_name"
    write_output "profile_uuid" "$profile_uuid"

    # Make the keychain reachable to subsequent steps in the same job.
    write_env "LOGDATE_IOS_KEYCHAIN_NAME" "$keychain_name"
    write_env "LOGDATE_IOS_PROVISIONING_PROFILE_UUID" "$profile_uuid"

    printf '[ok] iOS signing installed (keychain=%s, profile=%s).\n' \
        "$keychain_name" "$profile_uuid"
}

main "$@"
