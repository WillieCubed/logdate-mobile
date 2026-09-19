#!/usr/bin/env bash
# A Firebase Android app per build package, with the environment's signing
# certificates registered on the main app.

firebase_apps_describe() {
    printf 'Firebase project %s has Android apps and signing certificates for %s' \
        "$FIREBASE_PROJECT_ID" "$(firebase_apps_packages | tr '\n' ' ' | sed 's/ $//')"
}

firebase_apps_requires() {
    printf 'auth'
}

firebase_apps_lib() {
    # shellcheck source=scripts/lib/google-api.sh
    source "$LOGDATE_REPO_ROOT/scripts/lib/google-api.sh"
}

firebase_apps_packages() {
    printf '%s\n' "$ANDROID_PACKAGE"
    [[ -n "${ANDROID_DEBUG_PACKAGE:-}" ]] && printf '%s\n' "$ANDROID_DEBUG_PACKAGE"
    return 0
}

# Lowercase hex without colons, the form Firebase stores, one per line, from
# the signer evidence of every environment in FIREBASE_SIGNERS.
firebase_apps_expected_shas() {
    local signers=() signer
    read -r -a signers <<< "${FIREBASE_SIGNERS:-}"
    for signer in ${signers[@]+"${signers[@]}"}; do
        jq -r '.certificates[].fingerprint' \
            "$LOGDATE_REPO_ROOT/infra/android-signing/$signer-signer-evidence.json"
    done | tr -d ':' | tr '[:upper:]' '[:lower:]' | sort
}

firebase_apps_registered_shas() {
    GOOGLE_API_QUOTA_PROJECT="$FIREBASE_PROJECT_ID" \
        google_api GET "$FIREBASE_API/projects/$FIREBASE_PROJECT_ID/androidApps/$FIREBASE_ANDROID_APP_ID/sha" \
        | jq -r '.certificates[]? | select(.certType == "SHA_256") | .shaHash' | sort
}

firebase_apps_check() {
    firebase_apps_lib
    local apps package missing_shas drift=0
    apps="$(firebase_android_apps "$FIREBASE_PROJECT_ID")" || return 1

    while IFS= read -r package; do
        grep -q "^$package " <<< "$apps" && continue
        printf 'No Firebase Android app for %s\n' "$package"
        drift=1
    done < <(firebase_apps_packages)

    if ! grep -qxF "$ANDROID_PACKAGE $FIREBASE_ANDROID_APP_ID" <<< "$apps"; then
        printf 'FIREBASE_ANDROID_APP_ID in scripts/setup/environments/%s.conf does not match the %s app:\n' \
            "$SETUP_ENV" "$ANDROID_PACKAGE"
        printf '  %s\n' "$(grep "^$ANDROID_PACKAGE " <<< "$apps" || printf 'no app yet')"
        [[ "$drift" -eq 1 ]] && return 1
        printf 'Update the file to the app id above and commit it.\n'
        return 2
    fi

    missing_shas="$(comm -23 <(firebase_apps_expected_shas) <(firebase_apps_registered_shas))"
    if [[ -n "$missing_shas" ]]; then
        printf 'Signing certificates missing from the %s app: %s\n' "$ANDROID_PACKAGE" "$(tr '\n' ' ' <<< "$missing_shas")"
        drift=1
    fi
    return "$drift"
}

firebase_apps_create() {
    local package="$1" body operation name
    body="$(jq -n --arg package "$package" --arg name "LogDate ($package)" '{packageName: $package, displayName: $name}')"
    operation="$(GOOGLE_API_QUOTA_PROJECT="$FIREBASE_PROJECT_ID" \
        google_api POST "$FIREBASE_API/projects/$FIREBASE_PROJECT_ID/androidApps" "$body")" || return 1
    name="$(jq -r '.name' <<< "$operation")"
    local attempt
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        [[ "$(GOOGLE_API_QUOTA_PROJECT="$FIREBASE_PROJECT_ID" google_api GET "$FIREBASE_API/$name" \
            | jq -r '.done // false')" == "true" ]] && {
            log_info "Created Firebase app for $package"
            return 0
        }
        sleep "$attempt"
    done
    log_error "Firebase did not finish creating the $package app; re-run to check."
    return 1
}

firebase_apps_apply() {
    firebase_apps_lib
    local apps package sha
    apps="$(firebase_android_apps "$FIREBASE_PROJECT_ID")" || return 1

    while IFS= read -r package; do
        grep -q "^$package " <<< "$apps" || firebase_apps_create "$package" || return 1
    done < <(firebase_apps_packages)

    grep -qxF "$ANDROID_PACKAGE $FIREBASE_ANDROID_APP_ID" <<< "$(firebase_android_apps "$FIREBASE_PROJECT_ID")" \
        || return 0 # check reports the id to record

    while IFS= read -r sha; do
        [[ -z "$sha" ]] && continue
        GOOGLE_API_QUOTA_PROJECT="$FIREBASE_PROJECT_ID" google_api POST \
            "$FIREBASE_API/projects/$FIREBASE_PROJECT_ID/androidApps/$FIREBASE_ANDROID_APP_ID/sha" \
            "$(jq -n --arg sha "$sha" '{shaHash: $sha, certType: "SHA_256"}')" >/dev/null \
            || return 1
        log_info "Registered certificate $sha"
    done < <(comm -23 <(firebase_apps_expected_shas) <(firebase_apps_registered_shas))
}
