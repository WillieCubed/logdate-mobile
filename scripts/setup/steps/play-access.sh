#!/usr/bin/env bash
# Play Console access for the service account. There is no API to grant it.

play_access_describe() {
    printf 'The service account can publish %s in Play Console' "$ANDROID_PACKAGE"
}

play_access_requires() {
    printf 'play-service-account'
}

play_access_check() {
    # shellcheck source=scripts/lib/google-api.sh
    source "$LOGDATE_REPO_ROOT/scripts/lib/google-api.sh"
    # shellcheck source=scripts/setup/steps/play-service-account.sh
    source "$SETUP_STEPS_DIR/play-service-account.sh"
    local reason
    if ! impersonated_token "$(play_service_account_email)" "$PLAY_SCOPE" >/dev/null 2>&1; then
        printf 'Cannot impersonate %s yet. New IAM grants take up to 7 minutes;\n' "$(play_service_account_email)"
        printf 're-check shortly.\n'
        return 2
    fi
    reason="$(play_bundle_count "$(play_service_account_email)" "$ANDROID_PACKAGE" 2>&1 >/dev/null)" && return 0

    printf 'Play refused the service account: %s\n' "$reason"
    printf 'In Play Console (https://play.google.com/console) open Users and permissions,\n'
    printf 'choose Invite new users, and enter:\n'
    printf '  %s\n' "$(play_service_account_email)"
    printf 'Under App permissions add LogDate with "Release to testing tracks" and\n'
    printf '"Release to production, exclude devices, and use Play App Signing", then\n'
    printf 'Invite user. Access can take a few minutes to take effect.\n'
    return 2
}
