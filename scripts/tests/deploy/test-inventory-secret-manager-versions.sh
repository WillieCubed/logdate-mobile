#!/usr/bin/env bash
# Regression checks for least-privilege Neon Secret Manager version inventory.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/tests/lib/assertions.sh
source "$REPO_ROOT/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/inventory-secret-manager-versions.sh"
WORKFLOW=".github/workflows/inventory-secret-manager-versions.yml"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
COMMAND_LOG="$TMP_DIR/gcloud.log"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT
mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/gcloud" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$GCLOUD_COMMAND_LOG"
project="${GCLOUD_FAKE_PROJECT:-logdate-dev}"
principal="${GCLOUD_FAKE_PRINCIPAL:-github-deploy@${project}.iam.gserviceaccount.com}"

case "$*" in
    "auth list --filter=status:ACTIVE --format=value(account) --quiet")
        printf '%s\n' "$principal"
        ;;
    "config get-value project --quiet")
        printf '%s\n' "$project"
        ;;
    "secrets versions list logdate-db-url --project $project"*)
        printf 'projects/%s/secrets/logdate-db-url/versions/10\nprojects/%s/secrets/logdate-db-url/versions/2\nprojects/%s/secrets/logdate-db-url/versions/1\n' \
            "${GCLOUD_VERSION_PROJECT:-$project}" "${GCLOUD_VERSION_PROJECT:-$project}" "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-db-user --project $project"*)
        printf 'projects/%s/secrets/logdate-db-user/versions/12\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-db-password --project $project"*)
        printf 'projects/%s/secrets/logdate-db-password/versions/3\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-jwt-secret --project $project"*)
        printf 'projects/%s/secrets/logdate-jwt-secret/versions/4\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-server-encryption-key --project $project"*)
        printf 'projects/%s/secrets/logdate-server-encryption-key/versions/5\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-server-encryption-key-id --project $project"*)
        printf 'projects/%s/secrets/logdate-server-encryption-key-id/versions/6\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        ;;
    "secrets versions list logdate-health-internal-token --project $project"*)
        if [[ "${GCLOUD_EMPTY_SECRET:-}" != "logdate-health-internal-token" ]]; then
            printf 'projects/%s/secrets/logdate-health-internal-token/versions/7\n' "${GCLOUD_VERSION_PROJECT:-$project}"
        fi
        ;;
    *)
        printf 'unexpected gcloud command: %s\n' "$*" >&2
        exit 97
        ;;
esac
EOF
chmod +x "$FAKE_BIN/gcloud"

run_inventory() {
    GCLOUD_COMMAND_LOG="$COMMAND_LOG" PATH="$FAKE_BIN:$PATH" "$SCRIPT" --environment "$1"
}

assert_file_exists "$SCRIPT"
assert_file_exists "$WORKFLOW"

staging_output="$(run_inventory staging)"
assert_contains 'environment=staging' "$staging_output"
assert_contains 'project_id=logdate-dev' "$staging_output"
assert_contains 'authenticated_principal=github-deploy@logdate-dev.iam.gserviceaccount.com' "$staging_output"
assert_contains 'secret_id=logdate-db-url' "$staging_output"
assert_contains 'secret_id=logdate-db-user' "$staging_output"
assert_contains 'secret_id=logdate-db-password' "$staging_output"
assert_contains 'secret_id=logdate-jwt-secret' "$staging_output"
assert_contains 'secret_id=logdate-server-encryption-key' "$staging_output"
assert_contains 'secret_id=logdate-server-encryption-key-id' "$staging_output"
assert_contains 'secret_id=logdate-health-internal-token' "$staging_output"
assert_contains 'enabled_version=1' "$staging_output"
assert_contains 'enabled_version=2' "$staging_output"
assert_contains 'enabled_version=10' "$staging_output"
assert_contains 'enabled_version=12' "$staging_output"
assert_contains 'enabled_version=3' "$staging_output"
assert_contains 'enabled_version=4' "$staging_output"
assert_contains 'enabled_version=5' "$staging_output"
assert_contains 'enabled_version=6' "$staging_output"
assert_contains 'enabled_version=7' "$staging_output"
assert_not_contains 'credential-value-must-not-print' "$staging_output"
assert_not_contains 'secret_id=unexpected-project-secret' "$staging_output"
actual_db_url_versions="$(awk '/^secret_id=logdate-db-url$/{capture=1; next} /^secret_id=/{capture=0} capture && /^enabled_version=/{print}' <<<"$staging_output")"
expected_db_url_versions=$'enabled_version=1\nenabled_version=2\nenabled_version=10'
[[ "$actual_db_url_versions" == "$expected_db_url_versions" ]] || fail 'enabled versions must be sorted numerically and deduplicated'
pass

