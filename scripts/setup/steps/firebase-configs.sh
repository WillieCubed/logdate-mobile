#!/usr/bin/env bash
# The real google-services.json files; without them Gradle writes a stub.
#
#   debug   app/android-main/google-services.json              staging Firebase
#   release app/android-main/src/release/google-services.json  production Firebase

firebase_configs_describe() {
    printf 'Real Firebase configs are in place for debug and release builds'
}

firebase_configs_file() {
    case "$1" in
        debug) printf '%s' "$LOGDATE_REPO_ROOT/app/android-main/google-services.json" ;;
        release) printf '%s' "$LOGDATE_REPO_ROOT/app/android-main/src/release/google-services.json" ;;
    esac
}

firebase_configs_env() {
    case "$1" in
        debug) printf 'staging' ;;
        release) printf 'production' ;;
    esac
}

# Prints the validator's complaint and fails when FILE is not a valid config.
firebase_configs_valid() {
    local variant="$1" file="$2"
    [[ -f "$file" ]] || { printf '%s is missing\n' "${file#"$LOGDATE_REPO_ROOT"/}"; return 1; }
    # shellcheck disable=SC2069 # keep stderr only
    "$LOGDATE_REPO_ROOT/scripts/validate-firebase-config.sh" "android-$variant" "$file" 2>&1 >/dev/null
}

firebase_configs_check() {
    local variant drift=0
    for variant in debug release; do
        firebase_configs_valid "$variant" "$(firebase_configs_file "$variant")" || drift=1
    done
    return "$drift"
}

firebase_configs_apply() {
    # shellcheck source=scripts/lib/google-api.sh
    source "$LOGDATE_REPO_ROOT/scripts/lib/google-api.sh"
    local variant file primary env tmp manual=0
    primary="$(primary_checkout)"

    for variant in debug release; do
        file="$(firebase_configs_file "$variant")"
        firebase_configs_valid "$variant" "$file" >/dev/null && continue

        tmp="$primary/${file#"$LOGDATE_REPO_ROOT"/}"
        if [[ "$primary" != "$LOGDATE_REPO_ROOT" ]] && firebase_configs_valid "$variant" "$tmp" >/dev/null; then
            mkdir -p "$(dirname "$file")"
            cp "$tmp" "$file"
            log_info "Copied the $variant config from $primary"
            continue
        fi

        env="$(firebase_configs_env "$variant")"
        tmp="$(mktemp)"
        if ! firebase_android_config "$(environment_value "$env" FIREBASE_PROJECT_ID)" \
            "$(environment_value "$env" FIREBASE_ANDROID_APP_ID)" "$tmp"; then
            rm -f "$tmp"
            printf 'Could not download the %s config. Run gcloud auth login, then re-check.\n' "$variant"
            manual=1
            continue
        fi
        if ! firebase_configs_valid "$variant" "$tmp"; then
            rm -f "$tmp"
            printf 'The downloaded %s config is incomplete. Run ./run setup %s to register its Firebase apps.\n' \
                "$variant" "$env"
            manual=1
            continue
        fi
        mkdir -p "$(dirname "$file")"
        mv "$tmp" "$file"
        log_info "Downloaded the $variant config from Firebase"
    done
    [[ "$manual" -eq 0 ]] || return 2
}
