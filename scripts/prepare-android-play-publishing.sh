#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"

log_error() {
    printf '[error] %s\n' "$1" >&2
}

die() {
    log_error "$1"
    exit 1
}

require_env() {
    local name="$1"
    [[ -n "${!name:-}" ]] || die "$name must be configured before Android Play publishing can run."
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

prepare_internal() {
    require_env "ANDROID_PUBLISHER_CREDENTIALS"
    require_env "LOGDATE_ANDROID_GOOGLE_SERVICES_JSON"
    require_env "LOGDATE_RELEASE_STORE_BASE64"
    require_env "LOGDATE_RELEASE_STORE_PASSWORD"
    require_env "LOGDATE_RELEASE_KEY_ALIAS"
    require_env "LOGDATE_RELEASE_KEY_PASSWORD"

    local release_dir
    release_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/logdate-play"
    mkdir -p "$release_dir"

    local keystore_path google_services_path
    keystore_path="${release_dir}/logdate-release.jks"
    google_services_path="app/android-main/google-services.json"

    printf '%s' "$LOGDATE_RELEASE_STORE_BASE64" | base64 --decode > "$keystore_path"
    printf '%s' "$LOGDATE_ANDROID_GOOGLE_SERVICES_JSON" > "$google_services_path"
    chmod 600 "$keystore_path" "$google_services_path"

    write_env "LOGDATE_RELEASE_STORE_FILE" "$keystore_path"
    write_output "release_dir" "$release_dir"
    write_output "keystore_path" "$keystore_path"
    write_output "google_services_path" "$google_services_path"
}

prepare_production() {
    require_env "ANDROID_PUBLISHER_CREDENTIALS"
}

case "$MODE" in
    internal) prepare_internal ;;
    production) prepare_production ;;
    *)
        die "Usage: $0 <internal|production>"
        ;;
esac