production_output="$(GCLOUD_FAKE_PROJECT=logdate GCLOUD_COMMAND_LOG="$COMMAND_LOG" PATH="$FAKE_BIN:$PATH" "$SCRIPT" --environment production)"
assert_contains 'environment=production' "$production_output"
assert_contains 'project_id=logdate' "$production_output"
assert_contains 'authenticated_principal=github-deploy@logdate.iam.gserviceaccount.com' "$production_output"

assert_fails() {
    local description="$1"
    shift
    set +e
    local output
    output="$(GCLOUD_COMMAND_LOG="$COMMAND_LOG" PATH="$FAKE_BIN:$PATH" "$@" 2>&1)"
    local status=$?
    set -e
    assert_exit_code 1 "$status"
    assert_contains "$description" "$output"
    assert_not_contains 'credential-value-must-not-print' "$output"
}

assert_fails 'authenticated principal does not match' env GCLOUD_FAKE_PRINCIPAL='unexpected@example.invalid' "$SCRIPT" --environment staging
assert_fails 'active project does not match' env GCLOUD_FAKE_PROJECT=logdate GCLOUD_FAKE_PRINCIPAL=github-deploy@logdate-dev.iam.gserviceaccount.com "$SCRIPT" --environment staging
assert_fails 'invalid enabled version resource name' env GCLOUD_VERSION_PROJECT=logdate "$SCRIPT" --environment staging
assert_fails 'no enabled numeric version' env GCLOUD_EMPTY_SECRET=logdate-health-internal-token "$SCRIPT" --environment staging
assert_fails 'environment must be staging or production' "$SCRIPT" --environment unexpected

command_log="$(<"$COMMAND_LOG")"
assert_not_contains 'secrets list' "$command_log"
assert_not_contains 'get-iam-policy' "$command_log"
assert_not_contains 'versions access' "$command_log"
assert_not_contains 'secrets create' "$command_log"
assert_not_contains 'secrets delete' "$command_log"
assert_not_contains 'run deploy' "$command_log"
assert_not_contains 'run services' "$command_log"
assert_not_contains 'sql ' "$command_log"

assert_file_contains 'workflow_dispatch:' "$WORKFLOW"
# Literal GitHub Actions expression under test.
# shellcheck disable=SC2016
assert_file_contains "environment: "'${{ inputs.environment }}' "$WORKFLOW"
assert_file_contains 'id-token: write' "$WORKFLOW"
assert_file_contains 'google-github-actions/auth@v3' "$WORKFLOW"
assert_file_contains 'inventory-secret-manager-versions.sh' "$WORKFLOW"
assert_file_not_contains 'gcloud run' "$WORKFLOW"
assert_file_not_contains 'run-migrations.sh' "$WORKFLOW"
assert_file_not_contains 'secrets versions access' "$WORKFLOW"
assert_file_contains 'seven required server secrets' docs/runbook/secret-manager-version-inventory.md
assert_file_contains 'does not prove environment isolation' docs/runbook/secret-manager-version-inventory.md
assert_file_contains 'Viewer at the project level' docs/runbook/secret-manager-version-inventory.md
assert_file_contains 'never lists project secrets' docs/runbook/secret-manager-version-inventory.md
# Literal Markdown code spans under test.
# shellcheck disable=SC2016
assert_file_contains '| GitHub Environment | `staging` | `production` |' docs/runbook/staging-production-configuration.md

print_pass_summary 'least-privilege secret inventory checks'
