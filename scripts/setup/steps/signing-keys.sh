#!/usr/bin/env bash
# The environment's upload keystore, whose certificate must match
# infra/android-signing/<env>-signer-evidence.json.

signing_keys_describe() {
    printf 'The %s upload keystore is present, private, and the one the servers trust' "$SETUP_ENV"
}

signing_keys_dir() { printf '%s' "${LOGDATE_SIGNING_DIR:-$HOME/.logdate-signing}"; }
signing_keys_store() { printf '%s/%s-upload.jks' "$(signing_keys_dir)" "$SETUP_ENV"; }
signing_keys_env_file() { printf '%s/%s-upload.env' "$(signing_keys_dir)" "$SETUP_ENV"; }
signing_keys_evidence() { printf '%s/infra/android-signing/%s-signer-evidence.json' "$LOGDATE_REPO_ROOT" "$SETUP_ENV"; }

signing_keys_expected() {
    [[ -f "$(signing_keys_evidence)" ]] || return 1
    jq -r '.expected_build_signer_fingerprint' "$(signing_keys_evidence)"
}

# Certificate SHA-256. Password via -storepass:env, not argv.
signing_keys_actual() (
    set +x
    # shellcheck source=/dev/null
    source "$(signing_keys_env_file)"
    export LOGDATE_SETUP_STOREPASS="$LOGDATE_RELEASE_STORE_PASSWORD"
    keytool -list -v -keystore "$(signing_keys_store)" -alias "$LOGDATE_RELEASE_KEY_ALIAS" \
        -storepass:env LOGDATE_SETUP_STOREPASS 2>/dev/null \
        | sed -n 's/^[[:space:]]*SHA256: //p' | head -1
)

signing_keys_mode() {
    # GNU first: GNU `stat -f` succeeds with filesystem data instead of failing.
    stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"
}

signing_keys_check() {
    local store env_file expected actual file drift=0
    store="$(signing_keys_store)"
    env_file="$(signing_keys_env_file)"

    if [[ ! -f "$store" || ! -f "$env_file" ]]; then
        if expected="$(signing_keys_expected)"; then
            printf 'The %s upload key (%s) is not on this machine.\n' "$SETUP_ENV" "$expected"
            printf 'It cannot be regenerated. Restore %s and %s from your backup,\n' "$store" "$env_file"
            printf 'or, if it is truly lost, start a Play upload-key reset in Play Console.\n'
            return 2
        fi
        printf 'No %s upload keystore exists yet\n' "$SETUP_ENV"
        return 1
    fi

    for file in "$store" "$env_file"; do
        if [[ "$(signing_keys_mode "$file")" != "600" ]]; then
            printf '%s is readable by other users\n' "$file"
            drift=1
        fi
    done

    if expected="$(signing_keys_expected)"; then
        actual="$(signing_keys_actual)"
        if [[ "$actual" != "$expected" ]]; then
            printf 'The keystore certificate is %s, but the servers trust %s.\n' "${actual:-unreadable}" "$expected"
            printf 'Restore the right %s key; a different one can never sign in.\n' "$SETUP_ENV"
            return 2
        fi
    fi
    return "$drift"
}

signing_keys_apply() {
    local store env_file
    store="$(signing_keys_store)"
    env_file="$(signing_keys_env_file)"
    if [[ ! -f "$store" ]]; then
        "$LOGDATE_REPO_ROOT/scripts/create-signing-keystore.sh" --environment "$SETUP_ENV"
        printf 'Created a new %s upload key. Back up %s now, then commit the\n' "$SETUP_ENV" "$(signing_keys_dir)"
        printf 'fingerprint it printed with scripts/android-signer-evidence.sh generate.\n'
        return 2
    fi
    chmod 600 "$store" "$env_file"
}
