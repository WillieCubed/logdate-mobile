#!/usr/bin/env bash
# The Play publishing service account, used without keys: GitHub Actions
# impersonates it through Workload Identity Federation, and setup's own Play
# checks impersonate it as the gcloud user.

play_service_account_describe() {
    printf 'Service account %s exists and GitHub Actions can impersonate it' "$(play_service_account_email)"
}

play_service_account_requires() {
    printf 'auth gh-environment'
}

play_service_account_email() {
    printf '%s@%s.iam.gserviceaccount.com' "$PLAY_SERVICE_ACCOUNT_NAME" "$GCP_PROJECT_ID"
}

play_service_account_github_member() {
    local number
    number="$(gcloud projects describe "$GCP_PROJECT_ID" --format='value(projectNumber)')" || return 1
    printf 'principalSet://iam.googleapis.com/projects/%s/locations/global/workloadIdentityPools/%s/attribute.repository/%s' \
        "$number" "$GITHUB_WIF_POOL" "$(repo_slug)"
}

play_service_account_user_member() {
    local account
    account="$(gcloud config get-value account 2>/dev/null)"
    if [[ "$account" == *.gserviceaccount.com ]]; then
        printf 'serviceAccount:%s' "$account"
    else
        printf 'user:%s' "$account"
    fi
}

# Prints "role member" per binding on the service account.
play_service_account_bindings() {
    gcloud iam service-accounts get-iam-policy "$(play_service_account_email)" --project "$GCP_PROJECT_ID" \
        --format=json | jq -r '.bindings[]? | .role as $role | .members[] | "\($role) \(.)"'
}

play_service_account_check() {
    local email bindings drift=0
    email="$(play_service_account_email)"

    if [[ -z "$(gcloud services list --enabled --project "$GCP_PROJECT_ID" \
        --filter=config.name=androidpublisher.googleapis.com --format='value(config.name)' 2>/dev/null)" ]]; then
        printf 'The Google Play Android Developer API is not enabled in %s\n' "$GCP_PROJECT_ID"
        drift=1
    fi
    if ! gcloud iam service-accounts describe "$email" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
        printf 'Service account %s does not exist\n' "$email"
        return 1
    fi

    bindings="$(play_service_account_bindings)"
    if ! grep -qxF "roles/iam.workloadIdentityUser $(play_service_account_github_member)" <<< "$bindings"; then
        printf 'GitHub Actions cannot impersonate %s\n' "$email"
        drift=1
    fi
    if ! grep -qxF "roles/iam.serviceAccountTokenCreator $(play_service_account_user_member)" <<< "$bindings"; then
        printf '%s cannot impersonate %s to verify Play access\n' "$(play_service_account_user_member)" "$email"
        drift=1
    fi
    if [[ "$(gh variable get LOGDATE_PLAY_SERVICE_ACCOUNT --env "$GITHUB_ENVIRONMENT" 2>/dev/null)" != "$email" ]]; then
        printf 'LOGDATE_PLAY_SERVICE_ACCOUNT is not set to %s in the %s environment\n' "$email" "$GITHUB_ENVIRONMENT"
        drift=1
    fi
    return "$drift"
}

play_service_account_apply() {
    local email
    email="$(play_service_account_email)"

    gcloud services enable androidpublisher.googleapis.com iamcredentials.googleapis.com --project "$GCP_PROJECT_ID"
    if ! gcloud iam service-accounts describe "$email" --project "$GCP_PROJECT_ID" >/dev/null 2>&1; then
        gcloud iam service-accounts create "$PLAY_SERVICE_ACCOUNT_NAME" --project "$GCP_PROJECT_ID" \
            --display-name "Google Play publisher (CI)"
    fi
    gcloud iam service-accounts add-iam-policy-binding "$email" --project "$GCP_PROJECT_ID" \
        --role roles/iam.workloadIdentityUser --member "$(play_service_account_github_member)" >/dev/null
    gcloud iam service-accounts add-iam-policy-binding "$email" --project "$GCP_PROJECT_ID" \
        --role roles/iam.serviceAccountTokenCreator --member "$(play_service_account_user_member)" >/dev/null
    gh variable set LOGDATE_PLAY_SERVICE_ACCOUNT --env "$GITHUB_ENVIRONMENT" --body "$email"
    log_info "GitHub Actions and $(play_service_account_user_member) can impersonate $email"
}
