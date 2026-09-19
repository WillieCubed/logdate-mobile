#!/usr/bin/env bash
# Google REST calls using the gcloud login. Source after scripts/lib/common.sh.

# google_api METHOD URL [BODY_JSON] — prints the body; fails on HTTP >= 400.
#   GOOGLE_API_QUOTA_PROJECT  x-goog-user-project; Firebase requires it for user credentials
#   GOOGLE_API_TOKEN          use this token instead of the gcloud user's
google_api() {
    local method="$1" url="$2" body="${3:-}" bearer response status
    bearer="${GOOGLE_API_TOKEN:-}"
    if [[ -z "$bearer" ]]; then
        bearer="$(gcloud auth print-access-token 2>/dev/null)" \
            || { log_error "gcloud is not logged in; run: gcloud auth login"; return 1; }
    fi

    local args=(-sS -X "$method" -w '\n%{http_code}' -H "Content-Type: application/json")
    [[ -n "${GOOGLE_API_QUOTA_PROJECT:-}" ]] && args+=(-H "x-goog-user-project: $GOOGLE_API_QUOTA_PROJECT")
    [[ -n "$body" ]] && args+=(--data "$body")

    # Token via a curl config on stdin, not argv.
    response="$(printf 'header = "Authorization: Bearer %s"\n' "$bearer" | curl --config - "${args[@]}" "$url")" \
        || return 1
    status="${response##*$'\n'}"
    response="${response%$'\n'*}"
    if [[ "$status" -ge 400 ]]; then
        printf '%s' "$response" | jq -r '.error.message // .' >&2 2>/dev/null || printf '%s\n' "$response" >&2
        return 1
    fi
    printf '%s' "$response"
}

# impersonated_token EMAIL SCOPE — a short-lived token for a service account,
# issued to the gcloud user through Service Account Token Creator. No key.
impersonated_token() {
    local email="$1" scope="$2"
    google_api POST "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/$email:generateAccessToken" \
        "$(jq -cn --arg scope "$scope" '{scope: [$scope], lifetime: "600s"}')" \
        | jq -er '.accessToken'
}

# play_bundle_count EMAIL PACKAGE — number of uploaded bundles, read through a
# discarded edit. Fails when the account cannot access the app.
play_bundle_count() {
    local email="$1" package="$2" bearer edit count
    bearer="$(impersonated_token "$email" "$PLAY_SCOPE")" || return 1
    edit="$(GOOGLE_API_TOKEN="$bearer" google_api POST "$PLAY_API/applications/$package/edits" '{}')" || return 1
    edit="$(jq -r '.id' <<< "$edit")"
    count="$(GOOGLE_API_TOKEN="$bearer" google_api GET "$PLAY_API/applications/$package/edits/$edit/bundles" \
        | jq '.bundles // [] | length')" || count=""
    GOOGLE_API_TOKEN="$bearer" google_api DELETE "$PLAY_API/applications/$package/edits/$edit" >/dev/null || true
    [[ -n "$count" ]] || return 1
    printf '%s' "$count"
}

FIREBASE_API="https://firebase.googleapis.com/v1beta1"
PLAY_API="https://androidpublisher.googleapis.com/androidpublisher/v3"
PLAY_SCOPE="https://www.googleapis.com/auth/androidpublisher"

# Prints "packageName appId" per Android app in a Firebase project.
firebase_android_apps() {
    local project="$1"
    GOOGLE_API_QUOTA_PROJECT="$project" google_api GET "$FIREBASE_API/projects/$project/androidApps?pageSize=100" \
        | jq -r '.apps[]? | "\(.packageName) \(.appId)"'
}

# Writes the project's google-services.json (every Android app in it) to FILE.
firebase_android_config() {
    local project="$1" app_id="$2" file="$3" contents
    contents="$(GOOGLE_API_QUOTA_PROJECT="$project" google_api GET "$FIREBASE_API/projects/$project/androidApps/$app_id/config")" \
        || return 1
    mkdir -p "$(dirname "$file")"
    printf '%s' "$contents" | jq -r '.configFileContents' | base64 --decode > "$file"
}
