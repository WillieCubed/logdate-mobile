#!/usr/bin/env bash
# Regression checks for GitHub Actions readability and release metadata.

set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

assert_file_missing .github/workflows/deploy-server.yml
workflow_files=()
while IFS= read -r workflow; do
    workflow_files+=("$workflow")
done < <(find .github/workflows -maxdepth 1 -type f -name '*.yml' | sort)
for workflow in "${workflow_files[@]}"; do
    assert_file_exists "$workflow"
    assert_file_contains "run-name:" "$workflow"
    assert_file_contains "permissions:" "$workflow"
    assert_file_not_contains "- uses:" "$workflow"
    assert_file_not_contains "Setup Gradle" "$workflow"
done

assert_file_contains "name: Deploy Server Staging" .github/workflows/deploy-server-staging.yml
assert_file_contains "name: Deploy Server Production" .github/workflows/deploy-server-production.yml
assert_file_contains "name: Deploy Server Cloud Run" .github/workflows/deploy-server-cloud-run.yml
assert_file_contains "Verify staging before production" .github/workflows/deploy-server-production.yml
assert_file_contains "actions: read" .github/workflows/deploy-server-production.yml
assert_file_contains "workflow_call:" .github/workflows/deploy-server-cloud-run.yml
assert_file_contains "Install passkey verifier dependencies" .github/workflows/deploy-server-cloud-run.yml
assert_file_contains 'scripts/smoke-test-revision.sh "$REVISION_URL" "${origin_arg[@]}"' .github/workflows/deploy-server-cloud-run.yml
assert_file_not_contains "Pre-tag staging smoke test" .github/workflows/deploy-server-production.yml
assert_file_contains "LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_RELEASE_BASE64" .github/workflows/publish-android-play.yml
assert_file_contains "android-flavor: release" .github/workflows/publish-android-play.yml
assert_file_contains "iosApp/iosApp/Firebase/GoogleService-Info-Release.plist" .github/actions/setup-firebase-configs/action.yml
assert_file_not_contains "target=\"iosApp/iosApp/GoogleService-Info.plist\"" .github/actions/setup-firebase-configs/action.yml

assert_file_exists docs/runbook/staging-production-configuration.md

print_pass_summary "github actions metadata"
