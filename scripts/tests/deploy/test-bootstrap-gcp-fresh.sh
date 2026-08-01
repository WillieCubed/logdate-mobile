#!/usr/bin/env bash
# Smoke test for the turnkey GCP bootstrap command using fake cloud binaries.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

TMP_DIR="$(mktemp -d)"
INSTANCE_PROJECT="logdate-bootstrap-test"
INSTANCE_DIR="$REPO_ROOT/.logdate/deploy/${INSTANCE_PROJECT}"
INTERACTIVE_PROJECT="logdate-interactive-test"
INTERACTIVE_INSTANCE_DIR="$REPO_ROOT/.logdate/deploy/${INTERACTIVE_PROJECT}"
CURRENT_PROJECT_ID="existing-current-project"
CURRENT_INSTANCE_DIR="$REPO_ROOT/.logdate/deploy/${CURRENT_PROJECT_ID}"
EXISTING_TEAM_PROJECT_ID="existing-team-project"
EXISTING_TEAM_INSTANCE_DIR="$REPO_ROOT/.logdate/deploy/${EXISTING_TEAM_PROJECT_ID}"
FAKE_BIN="$TMP_DIR/bin"
LOG_DIR="$TMP_DIR/logs"
OUTPUT_FILE="$TMP_DIR/bootstrap.out"
INTERACTIVE_LOG_DIR="$TMP_DIR/logs-interactive"
INTERACTIVE_OUTPUT_FILE="$TMP_DIR/bootstrap-interactive.out"
DATABASE_PASSWORD_SENTINEL="DATABASE_PASSWORD_SENTINEL_DO_NOT_LEAK"

