#!/usr/bin/env bash

set -euo pipefail

readonly ANDROID_PACKAGE="studio.hypertext.logdate"
readonly IOS_BUNDLE="studio.hypertext.LogDate"

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

validate_android() {
    local path="$1" expected_project="$2" expected_app="$3"
    local project

    jq -e . "$path" >/dev/null 2>&1 || die "$path is not valid JSON."
    project="$(jq -r '.project_info.project_id // empty' "$path")"
    [[ "$project" == "$expected_project" ]] \
        || die "$path expected project $expected_project, found ${project:-<missing>}."

    jq -e --arg package "$ANDROID_PACKAGE" \
        '.client[]? | select(.client_info.android_client_info.package_name == $package)' \
        "$path" >/dev/null \
        || die "$path expected Android package $ANDROID_PACKAGE."

    jq -e --arg package "$ANDROID_PACKAGE" --arg app "$expected_app" '
        .client[]?
        | select(.client_info.android_client_info.package_name == $package)
        | select(.client_info.mobilesdk_app_id == $app)
    ' "$path" >/dev/null \
        || die "$path expected Firebase app $expected_app for $ANDROID_PACKAGE."

    jq -e --arg package "$ANDROID_PACKAGE" '
        .client[]?
        | select(.client_info.android_client_info.package_name == $package)
        | .api_key[]?.current_key
        | strings
        | select(length > 0)
    ' "$path" >/dev/null \
        || die "$path is missing API key for $ANDROID_PACKAGE."
}

plist_value() {
    plutil -extract "$2" raw -o - "$1" 2>/dev/null || true
}

validate_ios() {
    local path="$1"
    local project bundle app_id api_key

    plutil -lint "$path" >/dev/null 2>&1 || die "$path is not a valid plist."
    project="$(plist_value "$path" PROJECT_ID)"
    bundle="$(plist_value "$path" BUNDLE_ID)"
    app_id="$(plist_value "$path" GOOGLE_APP_ID)"
    api_key="$(plist_value "$path" API_KEY)"

    [[ "$project" == "logdate" ]] \
        || die "$path expected project logdate, found ${project:-<missing>}."
    [[ "$bundle" == "$IOS_BUNDLE" ]] \
        || die "$path expected iOS bundle $IOS_BUNDLE, found ${bundle:-<missing>}."
    [[ "$app_id" == "1:216887423795:ios:35aa0348aa4f6fcb8ec446" ]] \
        || die "$path expected Firebase app 1:216887423795:ios:35aa0348aa4f6fcb8ec446."
    [[ -n "$api_key" ]] || die "$path is missing API_KEY."
}

[[ $# -eq 2 ]] || die "usage: $0 {android-debug|android-release|ios} <config-file>"
readonly CONFIG_KIND="$1"
readonly CONFIG_PATH="$2"
[[ -f "$CONFIG_PATH" ]] || die "$CONFIG_PATH does not exist."

case "$CONFIG_KIND" in
    android-debug)
        validate_android \
            "$CONFIG_PATH" \
            "logdate-dev" \
            "1:786734185325:android:d1d954e3ec8b414b23f864"
        ;;
    android-release)
        validate_android \
            "$CONFIG_PATH" \
            "logdate" \
            "1:216887423795:android:1d5cb98b3aaefc568ec446"
        ;;
    ios) validate_ios "$CONFIG_PATH" ;;
    *) die "unknown config kind: $CONFIG_KIND" ;;
esac

printf 'Validated %s Firebase config: %s\n' "$CONFIG_KIND" "$CONFIG_PATH"
