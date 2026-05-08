#!/usr/bin/env bash
# sync-firebase-configs.sh
#
# Uploads Firebase config files from local disk to GitHub Actions secrets,
# base64-encoded, under the canonical names the setup-firebase-configs
# composite action expects.
#
# Usage:
#   ./scripts/sync-firebase-configs.sh android-debug    # debug Firebase project
#   ./scripts/sync-firebase-configs.sh android-release  # release Firebase project
#   ./scripts/sync-firebase-configs.sh ios              # iOS Firebase project
#   ./scripts/sync-firebase-configs.sh android-all      # both Android flavors
#   ./scripts/sync-firebase-configs.sh all              # everything
#
# Validates the file before upload (jq for JSON, plutil for plist), refuses
# to upload garbage. Idempotent — re-running with a newer file rotates the
# secret in place. Requires `gh` CLI authenticated against the LogDateApp
# repo (this script always sets secrets for the repo `gh` resolves from
# the cwd).

set -euo pipefail

ANDROID_DEBUG_PATH="app/android-main/google-services.json"
ANDROID_DEBUG_SECRET="LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_DEBUG_BASE64"
ANDROID_RELEASE_PATH="app/android-main/src/release/google-services.json"
ANDROID_RELEASE_SECRET="LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_RELEASE_BASE64"
IOS_PATH="iosApp/iosApp/GoogleService-Info.plist"
IOS_SECRET="LOGDATE_IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64"

log_error() {
    printf '[error] %s\n' "$1" >&2
}

die() {
    log_error "$1"
    exit 1
}

require_gh() {
    command -v gh >/dev/null || die "gh CLI not installed. Install from https://cli.github.com."
    gh auth status >/dev/null 2>&1 || die "gh CLI not authenticated. Run 'gh auth login'."
}

# upload_json <local-path> <secret-name>
upload_json() {
    local path="$1" secret="$2"
    [[ -f "$path" ]] || die "$path does not exist locally."
    jq -e '.project_info.project_id' "$path" >/dev/null \
        || die "$path is not a valid Firebase config (missing .project_info.project_id)."
    base64 -i "$path" | gh secret set "$secret"
    printf '[ok] Uploaded %s → %s\n' "$path" "$secret"
}

upload_ios() {
    [[ -f "$IOS_PATH" ]] || die "$IOS_PATH does not exist locally."
    plutil -lint "$IOS_PATH" >/dev/null \
        || die "$IOS_PATH did not lint as a valid plist."
    base64 -i "$IOS_PATH" | gh secret set "$IOS_SECRET"
    printf '[ok] Uploaded %s → %s\n' "$IOS_PATH" "$IOS_SECRET"
}

usage() {
    cat <<EOF >&2
Usage: $0 {android-debug|android-release|android-all|ios|all}

  android-debug    Upload $ANDROID_DEBUG_PATH → $ANDROID_DEBUG_SECRET
  android-release  Upload $ANDROID_RELEASE_PATH → $ANDROID_RELEASE_SECRET
  android-all      Both Android flavors above
  ios              Upload $IOS_PATH → $IOS_SECRET
  all              Everything (both Android flavors + iOS)
EOF
    exit 2
}

main() {
    require_gh
    case "${1:-}" in
        android-debug)   upload_json "$ANDROID_DEBUG_PATH" "$ANDROID_DEBUG_SECRET" ;;
        android-release) upload_json "$ANDROID_RELEASE_PATH" "$ANDROID_RELEASE_SECRET" ;;
        android-all)
            upload_json "$ANDROID_DEBUG_PATH" "$ANDROID_DEBUG_SECRET"
            upload_json "$ANDROID_RELEASE_PATH" "$ANDROID_RELEASE_SECRET"
            ;;
        ios) upload_ios ;;
        all)
            upload_json "$ANDROID_DEBUG_PATH" "$ANDROID_DEBUG_SECRET"
            upload_json "$ANDROID_RELEASE_PATH" "$ANDROID_RELEASE_SECRET"
            upload_ios
            ;;
        *) usage ;;
    esac
}

main "$@"