cleanup() {
    rm -rf "$TMP_DIR"
    rm -rf "$INSTANCE_DIR"
    rm -rf "$INTERACTIVE_INSTANCE_DIR"
    rm -rf "$CURRENT_INSTANCE_DIR"
    rm -rf "$EXISTING_TEAM_INSTANCE_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN" "$LOG_DIR" "$INTERACTIVE_LOG_DIR"
rm -rf "$INSTANCE_DIR"
rm -rf "$INTERACTIVE_INSTANCE_DIR"
rm -rf "$CURRENT_INSTANCE_DIR"
rm -rf "$EXISTING_TEAM_INSTANCE_DIR"

reset_fake_state() {
    local dir="$1"
    rm -f "$dir"/*
}

pass_count=0

assert_equals() {
    local expected="$1"
    local actual="$2"
    if [[ "$expected" != "$actual" ]]; then
        echo "FAIL: expected '$expected', got '$actual'"
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

assert_contains() {
    local needle="$1"
    local text="$2"
    if ! echo "$text" | grep -q -- "$needle"; then
        echo "FAIL: expected output to contain '$needle'"
        echo "$text"
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

assert_not_contains() {
    local needle="$1"
    local text="$2"
    if echo "$text" | grep -q -- "$needle"; then
        echo "FAIL: expected output to not contain '$needle'"
        echo "$text"
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

cat >"$FAKE_BIN/gcloud" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
echo "$*" >>"$LOG_DIR/gcloud.log"

in_csv() {
    local needle="$1"
    case ",${2:-}," in
        *,"$needle",*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

flag_value() {
    local prefix="$1"
    shift
    local arg next_is_value="false"
    for arg in "$@"; do
        if [[ "$next_is_value" == "true" ]]; then
            printf '%s' "$arg"
            return
        fi
        if [[ "$arg" == "$prefix"* ]]; then
            printf '%s' "${arg#"$prefix"}"
            return
        fi
        if [[ "$arg" == "${prefix%=}" ]]; then
            next_is_value="true"
        fi
    done
}

project_exists() {
    local project_id="$1"
    if [[ -f "$LOG_DIR/project-$project_id" ]]; then
        return 0
    fi
    in_csv "$project_id" "${GCLOUD_EXISTING_PROJECTS:-}"
}

billing_account_for_project() {
    local project_id="$1"
    if [[ -f "$LOG_DIR/project-billing-$project_id" ]]; then
        cat "$LOG_DIR/project-billing-$project_id"
        return 0
    fi
    case "$project_id" in
        existing-current-project|existing-team-project)
            printf 'XYZ987'
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

case "$1 $2" in
    "auth print-access-token")
        echo "fake-token"
        ;;
    "config get-value")
        if [[ "$3" == "project" ]]; then
            printf '%s\n' "${GCLOUD_CURRENT_PROJECT:-"(unset)"}"
            exit 0
        fi
        ;;
    "billing accounts")
        printf 'billingAccounts/ABC123\tPersonal Billing\nbillingAccounts/XYZ987\tTeam Billing\n'
        ;;
    "billing projects")
        if [[ "$3" == "describe" ]]; then
            if billing_account="$(billing_account_for_project "$4")"; then
                printf 'True\tbillingAccounts/%s\n' "$billing_account"
            else
                printf 'False\t\n'
            fi
            exit 0
        fi
        if [[ "$3" == "link" ]]; then
            project_id="$4"
            account_id="$(flag_value "--billing-account=" "$@")"
            printf '%s' "${account_id#billingAccounts/}" >"$LOG_DIR/project-billing-$project_id"
            exit 0
        fi
        ;;
    "projects describe")
        if project_exists "$3"; then
            echo "{\"projectId\":\"$3\"}"
            exit 0
        fi
        exit 1
        ;;
    "projects create")
        touch "$LOG_DIR/project-$3"
        ;;
    "beta billing")
        ;;
    "projects list")
        printf 'existing-current-project\tExisting Current Project\nexisting-team-project\tExisting Team Project\n'
        ;;
    "config set")
        ;;
    "services enable")
        ;;
    "storage buckets")
        if [[ "$3" == "describe" ]]; then
            bucket_name="${4#gs://}"
            [[ -f "$LOG_DIR/bucket-$bucket_name" ]] && exit 0
            exit 1
        fi
        if [[ "$3" == "create" ]]; then
            bucket_name="${4#gs://}"
            touch "$LOG_DIR/bucket-$bucket_name"
            exit 0
        fi
        ;;
    "secrets versions")
        if [[ "$3" == "access" ]]; then
            secret_name="$(flag_value "--secret=" "$@")"
            [[ -f "$LOG_DIR/secret-$secret_name" ]] && cat "$LOG_DIR/secret-$secret_name"
            exit 0
        fi
        if [[ "$3" == "add" ]]; then
            secret_name="$4"
            data_file="$(flag_value "--data-file=" "$@")"
            [[ -n "$data_file" && -f "$data_file" ]] || exit 1
            data_mode="$(stat -f '%Lp' "$data_file" 2>/dev/null || stat -c '%a' "$data_file")"
            [[ "$data_mode" == "600" ]] || exit 1
            cp "$data_file" "$LOG_DIR/secret-$secret_name"
            printf '1' >"$LOG_DIR/secret-version-$secret_name"
            echo "Created version [1] of the secret [$secret_name]."
            exit 0
        fi
        if [[ "$3" == "describe" ]]; then
            secret_name="$(flag_value "--secret=" "$@")"
            [[ -f "$LOG_DIR/secret-version-$secret_name" ]] || exit 1
            printf 'projects/123456/secrets/%s/versions/%s\n' "$secret_name" "$(cat "$LOG_DIR/secret-version-$secret_name")"
            exit 0
        fi
        ;;
    "sql users")
        if [[ "$3" == "list" ]]; then
            if [[ -f "$LOG_DIR/sql-user-logdate" ]]; then
                echo "logdate"
            fi
            exit 0
        fi
        if [[ "$3" == "create" ]]; then
            touch "$LOG_DIR/sql-user-$4"
            exit 0
        fi
        if [[ "$3" == "set-password" ]]; then
            touch "$LOG_DIR/sql-user-password-reset-$4"
            exit 0
        fi
        ;;
    "auth configure-docker")
        ;;
    "run deploy")
        touch "$LOG_DIR/cloud-run-deployed"
        ;;
    "run services")
        if [[ "$3" == "describe" ]]; then
            echo "https://logdate-server-us-central1-test.a.run.app"
            exit 0
        fi
        if [[ "$3" == "update" ]]; then
            touch "$LOG_DIR/cloud-run-updated"
            exit 0
        fi
        ;;
esac
EOF
chmod +x "$FAKE_BIN/gcloud"

cat >"$FAKE_BIN/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
echo "$*" >>"$LOG_DIR/terraform.log"

args=("$@")
index=0
if [[ "${args[0]:-}" == -chdir=* ]]; then
    index=1
fi

command="${args[$index]}"
case "$command" in
    init|apply)
        exit 0
        ;;
    output)
        name="${args[$((index + 2))]}"
        case "$name" in
            workload_identity_provider)
                echo "projects/123456/locations/global/workloadIdentityPools/logdate-github-pool/providers/github-provider"
                ;;
            github_deploy_service_account_email)
                echo "github-deploy@logdate-bootstrap-test.iam.gserviceaccount.com"
                ;;
            runtime_service_account_email)
                echo "logdate-runtime@logdate-bootstrap-test.iam.gserviceaccount.com"
                ;;
            *)
                exit 1
                ;;
        esac
        ;;
    console)
        query="$(cat)"
        case "$query" in
            "var.project_id")
                echo '"logdate-bootstrap-test"'
                ;;
            "var.region")
                echo '"us-central1"'
                ;;
            "var.service_name")
                echo '"logdate-server"'
                ;;
            "var.domain")
                echo '""'
                ;;
            "var.artifact_registry_repo")
                echo '"logdate"'
                ;;
            "var.runtime_service_account_name")
                echo '"logdate-runtime"'
                ;;
            "var.gcs_bucket_name")
                echo '"logdate-bootstrap-test-media"'
                ;;
            "var.webauthn_rp_id")
                echo '""'
                ;;
            "var.webauthn_origin")
                echo '""'
                ;;
            "var.allow_unauthenticated")
                echo 'true'
                ;;
            "var.min_instances")
                echo '0'
                ;;
            "var.max_instances")
                echo '10'
                ;;
            "var.memory")
                echo '"512Mi"'
                ;;
            "var.cpu")
                echo '"1"'
                ;;
            "var.timeout_seconds")
                echo '60'
                ;;
            'join("\n", [for k, v in var.cloud_run_env : "${k}=${v}"])')
                cat <<'ENVEOF'
AUTO_MIGRATE=true
LOGDATE_DEPLOYMENT_KIND=first_party
LOGDATE_SERVER_DISPLAY_NAME=LogDate Cloud
WEBAUTHN_STRICT_VERIFICATION=true
SERVER_ENCRYPTION_ENABLED=true
SYNC_MEDIA_SIGNED_URLS=true
SYNC_MEDIA_SIGNED_URL_TTL_HOURS=1
INSTANCE_CONNECTION_NAME=logdate-bootstrap-test:us-central1:logdate-db
DB_NAME=logdate
ENVEOF
                ;;
            'join("\n", [for k, v in var.cloud_run_secret_env : "${k}=${v.secret_id}:${try(v.version, "latest")}"])')
                cat <<'SECRETEOF'
DATABASE_USER=logdate-db-user:1
DATABASE_PASSWORD=logdate-db-password:1
JWT_SECRET=logdate-jwt-secret:latest
SERVER_ENCRYPTION_KEY=logdate-server-encryption-key:latest
SERVER_ENCRYPTION_KEY_ID=logdate-server-encryption-key-id:latest
SECRETEOF
                ;;
            *)
                echo "Unexpected terraform console query: $query" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        echo "Unexpected terraform command: $*" >&2
        exit 1
        ;;
esac
EOF
chmod +x "$FAKE_BIN/terraform"

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
LOG_DIR="${TEST_LOG_DIR:?}"
echo "$*" >>"$LOG_DIR/docker.log"
exit 0
EOF
chmod +x "$FAKE_BIN/docker"

cat >"$FAKE_BIN/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
LOG_DIR="${TEST_LOG_DIR:?}"
echo "$*" >>"$LOG_DIR/gh.log"

case "$1 $2" in
    "auth status")
        exit 0
        ;;
    "secret set")
        if [[ "${GH_FORCE_FAIL:-false}" == "true" ]]; then
            exit 1
        fi
        printf 'secret %s=%s\n' "$3" "$7" >>"$LOG_DIR/gh-values.log"
        ;;
    "variable set")
        if [[ "${GH_FORCE_FAIL:-false}" == "true" ]]; then
            exit 1
        fi
        printf 'variable %s=%s\n' "$3" "$7" >>"$LOG_DIR/gh-values.log"
        ;;
    *)
        exit 1
        ;;
esac
EOF
chmod +x "$FAKE_BIN/gh"

cat >"$FAKE_BIN/openssl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == "rand -hex 24" ]]; then
    printf '%s\n' "${DATABASE_PASSWORD_SENTINEL:-DATABASE_PASSWORD_SENTINEL_DO_NOT_LEAK}"
else
    printf 'safe-non-database-secret\n'
fi
EOF
chmod +x "$FAKE_BIN/openssl"

cat >"$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
LOG_DIR="${TEST_LOG_DIR:?}"
printf '%s\n' "$*" >>"$LOG_DIR/curl.log"
if [[ "$*" == *"/health"* ]]; then
    exit 0
fi

flag_value() {
    local flag="$1"
    shift
    local next="false" arg
    for arg in "$@"; do
        if [[ "$next" == "true" ]]; then
            printf '%s' "$arg"
            return
        fi
        if [[ "$arg" == "$flag" ]]; then
            next="true"
        fi
    done
}

url="$(flag_value --url "$@")"
method="$(flag_value --request "$@")"
header_arg="$(flag_value --header "$@")"
body_arg="$(flag_value --data-binary "$@")"
output_file="$(flag_value --output "$@")"
header_file="${header_arg#@}"
body_file="${body_arg#@}"

if [[ "$url" == https://sqladmin.googleapis.com/sql/v1beta4/projects/*/instances/*/users* ]]; then
    [[ -f "$header_file" && -f "$body_file" ]] || exit 1
    header_mode="$(stat -f '%Lp' "$header_file" 2>/dev/null || stat -c '%a' "$header_file")"
    body_mode="$(stat -f '%Lp' "$body_file" 2>/dev/null || stat -c '%a' "$body_file")"
    [[ "$header_mode" == "600" && "$body_mode" == "600" ]] || exit 1
    printf '%s\n%s\n%s\n' "$header_file" "$body_file" "$(dirname "$header_file")/cloud-sql-access-token" >>"$LOG_DIR/sensitive-paths"
    grep -q '"name": "logdate"' "$body_file"
    grep -q "${DATABASE_PASSWORD_SENTINEL:-DATABASE_PASSWORD_SENTINEL_DO_NOT_LEAK}" "$body_file"
    printf '{}\n' >"$output_file"
    if [[ "$method" == "PUT" && ! -f "$LOG_DIR/cloud-sql-user" ]]; then
        printf '404'
        exit 0
    fi
    if [[ "$method" == "POST" ]]; then
        touch "$LOG_DIR/cloud-sql-user"
        printf '201'
        exit 0
    fi
    if [[ "$method" == "PUT" ]]; then
        printf '200'
        exit 0
    fi
fi

echo "Unexpected curl invocation: $*" >&2
exit 1
EOF
chmod +x "$FAKE_BIN/curl"

set +e
PATH="$FAKE_BIN:$PATH" ./scripts/bootstrap-gcp-fresh.sh --help >"$OUTPUT_FILE" 2>&1
status=$?
set -e

output="$(cat "$OUTPUT_FILE")"
if [[ "$status" != "0" ]]; then
    echo "$output"
fi
assert_equals "0" "$status"
assert_contains 'Usage:' "$output"
assert_contains '--billing-account ID' "$output"
assert_contains 'Lists open billing accounts and lets you pick by number' "$output"
assert_contains 'Lets you select an existing GCP project or create a new one' "$output"

reset_fake_state "$LOG_DIR"
set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LOG_DIR" \
GCLOUD_CURRENT_PROJECT="existing-current-project" \
GCLOUD_EXISTING_PROJECTS="existing-current-project,existing-team-project" \
GITHUB_REPO="acme/logdate" \
REGION="us-central1" \
IMAGE_TAG="bootstrap-test" \
USER="tester" \
./scripts/bootstrap-gcp-fresh.sh --yes >"$OUTPUT_FILE" 2>&1
status=$?
set -e

output="$(cat "$OUTPUT_FILE")"
assert_equals "0" "$status"
assert_contains 'Using current gcloud project: existing-current-project' "$output"
assert_contains 'Reusing billing already attached to project: Team Billing (XYZ987)' "$output"
assert_contains 'Keeping existing billing attachment on project.' "$output"
assert_contains '"projectId": "existing-current-project"' "$(cat "$CURRENT_INSTANCE_DIR/instance-manifest.json")"
assert_not_contains 'billing projects link existing-current-project' "$(cat "$LOG_DIR/gcloud.log")"

rm -rf "$CURRENT_INSTANCE_DIR"
reset_fake_state "$LOG_DIR"

set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LOG_DIR" \
DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
GITHUB_REPO="acme/logdate" \
INSTANCE_NAME="$INSTANCE_PROJECT" \
PROJECT_ID="$INSTANCE_PROJECT" \
BILLING_ACCOUNT="ABC123" \
REGION="us-central1" \
IMAGE_TAG="bootstrap-test" \
USER="tester" \
bash -x ./scripts/bootstrap-gcp-fresh.sh --yes >"$OUTPUT_FILE" 2>&1
status=$?
set -e

output="$(cat "$OUTPUT_FILE")"
if [[ "$status" != "0" ]]; then
    echo "$output"
fi
assert_equals "0" "$status"
assert_contains "Bootstrap complete." "$output"
assert_contains "Service URL:  https://logdate-server-us-central1-test.a.run.app" "$output"
assert_contains '"create_cloud_sql_instance": true' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_contains '"serviceUrl": "https://logdate-server-us-central1-test.a.run.app"' "$(cat "$INSTANCE_DIR/instance-manifest.json")"
assert_contains '"runtimeServiceAccount": "logdate-runtime@logdate-bootstrap-test.iam.gserviceaccount.com"' "$(cat "$INSTANCE_DIR/instance-manifest.json")"
assert_contains '"webauthn_origin": "https://logdate-server-us-central1-test.a.run.app"' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_contains '"INSTANCE_CONNECTION_NAME": "logdate-bootstrap-test:us-central1:logdate-db"' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_not_contains 'CLOUD_SQL_INSTANCE_CONNECTION_NAME' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_not_contains 'cloud_sql_user_name' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_contains '"version": "1"' "$(cat "$INSTANCE_DIR/bootstrap.tfvars.json")"
assert_contains '"environment": "bootstrap"' "$(cat "$INSTANCE_DIR/deployment-contract.json")"
assert_contains '"INSTANCE_CONNECTION_NAME": "logdate-bootstrap-test:us-central1:logdate-db"' "$(cat "$INSTANCE_DIR/deployment-contract.json")"
assert_contains '"DATABASE_PASSWORD": { "secret_id": "logdate-db-password", "version": "1" }' "$(cat "$INSTANCE_DIR/deployment-contract.json")"
assert_contains 'serviceusage.googleapis.com' "$(cat "$LOG_DIR/gcloud.log")"
assert_contains 'secrets versions add logdate-jwt-secret' "$(cat "$LOG_DIR/gcloud.log")"
assert_contains '/sql/v1beta4/projects/logdate-bootstrap-test/instances/logdate-db/users' "$(cat "$LOG_DIR/curl.log")"
assert_contains '--request POST' "$(cat "$LOG_DIR/curl.log")"
assert_not_contains 'sql users create' "$(cat "$LOG_DIR/gcloud.log")"
assert_not_contains 'sql users set-password' "$(cat "$LOG_DIR/gcloud.log")"
assert_contains 'secret GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123456/locations/global/workloadIdentityPools/logdate-github-pool/providers/github-provider' "$(cat "$LOG_DIR/gh-values.log")"
assert_contains 'variable LOGDATE_DEPLOY_SOURCE=repo_vars' "$(cat "$LOG_DIR/gh-values.log")"
assert_contains 'variable LOGDATE_PROJECT_ID=logdate-bootstrap-test' "$(cat "$LOG_DIR/gh-values.log")"
assert_contains 'variable LOGDATE_RUNTIME_SERVICE_ACCOUNT=logdate-runtime@logdate-bootstrap-test.iam.gserviceaccount.com' "$(cat "$LOG_DIR/gh-values.log")"
assert_contains 'buildx build' "$(cat "$LOG_DIR/docker.log")"
assert_contains 'run deploy logdate-server' "$(cat "$LOG_DIR/gcloud.log")"

security_evidence="$(cat "$OUTPUT_FILE" "$LOG_DIR"/*.log "$INSTANCE_DIR"/*.json "$INSTANCE_DIR"/*.hcl)"
assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$security_evidence"
assert_not_contains 'resource "google_sql_user"' "$(cat infra/terraform/main.tf)"
assert_contains 'roles/cloudsql.client' "$(cat infra/terraform/main.tf)"
assert_contains 'google_secret_manager_secret_iam_member" "github_migration_access' "$(cat infra/terraform/main.tf)"
while IFS= read -r sensitive_path; do
    [[ ! -e "$sensitive_path" ]] || {
        echo "FAIL: expected sensitive temporary file to be removed: $sensitive_path"
        exit 1
    }
    pass_count=$((pass_count + 1))
done <"$LOG_DIR/sensitive-paths"

secret_adds_before="$(grep -c 'secrets versions add' "$LOG_DIR/gcloud.log")"

set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LOG_DIR" \
GH_FORCE_FAIL="true" \
GITHUB_REPO="acme/logdate" \
INSTANCE_NAME="$INSTANCE_PROJECT" \
PROJECT_ID="$INSTANCE_PROJECT" \
BILLING_ACCOUNT="ABC123" \
REGION="us-central1" \
IMAGE_TAG="bootstrap-test" \
USER="tester" \
./scripts/bootstrap-gcp-fresh.sh --yes >"$OUTPUT_FILE" 2>&1
status=$?
set -e

output="$(cat "$OUTPUT_FILE")"
assert_equals "0" "$status"
assert_contains 'GitHub deploy wiring needs manual follow-up:' "$output"
assert_contains "gh secret set GCP_WORKLOAD_IDENTITY_PROVIDER --repo acme/logdate --body 'projects/123456/locations/global/workloadIdentityPools/logdate-github-pool/providers/github-provider'" "$output"
assert_contains "gh variable set LOGDATE_DEPLOY_SOURCE --repo acme/logdate --body 'repo_vars'" "$output"
secret_adds_after="$(grep -c 'secrets versions add' "$LOG_DIR/gcloud.log")"
assert_equals "$secret_adds_before" "$secret_adds_after"
assert_contains '--request PUT' "$(cat "$LOG_DIR/curl.log")"

reset_fake_state "$INTERACTIVE_LOG_DIR"
set +e
printf '2\ny\n' | PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$INTERACTIVE_LOG_DIR" \
GCLOUD_CURRENT_PROJECT="existing-current-project" \
GCLOUD_EXISTING_PROJECTS="existing-current-project,existing-team-project" \
GITHUB_REPO="acme/logdate" \
REGION="us-central1" \
IMAGE_TAG="bootstrap-test" \
USER="tester" \
./scripts/bootstrap-gcp-fresh.sh >"$INTERACTIVE_OUTPUT_FILE" 2>&1
status=$?
set -e

interactive_output="$(cat "$INTERACTIVE_OUTPUT_FILE")"
assert_equals "0" "$status"
assert_contains 'Select a GCP project:' "$interactive_output"
assert_contains '2) Existing Team Project (existing-team-project)' "$interactive_output"
assert_contains 'Bootstrap configuration:' "$interactive_output"
assert_contains "Project:             Existing Team Project (existing-team-project)" "$interactive_output"
assert_contains "Project mode:        Reuse existing project" "$interactive_output"
assert_contains "Billing account:     Reuse existing project billing (Team Billing (XYZ987))" "$interactive_output"
assert_not_contains 'billing projects link existing-team-project' "$(cat "$INTERACTIVE_LOG_DIR/gcloud.log")"
assert_contains '"projectId": "existing-team-project"' "$(cat "$EXISTING_TEAM_INSTANCE_DIR/instance-manifest.json")"

echo "Bootstrap deploy smoke test passed ($pass_count assertions)."
