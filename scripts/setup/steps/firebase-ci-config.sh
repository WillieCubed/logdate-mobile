#!/usr/bin/env bash
# CI's base64 google-services.json secret. GitHub hides secret values, so the
# <SECRET>_SHA256 repository variable records the uploaded file's hash.

firebase_ci_config_describe() {
    printf 'CI has the current %s Firebase config' "$(firebase_ci_config_variant)"
}

firebase_ci_config_requires() {
    printf 'firebase-apps'
}

firebase_ci_config_variant() {
    [[ "$SETUP_ENV" == "production" ]] && printf 'release' || printf 'debug'
}

firebase_ci_config_name() {
    printf 'LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_%s_BASE64' "$(firebase_ci_config_variant | tr '[:lower:]' '[:upper:]')"
}

firebase_ci_config_file() {
    # shellcheck source=scripts/setup/steps/firebase-configs.sh
    source "$SETUP_STEPS_DIR/firebase-configs.sh"
    firebase_configs_file "$(firebase_ci_config_variant)"
}

firebase_ci_config_check() {
    local name file recorded
    name="$(firebase_ci_config_name)"
    file="$(firebase_ci_config_file)"
    if ! firebase_configs_valid "$(firebase_ci_config_variant)" "$file" >/dev/null; then
        printf 'The local %s config is missing or stale, so there is nothing current to upload\n' \
            "$(firebase_ci_config_variant)"
        return 1
    fi
    if ! gh secret list --json name --jq '.[].name' | grep -qx "$name"; then
        printf 'Secret %s does not exist\n' "$name"
        return 1
    fi
    recorded="$(gh variable get "${name}_SHA256" 2>/dev/null || true)"
    if [[ "$recorded" != "$(sha256_file "$file")" ]]; then
        printf 'Secret %s may not match the local config (recorded hash: %s)\n' "$name" "${recorded:-none}"
        return 1
    fi
    return 0
}

firebase_ci_config_apply() {
    local variant file name
    variant="$(firebase_ci_config_variant)"
    file="$(firebase_ci_config_file)"
    name="$(firebase_ci_config_name)"

    # firebase-apps may have added apps since firebase-configs ran.
    if ! firebase_configs_valid "$variant" "$file" >/dev/null; then
        firebase_configs_apply || return $?
    fi

    # errexit is off inside steps; record the hash only after a successful upload.
    "$LOGDATE_REPO_ROOT/scripts/sync-firebase-configs.sh" "android-$variant" || return 1
    gh variable set "${name}_SHA256" --body "$(sha256_file "$file")"
    log_info "Recorded ${name}_SHA256"
}
