#!/usr/bin/env bash
# gh and gcloud logins, for environment targets.

auth_describe() {
    printf 'GitHub and Google Cloud logins are active'
}

auth_requires() {
    printf 'tools'
}

auth_check() {
    [[ "$SETUP_TARGET" == "local" ]] && return 0
    local actions=()

    gh auth status >/dev/null 2>&1 || actions+=("gh auth login")
    # An active account can still have an expired refresh token.
    gcloud auth print-access-token >/dev/null 2>&1 || actions+=("gcloud auth login")

    [[ ${#actions[@]} -eq 0 ]] && return 0
    local joined
    joined="$(printf '%s; ' "${actions[@]}")"
    printf 'Run in your own terminal: %s\n' "${joined%; }"
    return 2
}
