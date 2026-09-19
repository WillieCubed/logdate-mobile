#!/usr/bin/env bash
# The upload keystore secrets in the GitHub environment. LOGDATE_RELEASE_STORE_SHA256
# fingerprints the uploaded keystore; passwords are never fingerprinted.

PLAY_SECRET_NAMES=(
    LOGDATE_RELEASE_STORE_BASE64
    LOGDATE_RELEASE_STORE_PASSWORD
    LOGDATE_RELEASE_KEY_ALIAS
    LOGDATE_RELEASE_KEY_PASSWORD
)

play_keystore_describe() {
    printf 'GitHub environment %s holds the current upload keystore' "$GITHUB_ENVIRONMENT"
}

play_keystore_requires() {
    printf 'gh-environment signing-keys'
}

play_keystore_sources() {
    # shellcheck source=scripts/setup/steps/signing-keys.sh
    source "$SETUP_STEPS_DIR/signing-keys.sh"
    PLAY_KEYSTORE="$(signing_keys_store)"
    PLAY_UPLOAD_ENV="$(signing_keys_env_file)"
}

play_keystore_check() {
    play_keystore_sources
    local existing name drift=0
    existing="$(gh secret list --env "$GITHUB_ENVIRONMENT" --json name --jq '.[].name')"
    for name in "${PLAY_SECRET_NAMES[@]}"; do
        grep -qx "$name" <<< "$existing" && continue
        printf 'Secret %s is missing\n' "$name"
        drift=1
    done
    [[ "$drift" -eq 1 ]] && return 1

    if [[ "$(gh variable get LOGDATE_RELEASE_STORE_SHA256 --env "$GITHUB_ENVIRONMENT" 2>/dev/null)" \
        != "$(sha256_file "$PLAY_KEYSTORE")" ]]; then
        printf 'LOGDATE_RELEASE_STORE_BASE64 may not be the keystore %s\n' "$PLAY_KEYSTORE"
        return 1
    fi
    return 0
}

play_keystore_apply() {
    play_keystore_sources
    NON_INTERACTIVE=true "$LOGDATE_REPO_ROOT/scripts/upload-play-keystore.sh" \
        --env "$GITHUB_ENVIRONMENT" \
        --non-interactive \
        --keyless \
        --upload-env "$PLAY_UPLOAD_ENV" \
        --keystore "$PLAY_KEYSTORE" \
        --enable-internal false \
        --enable-production false \
        || return 1
    gh variable set LOGDATE_RELEASE_STORE_SHA256 --env "$GITHUB_ENVIRONMENT" --body "$(sha256_file "$PLAY_KEYSTORE")"
}
