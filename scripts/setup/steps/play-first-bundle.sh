#!/usr/bin/env bash
# Play accepts API uploads only once an app has a bundle, and the first must be
# uploaded by hand. Apply builds and verifies it.

play_first_bundle_describe() {
    printf 'Play has at least one %s bundle, so CI can publish the rest' "$ANDROID_PACKAGE"
}

play_first_bundle_requires() {
    # Not play-access: Play reports "Package not found" until the first bundle
    # exists, so this step must be able to run before access can be verified.
    printf 'play-service-account play-release-tag signing-keys'
}

play_first_bundle_check() {
    # shellcheck source=scripts/lib/google-api.sh
    source "$LOGDATE_REPO_ROOT/scripts/lib/google-api.sh"
    # shellcheck source=scripts/setup/steps/play-service-account.sh
    source "$SETUP_STEPS_DIR/play-service-account.sh"
    local count
    if ! count="$(play_bundle_count "$(play_service_account_email)" "$ANDROID_PACKAGE" 2>/dev/null)"; then
        printf 'Play cannot list %s bundles yet (no bundle uploaded, or no access)\n' "$ANDROID_PACKAGE"
        return 1
    fi
    [[ "$count" -gt 0 ]] && return 0
    printf 'Play has no bundles for %s yet\n' "$ANDROID_PACKAGE"
    return 1
}

play_first_bundle_signer() {
    keytool -printcert -jarfile "$1" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -1
}

play_first_bundle_apply() {
    local version version_name bundle expected actual
    # Never build a release with the stub Firebase config.
    # shellcheck source=scripts/setup/steps/firebase-configs.sh
    source "$SETUP_STEPS_DIR/firebase-configs.sh"
    firebase_configs_valid release "$(firebase_configs_file release)" || return 1

    # The bundle ships to testers, so build exactly what is on origin/main.
    git fetch --quiet origin main || return 1
    if [[ -n "$(git status --porcelain)" || "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
        printf 'The first bundle must be built from a clean checkout of origin/main.\n'
        printf 'Commit or stash local changes, check out origin/main, then re-run.\n'
        return 2
    fi

    version="$("$LOGDATE_REPO_ROOT/scripts/resolve-android-play-version.sh")" || return 1
    version_name="$(sed -n 's/^version_name=//p' <<< "$version")"

    # versionCode 1: CI uploads then take Play's highest code + 1.
    log_info "Building $ANDROID_PACKAGE $version_name (versionCode 1) against $BACKEND_URL"
    "$LOGDATE_REPO_ROOT/gradlew" -p "$LOGDATE_REPO_ROOT" :app:android-main:bundleRelease \
        "-Plogdate.backendUrl=$BACKEND_URL" \
        "-Plogdate.versionCode=1" \
        "-Plogdate.versionName=$version_name" || return 1

    bundle="$(find "$LOGDATE_REPO_ROOT/app/android-main/build/outputs/bundle/release" -name '*.aab' -print -quit)"
    [[ -n "$bundle" ]] || { log_error "The build produced no .aab"; return 1; }

    expected="$(jq -r '.expected_build_signer_fingerprint' \
        "$LOGDATE_REPO_ROOT/infra/android-signing/$SETUP_ENV-signer-evidence.json")"
    actual="$(play_first_bundle_signer "$bundle")"
    if [[ "$actual" != "$expected" ]]; then
        log_error "The bundle is signed by ${actual:-nothing readable}, but Play expects the upload key $expected."
        return 1
    fi
    log_info "Signed by the upload key $actual"

    printf 'Upload the first bundle by hand (Play requires it for a new app):\n'
    printf '  1. Play Console → LogDate → Test and release → Testing → Internal testing\n'
    printf '  2. Create new release, and upload:\n'
    printf '     %s\n' "$bundle"
    printf '  3. Add yourself under Testers, save, and roll out the release.\n'
    return 2
}
