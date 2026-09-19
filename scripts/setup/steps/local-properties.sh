#!/usr/bin/env bash
# Gitignored local.properties, with sdk.dir for Gradle.

LOCAL_PROPERTIES="$LOGDATE_REPO_ROOT/local.properties"

local_properties_describe() {
    printf 'local.properties points Gradle at the Android SDK'
}

local_properties_sdk_dir() {
    local candidate
    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [[ -n "$candidate" && -d "$candidate/platforms" ]] && { printf '%s' "$candidate"; return 0; }
    done
    return 1
}

local_properties_check() {
    local sdk
    if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
        printf 'local.properties is missing\n'
        return 1
    fi
    sdk="$(sed -n 's/^sdk\.dir=//p' "$LOCAL_PROPERTIES" | tail -1)"
    if [[ -z "$sdk" || ! -d "$sdk/platforms" ]]; then
        printf 'sdk.dir is %s, which is not an Android SDK\n' "${sdk:-unset}"
        return 1
    fi
    return 0
}

local_properties_apply() {
    local sdk primary
    sdk="$(local_properties_sdk_dir)" || {
        printf 'No Android SDK found. Install Android Studio, open it once so it downloads the SDK,\n'
        printf 'or set ANDROID_HOME to an existing SDK, then re-run ./run setup.\n'
        return 2
    }

    primary="$(primary_checkout)"
    if [[ ! -f "$LOCAL_PROPERTIES" && "$primary" != "$LOGDATE_REPO_ROOT" && -f "$primary/local.properties" ]]; then
        log_info "Copying local.properties from the primary checkout $primary"
        cp "$primary/local.properties" "$LOCAL_PROPERTIES"
    fi
    touch "$LOCAL_PROPERTIES"

    # Replace sdk.dir; keep every other key.
    local tmp
    tmp="$(mktemp)"
    grep -v '^sdk\.dir=' "$LOCAL_PROPERTIES" > "$tmp" || true
    printf 'sdk.dir=%s\n' "$sdk" >> "$tmp"
    mv "$tmp" "$LOCAL_PROPERTIES"
    log_info "sdk.dir=$sdk"
}
