#!/usr/bin/env bash
# Real-behavior tests for deterministic, fail-closed Cloud Run contracts.

set -euo pipefail

# shellcheck source=scripts/tests/lib/assertions.sh
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/render-cloud-run-contract.sh"
RELEASE_SHA="0123456789abcdef0123456789abcdef01234567"
CREDENTIAL_SENTINEL="CREDENTIAL_SENTINEL_MUST_NOT_LEAK"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
FIXTURE_DIR="$TMP_DIR/fixtures"
LOG_DIR="$TMP_DIR/logs"
REAL_JQ="$(command -v jq)"
CHECKOUT_STATE="infra/terraform/terraform.tfstate"
CHECKOUT_STATE_CHECKSUM=""
STAGING_TFVARS_CHECKSUM="$(shasum -a 256 infra/terraform/staging.tfvars | awk '{print $1}')"
PRODUCTION_TFVARS_CHECKSUM="$(shasum -a 256 infra/terraform/production.tfvars | awk '{print $1}')"
LOCKFILE_CHECKSUM="$(shasum -a 256 infra/terraform/.terraform.lock.hcl | awk '{print $1}')"
CHECKOUT_PLUGIN_CANARY="infra/terraform/.terraform/task-1-renderer-canary"
CHECKOUT_WORKSPACE_CANARY="infra/terraform/terraform.tfstate.d/production/task-1-renderer-canary"
CHECKOUT_TFVARS_CANARY="infra/terraform/terraform.tfvars"
CHECKOUT_AUTO_TFVARS_CANARY="infra/terraform/task-1-poison.auto.tfvars"
WORKSPACE_PARENT_EXISTED="false"
PLUGIN_PARENT_EXISTED="false"
CREATED_CHECKOUT_STATE="false"
CREATED_CHECKOUT_PLUGIN_CANARY="false"
CREATED_CHECKOUT_WORKSPACE_CANARY="false"
CREATED_CHECKOUT_TFVARS_CANARY="false"
CREATED_CHECKOUT_AUTO_TFVARS_CANARY="false"

cleanup() {
    if [[ "$CREATED_CHECKOUT_PLUGIN_CANARY" == "true" ]]; then
        rm -f "$CHECKOUT_PLUGIN_CANARY"
    fi
    if [[ "$CREATED_CHECKOUT_WORKSPACE_CANARY" == "true" ]]; then
        rm -f "$CHECKOUT_WORKSPACE_CANARY"
    fi
    if [[ "$CREATED_CHECKOUT_TFVARS_CANARY" == "true" ]]; then
        rm -f "$CHECKOUT_TFVARS_CANARY"
    fi
    if [[ "$CREATED_CHECKOUT_AUTO_TFVARS_CANARY" == "true" ]]; then
        rm -f "$CHECKOUT_AUTO_TFVARS_CANARY"
    fi
    if [[ "$CREATED_CHECKOUT_STATE" == "true" ]]; then
        rm -f "$CHECKOUT_STATE"
    fi
    if [[ "$WORKSPACE_PARENT_EXISTED" == "false" ]]; then
        rmdir "$(dirname "$CHECKOUT_WORKSPACE_CANARY")" 2>/dev/null || true
        rmdir "$(dirname "$(dirname "$CHECKOUT_WORKSPACE_CANARY")")" 2>/dev/null || true
    fi
    if [[ "$PLUGIN_PARENT_EXISTED" == "false" ]]; then
        rmdir "$(dirname "$CHECKOUT_PLUGIN_CANARY")" 2>/dev/null || true
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN" "$FIXTURE_DIR" "$LOG_DIR"
[[ ! -e "$CHECKOUT_TFVARS_CANARY" ]] || fail "$CHECKOUT_TFVARS_CANARY already exists; refusing to overwrite an operator file"
[[ ! -e "$CHECKOUT_AUTO_TFVARS_CANARY" ]] || fail "$CHECKOUT_AUTO_TFVARS_CANARY already exists; refusing to overwrite an operator file"
[[ ! -e "$CHECKOUT_PLUGIN_CANARY" ]] || fail "$CHECKOUT_PLUGIN_CANARY already exists; refusing to overwrite an operator file"
[[ ! -e "$CHECKOUT_WORKSPACE_CANARY" ]] || fail "$CHECKOUT_WORKSPACE_CANARY already exists; refusing to overwrite an operator file"
if [[ ! -e "$CHECKOUT_STATE" ]]; then
    printf 'checkout-state-canary\n' >"$CHECKOUT_STATE"
    CREATED_CHECKOUT_STATE="true"
fi
CHECKOUT_STATE_CHECKSUM="$(shasum -a 256 "$CHECKOUT_STATE" | awk '{print $1}')"
if [[ -d "$(dirname "$CHECKOUT_WORKSPACE_CANARY")" ]]; then
    WORKSPACE_PARENT_EXISTED="true"
fi
if [[ -d "$(dirname "$CHECKOUT_PLUGIN_CANARY")" ]]; then
    PLUGIN_PARENT_EXISTED="true"
fi
mkdir -p "$(dirname "$CHECKOUT_PLUGIN_CANARY")" "$(dirname "$CHECKOUT_WORKSPACE_CANARY")"
printf 'checkout-plugin-canary\n' >"$CHECKOUT_PLUGIN_CANARY"
CREATED_CHECKOUT_PLUGIN_CANARY="true"
printf 'checkout-workspace-canary\n' >"$CHECKOUT_WORKSPACE_CANARY"
CREATED_CHECKOUT_WORKSPACE_CANARY="true"
printf 'android_signing_certificates = "checkout-poison"\n' >"$CHECKOUT_TFVARS_CANARY"
CREATED_CHECKOUT_TFVARS_CANARY="true"
printf 'allow_unauthenticated = false\n' >"$CHECKOUT_AUTO_TFVARS_CANARY"
CREATED_CHECKOUT_AUTO_TFVARS_CANARY="true"

assert_equals() {
    local expected="$1" actual="$2"
    [[ "$expected" == "$actual" ]] || fail "expected '$expected', got '$actual'"
    pass
}

cat >"$FIXTURE_DIR/staging-source.json" <<'EOF'
{
  "project_id": "logdate-dev",
  "region": "us-central1",
  "service_name": "logdate-server-staging",
  "cloud_run_image": "us-central1-docker.pkg.dev/logdate-dev/logdate/logdate-server:latest",
  "runtime_service_account_name": "logdate-runtime",
  "artifact_registry_repo": "logdate",
  "domains": ["cloud-staging.logdate.app"],
  "domain": "",
  "android_signing_certificates": {
    "staging": {
      "fingerprint": "E1:6A:82:07:74:DE:F6:29:24:EB:E1:48:67:47:8C:72:9C:69:A0:CB:9D:01:8A:8C:E4:49:44:DA:00:15:E9:5A",
      "apk_key_hash_origin": "android:apk-key-hash:4WqCB3Te9ikk6-FIZ0eMcpxpoMudAYqM5ElE2gAV6Vo"
    }
  },
  "env_vars": {
    "HOST": "0.0.0.0",
    "GCS_PROJECT_ID": "logdate-dev",
    "GCS_BUCKET_NAME": "logdate-media-staging",
    "LOGDATE_ENV": "production",
    "LOGDATE_EXPECT_FIRST_PARTY": "true",
    "LOGDATE_DEPLOYMENT_KIND": "first_party",
    "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud (Staging)",
    "LOGDATE_PUBLIC_ORIGIN": "https://cloud-staging.logdate.app",
    "ATPROTO_PDS_SERVICE_URL": "https://cloud-staging.logdate.app",
    "ATPROTO_HANDLE_DOMAIN": "cloud-staging.logdate.app",
    "WEBAUTHN_RP_ID": "cloud-staging.logdate.app",
    "WEBAUTHN_ORIGIN": "https://cloud-staging.logdate.app",
    "BILLING_PROVIDER": "play",
    "SERVER_ENCRYPTION_ENABLED": "true",
    "SYNC_MEDIA_SIGNED_URLS": "true",
    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
    "AUTO_MIGRATE": "false",
    "INSTANCE_CONNECTION_NAME": "logdate-dev:us-central1:logdate-db",
    "DB_NAME": "logdate"
  },
  "secret_env": {
    "DATABASE_USER": {"secret_id": "logdate-db-user", "version": "7"},
    "DATABASE_PASSWORD": {"secret_id": "logdate-db-password", "version": "11"},
    "JWT_SECRET": {"secret_id": "logdate-jwt-secret", "version": "3"},
    "SERVER_ENCRYPTION_KEY": {"secret_id": "logdate-server-encryption-key", "version": "5"},
    "SERVER_ENCRYPTION_KEY_ID": {"secret_id": "logdate-server-encryption-key-id", "version": "2"},
    "HEALTH_INTERNAL_TOKEN": {"secret_id": "logdate-health-internal-token", "version": "13"}
  },
  "runtime": {
    "allow_unauthenticated": true,
    "ingress": "INGRESS_TRAFFIC_ALL",
    "port": 8080,
    "scaling": {"min_instances": 0, "max_instances": 10},
    "resources": {"cpu": "1", "memory": "512Mi", "cpu_idle": true, "startup_cpu_boost": true},
    "timeout_seconds": 60,
    "request_concurrency": 16,
    "startup_probe": {"path": "/health", "port": 8080, "timeout_seconds": 5, "period_seconds": 5, "failure_threshold": 12},
    "liveness_probe": {"path": "/health", "port": 8080, "initial_delay_seconds": 15, "timeout_seconds": 5, "period_seconds": 30, "failure_threshold": 3}
  }
}
EOF

cat >"$FIXTURE_DIR/production-source.json" <<'EOF'
{
  "project_id": "logdate",
  "region": "us-central1",
  "service_name": "logdate-server",
  "cloud_run_image": "us-docker.pkg.dev/cloudrun/container/hello",
  "runtime_service_account_name": "logdate-runtime",
  "artifact_registry_repo": "logdate",
  "domains": ["cloud.logdate.app"],
  "domain": "",
  "android_signing_certificates": {
    "upload": {
      "fingerprint": "11:98:70:B8:78:F3:AB:5F:55:C0:DF:65:C7:87:89:C0:24:59:CA:9F:F3:22:A0:89:40:AE:43:A2:9D:1D:D5:AB",
      "apk_key_hash_origin": "android:apk-key-hash:EZhwuHjzq19VwN9lx4eJwCRZyp_zIqCJQK5Dop0d1as"
    },
    "play_app_signing": {
      "fingerprint": "F1:3E:F5:D0:EC:93:ED:B0:8C:6C:F2:1D:8A:12:84:99:42:C2:92:D8:ED:EC:26:C0:E4:46:0C:3C:71:BC:6E:5F",
      "apk_key_hash_origin": "android:apk-key-hash:8T710OyT7bCMbPIdihKEmULCktjt7CbA5EYMPHG8bl8"
    }
  },
  "env_vars": {
    "HOST": "0.0.0.0",
    "GCS_PROJECT_ID": "logdate",
    "GCS_BUCKET_NAME": "logdate-media-logdate",
    "LOGDATE_ENV": "production",
    "LOGDATE_EXPECT_FIRST_PARTY": "true",
    "LOGDATE_DEPLOYMENT_KIND": "first_party",
    "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud",
    "LOGDATE_PUBLIC_ORIGIN": "https://cloud.logdate.app",
    "ATPROTO_PDS_SERVICE_URL": "https://cloud.logdate.app",
    "ATPROTO_HANDLE_DOMAIN": "logdate.app",
    "WEBAUTHN_RP_ID": "logdate.app",
    "WEBAUTHN_ORIGIN": "https://cloud.logdate.app",
    "BILLING_PROVIDER": "play",
    "SERVER_ENCRYPTION_ENABLED": "true",
    "SYNC_MEDIA_SIGNED_URLS": "true",
    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
    "AUTO_MIGRATE": "false"
  },
  "secret_env": {
    "DATABASE_URL": {"secret_id": "logdate-db-url", "version": "17"},
    "DATABASE_USER": {"secret_id": "logdate-db-user", "version": "7"},
    "DATABASE_PASSWORD": {"secret_id": "logdate-db-password", "version": "11"},
    "JWT_SECRET": {"secret_id": "logdate-jwt-secret", "version": "3"},
    "SERVER_ENCRYPTION_KEY": {"secret_id": "logdate-server-encryption-key", "version": "5"},
    "SERVER_ENCRYPTION_KEY_ID": {"secret_id": "logdate-server-encryption-key-id", "version": "2"},
    "HEALTH_INTERNAL_TOKEN": {"secret_id": "logdate-health-internal-token", "version": "13"}
  },
  "runtime": {
    "allow_unauthenticated": true,
    "ingress": "INGRESS_TRAFFIC_ALL",
    "port": 8080,
    "scaling": {"min_instances": 1, "max_instances": 10},
    "resources": {"cpu": "1", "memory": "512Mi", "cpu_idle": true, "startup_cpu_boost": true},
    "timeout_seconds": 60,
    "request_concurrency": 16,
    "startup_probe": {"path": "/health", "port": 8080, "timeout_seconds": 5, "period_seconds": 5, "failure_threshold": 12},
    "liveness_probe": {"path": "/health", "port": 8080, "initial_delay_seconds": 15, "timeout_seconds": 5, "period_seconds": 30, "failure_threshold": 3}
  }
}
EOF

cat >"$FIXTURE_DIR/current-staging-source.json" <<'EOF'
{"project_id":"logdate-dev","region":"us-central1","service_name":"logdate-server-staging","cloud_run_image":"us-central1-docker.pkg.dev/logdate-dev/logdate/logdate-server:latest","runtime_service_account_name":"logdate-runtime","artifact_registry_repo":"logdate","domains":["cloud-staging.logdate.app"],"domain":"","android_signing_certificates":{},"env_vars":{"HOST":"0.0.0.0","GCS_PROJECT_ID":"logdate-dev","GCS_BUCKET_NAME":"logdate-media-staging","LOGDATE_ENV":"production","AUTO_MIGRATE":"true","WEBAUTHN_RP_ID":"cloud-staging.logdate.app","WEBAUTHN_ORIGIN":"https://cloud-staging.logdate.app","WEBAUTHN_ALLOWED_ORIGINS":"https://cloud-staging.logdate.app,android:apk-key-hash:3zJp1NzJxP5y_mFioPTp7l8EFEfcs472qSV2_DiQ28c","ANDROID_CERT_FINGERPRINTS":"DF:32:69:D4:DC:C9:C4:FE:72:FE:61:62:A0:F4:E9:EE:5F:04:14:47:DC:B3:8E:F6:A9:25:76:FC:38:90:DB:C7"},"secret_env":{"DATABASE_URL":{"secret_id":"logdate-db-url","version":"latest"}},"runtime":{}}
EOF
cat >"$FIXTURE_DIR/current-production-source.json" <<'EOF'
{"project_id":"logdate","region":"us-central1","service_name":"logdate-server","cloud_run_image":"us-docker.pkg.dev/cloudrun/container/hello","runtime_service_account_name":"logdate-runtime","artifact_registry_repo":"logdate","domains":["cloud.logdate.app"],"domain":"","android_signing_certificates":{},"env_vars":{"HOST":"0.0.0.0","GCS_PROJECT_ID":"logdate","GCS_BUCKET_NAME":"logdate-media-logdate","LOGDATE_ENV":"production","AUTO_MIGRATE":"false","WEBAUTHN_RP_ID":"logdate.app","WEBAUTHN_ORIGIN":"https://cloud.logdate.app"},"secret_env":{"DATABASE_URL":{"secret_id":"logdate-db-url","version":"latest"}},"runtime":{}}
EOF

cat >"$FAKE_BIN/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
TF_DATA_DIR="${TF_DATA_DIR:?}"
[[ -d "$TF_DATA_DIR" ]] || exit 91
[[ "$TF_DATA_DIR" != "${CHECKOUT_TERRAFORM_DIR:?}" ]] || exit 92
while IFS='=' read -r name _; do
    [[ "$name" != TF_CLI_ARGS* ]] || { printf 'ambient variable remained: %s\n' "$name" >"$LOG_DIR/preflight-failure.log"; exit 93; }
    [[ "$name" != TF_VAR_* ]] || { printf 'ambient variable remained: %s\n' "$name" >"$LOG_DIR/preflight-failure.log"; exit 93; }
done < <(env)
[[ -z "${TF_WORKSPACE:-}" ]] || { printf 'TF_WORKSPACE remained\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
[[ -z "${TF_LOG:-}" && -z "${TF_LOG_PATH:-}" ]] || { printf 'Terraform logging remained\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
[[ "${TF_INPUT:-}" == "0" ]] || { printf 'TF_INPUT was not forced off\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
config_dir="${1#-chdir=}"
[[ "$config_dir" != "${SOURCE_TFVARS_DIR:?}" ]] || { printf 'checkout config directory used\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
[[ ! -e "$config_dir/terraform.tfvars" && ! -e "$config_dir/task-1-poison.auto.tfvars" ]] || { printf 'auto-loaded canary copied\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
[[ -f "$config_dir/.terraform.lock.hcl" && -f "$config_dir/${EXPECTED_ENVIRONMENT:?}.tfvars" ]] || { printf 'required isolated inputs missing\n' >"$LOG_DIR/preflight-failure.log"; exit 93; }
config_files="$(find "$config_dir" -maxdepth 1 -type f -print | sed 's#.*/##' | sort | tr '\n' ' ')"
[[ "$config_files" == ".terraform.lock.hcl main.tf outputs.tf ${EXPECTED_ENVIRONMENT}.tfvars variables.tf versions.tf " ]] || { printf 'unexpected isolated inputs: %s\n' "$config_files" >"$LOG_DIR/preflight-failure.log"; exit 93; }
mode="$(stat -f '%Lp' "$TF_DATA_DIR" 2>/dev/null || stat -c '%a' "$TF_DATA_DIR")"
[[ "$mode" == "700" ]] || exit 94
printf '%s\n' "$TF_DATA_DIR" >>"$LOG_DIR/data-dirs.log"
{
    for arg in "$@"; do
        printf '[%s]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/terraform.log"

case "${2:-}" in
    init)
        [[ "$#" == "4" && "$3" == "-backend=false" && "$4" == "-input=false" ]]
        if [[ "${FAIL_TERRAFORM_INIT:-false}" == "true" ]]; then
            printf '%s\n' "${CREDENTIAL_SENTINEL:?}" >&2
            exit 31
        fi
        ;;
    console)
        [[ "$#" == "4" ]]
        [[ "$3" == -state=* && "$4" == "-var-file=${EXPECTED_ENVIRONMENT:?}.tfvars" ]]
        state_path="${3#-state=}"
        [[ "$state_path" == /tmp/* || "$state_path" == /private/* || "$state_path" == /var/* ]] || exit 95
        [[ "$state_path" != "${SOURCE_TFVARS_DIR:?}/terraform.tfstate" ]] || exit 96
        state_parent_mode="$(stat -f '%Lp' "$(dirname "$state_path")" 2>/dev/null || stat -c '%a' "$(dirname "$state_path")")"
        [[ "$state_parent_mode" == "700" ]] || exit 97
        printf '%s\n' "$state_path" >>"$LOG_DIR/state-paths.log"
        printf 'private-console-state\n' >"$state_path"
        expression="$(cat)"
        [[ "$expression" != *$'\n'* ]] || exit 98
        [[ "$expression" == *"jsonencode("* ]] || exit 98
        [[ "$expression" != *"google_sql_"* && "$expression" != *"google_service_account"* && "$expression" != *"local.cloud_sql_env"* ]] || exit 99
        printf '%s\n' "$expression" >>"$LOG_DIR/console-expression.log"
        fixture="${FAKE_TERRAFORM_FIXTURE:?}"
        case "$fixture" in
            current-staging-source.json)
                ! grep -q 'LOGDATE_EXPECT_FIRST_PARTY' "${SOURCE_TFVARS_DIR:?}/staging.tfvars" || exit 100
                ;;
            current-production-source.json)
                ! grep -q 'ANDROID_CERT_FINGERPRINTS' "${SOURCE_TFVARS_DIR:?}/production.tfvars" || exit 101
                ;;
        esac
        case "${FAKE_CONSOLE_MODE:-valid}" in
            malformed) printf 'not-json\n'; exit 0 ;;
            null) printf 'null\n'; exit 0 ;;
            unknown) printf '%s\n' '"(known after apply)"'; exit 0 ;;
            truncated) printf '%s\n' '"{\"project_id\":'; exit 0 ;;
            prefix-failure) printf '%s' '"{\"project_id\":'; printf '%s\n' "${CREDENTIAL_SENTINEL:?}" >&2; exit 37 ;;
        esac
        encoded="$(python3 - "${FIXTURE_DIR:?}/$fixture" <<'PY'
import json
import pathlib
import sys

source = json.loads(pathlib.Path(sys.argv[1]).read_text())
print(json.dumps(json.dumps(source, separators=(",", ":"))))
PY
        )"
        case "${FAKE_CONSOLE_MODE:-valid}" in
            noisy) printf 'Terraform diagnostic noise\n%s\n' "$encoded" ;;
            multi) printf '%s\n%s\n' "$encoded" "$encoded" ;;
            valid) printf '%s\n' "$encoded" ;;
            *) exit 102 ;;
        esac
        ;;
    *)
        exit 98
        ;;
esac
EOF
chmod +x "$FAKE_BIN/terraform"

cat >"$FAKE_BIN/jq" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${FAIL_JQ_SORT:-false}" == "true" && " $* " == *" -S "* ]]; then
    exit 41
fi
exec "${REAL_JQ:?}" "$@"
EOF
chmod +x "$FAKE_BIN/jq"

run_renderer() {
    local environment="$1" release_sha="$2" fixture="$3" stdout_file="$4" stderr_file="$5"
    PATH="$FAKE_BIN:$PATH" \
        TEST_LOG_DIR="$LOG_DIR" \
        CHECKOUT_TERRAFORM_DIR="$PWD/infra/terraform/.terraform" \
        SOURCE_TFVARS_DIR="$PWD/infra/terraform" \
        FIXTURE_DIR="$FIXTURE_DIR" \
        REAL_JQ="$REAL_JQ" \
        CREDENTIAL_SENTINEL="$CREDENTIAL_SENTINEL" \
        FAKE_TERRAFORM_FIXTURE="$fixture" \
        EXPECTED_ENVIRONMENT="$environment" \
        TF_WORKSPACE="production" \
        TF_CLI_ARGS="-lock-timeout=1s" \
        TF_CLI_ARGS_init="-backend-config=production.hcl" \
        TF_CLI_ARGS_console="-var-file=wrong.tfvars" \
        TF_CLI_ARGS_plan="-destroy" \
        TF_VAR_android_signing_certificates='poison' \
        TF_VAR_allow_unauthenticated='false' \
        TF_LOG='TRACE' \
        TF_LOG_PATH="$TMP_DIR/terraform-trace.log" \
        TF_INPUT='1' \
        "$SCRIPT" --environment "$environment" --release-sha "$release_sha" >"$stdout_file" 2>"$stderr_file"
}

cat >"$FIXTURE_DIR/staging-contract.json" <<EOF
{
  "canonical_origin": "https://cloud-staging.logdate.app",
  "env_vars": {
    "ANDROID_CERT_FINGERPRINTS": "E1:6A:82:07:74:DE:F6:29:24:EB:E1:48:67:47:8C:72:9C:69:A0:CB:9D:01:8A:8C:E4:49:44:DA:00:15:E9:5A",
    "ATPROTO_HANDLE_DOMAIN": "cloud-staging.logdate.app",
    "ATPROTO_PDS_SERVICE_URL": "https://cloud-staging.logdate.app",
    "AUTO_MIGRATE": "false",
    "BILLING_PROVIDER": "play",
    "DB_NAME": "logdate",
    "GCS_BUCKET_NAME": "logdate-media-staging",
    "GCS_PROJECT_ID": "logdate-dev",
    "HOST": "0.0.0.0",
    "INSTANCE_CONNECTION_NAME": "logdate-dev:us-central1:logdate-db",
    "LOGDATE_DEPLOYMENT_KIND": "first_party",
    "LOGDATE_ENV": "production",
    "LOGDATE_EXPECT_FIRST_PARTY": "true",
    "LOGDATE_PUBLIC_ORIGIN": "https://cloud-staging.logdate.app",
    "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud (Staging)",
    "RELEASE_VERSION": "logdate-server@$RELEASE_SHA",
    "SERVER_ENCRYPTION_ENABLED": "true",
    "SYNC_MEDIA_SIGNED_URLS": "true",
    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
    "WEBAUTHN_ALLOWED_ORIGINS": "https://cloud-staging.logdate.app,android:apk-key-hash:4WqCB3Te9ikk6-FIZ0eMcpxpoMudAYqM5ElE2gAV6Vo",
    "WEBAUTHN_ORIGIN": "https://cloud-staging.logdate.app",
    "WEBAUTHN_RP_ID": "cloud-staging.logdate.app"
  },
  "environment": "staging",
  "image": "us-central1-docker.pkg.dev/logdate-dev/logdate/logdate-server-staging:$RELEASE_SHA",
  "project_id": "logdate-dev",
  "region": "us-central1",
  "release_sha": "$RELEASE_SHA",
  "runtime": {
    "allow_unauthenticated": true,
    "ingress": "INGRESS_TRAFFIC_ALL",
    "port": 8080,
    "liveness_probe": {"failure_threshold": 3, "initial_delay_seconds": 15, "path": "/health", "period_seconds": 30, "port": 8080, "timeout_seconds": 5},
    "request_concurrency": 16,
    "resources": {"cpu": "1", "cpu_idle": true, "memory": "512Mi", "startup_cpu_boost": true},
    "scaling": {"max_instances": 10, "min_instances": 0},
    "startup_probe": {"failure_threshold": 12, "path": "/health", "period_seconds": 5, "port": 8080, "timeout_seconds": 5},
    "timeout_seconds": 60
  },
  "runtime_service_account": "logdate-runtime@logdate-dev.iam.gserviceaccount.com",
  "secret_env": {
    "DATABASE_PASSWORD": {"secret_id": "logdate-db-password", "version": "11"},
    "DATABASE_USER": {"secret_id": "logdate-db-user", "version": "7"},
    "HEALTH_INTERNAL_TOKEN": {"secret_id": "logdate-health-internal-token", "version": "13"},
    "JWT_SECRET": {"secret_id": "logdate-jwt-secret", "version": "3"},
    "SERVER_ENCRYPTION_KEY": {"secret_id": "logdate-server-encryption-key", "version": "5"},
    "SERVER_ENCRYPTION_KEY_ID": {"secret_id": "logdate-server-encryption-key-id", "version": "2"}
  },
  "service_name": "logdate-server-staging"
}
EOF
jq -S . "$FIXTURE_DIR/staging-contract.json" >"$FIXTURE_DIR/staging-contract.sorted.json"

STAGING_OUT="$TMP_DIR/staging.out"
STAGING_ERR="$TMP_DIR/staging.err"
set +e
run_renderer staging "$RELEASE_SHA" staging-source.json "$STAGING_OUT" "$STAGING_ERR"
staging_status=$?
set -e
if [[ "$staging_status" != "0" ]]; then
    printf 'Renderer stderr: %s\n' "$(cat "$STAGING_ERR")" >&2
    if [[ -f "$LOG_DIR/terraform.log" ]]; then
        printf 'Fake Terraform log:\n%s\n' "$(cat "$LOG_DIR/terraform.log")" >&2
    fi
    if [[ -f "$LOG_DIR/preflight-failure.log" ]]; then
        printf 'Fake Terraform preflight: %s\n' "$(cat "$LOG_DIR/preflight-failure.log")" >&2
    fi
fi
assert_exit_code 0 "$staging_status"
cmp -s "$FIXTURE_DIR/staging-contract.sorted.json" "$STAGING_OUT" || fail "staging contract did not match the hand-checked canonical fixture"
pass
assert_equals "" "$(cat "$STAGING_ERR")"

DETERMINISTIC_OUT="$TMP_DIR/staging-second.out"
run_renderer staging "$RELEASE_SHA" staging-source.json "$DETERMINISTIC_OUT" "$TMP_DIR/staging-second.err"
cmp -s "$STAGING_OUT" "$DETERMINISTIC_OUT" || fail "renderer output was not deterministic"
pass
jq -S . "$STAGING_OUT" >"$TMP_DIR/staging-resorted.json"
cmp -s "$STAGING_OUT" "$TMP_DIR/staging-resorted.json" || fail "renderer output was not sorted JSON"
pass

PRODUCTION_OUT="$TMP_DIR/production.out"
run_renderer production "$RELEASE_SHA" production-source.json "$PRODUCTION_OUT" "$TMP_DIR/production.err"
cat >"$FIXTURE_DIR/production-contract.json" <<EOF
{
  "canonical_origin": "https://cloud.logdate.app",
  "env_vars": {
    "ANDROID_CERT_FINGERPRINTS": "11:98:70:B8:78:F3:AB:5F:55:C0:DF:65:C7:87:89:C0:24:59:CA:9F:F3:22:A0:89:40:AE:43:A2:9D:1D:D5:AB,F1:3E:F5:D0:EC:93:ED:B0:8C:6C:F2:1D:8A:12:84:99:42:C2:92:D8:ED:EC:26:C0:E4:46:0C:3C:71:BC:6E:5F",
    "ATPROTO_HANDLE_DOMAIN": "logdate.app",
    "ATPROTO_PDS_SERVICE_URL": "https://cloud.logdate.app",
    "AUTO_MIGRATE": "false",
    "BILLING_PROVIDER": "play",
    "GCS_BUCKET_NAME": "logdate-media-logdate",
    "GCS_PROJECT_ID": "logdate",
    "HOST": "0.0.0.0",
    "LOGDATE_DEPLOYMENT_KIND": "first_party",
    "LOGDATE_ENV": "production",
    "LOGDATE_EXPECT_FIRST_PARTY": "true",
    "LOGDATE_PUBLIC_ORIGIN": "https://cloud.logdate.app",
    "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud",
    "RELEASE_VERSION": "logdate-server@$RELEASE_SHA",
    "SERVER_ENCRYPTION_ENABLED": "true",
    "SYNC_MEDIA_SIGNED_URLS": "true",
    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
    "WEBAUTHN_ALLOWED_ORIGINS": "https://cloud.logdate.app,android:apk-key-hash:EZhwuHjzq19VwN9lx4eJwCRZyp_zIqCJQK5Dop0d1as,android:apk-key-hash:8T710OyT7bCMbPIdihKEmULCktjt7CbA5EYMPHG8bl8",
    "WEBAUTHN_ORIGIN": "https://cloud.logdate.app",
    "WEBAUTHN_RP_ID": "logdate.app"
  },
  "environment": "production",
  "image": "us-central1-docker.pkg.dev/logdate/logdate/logdate-server:$RELEASE_SHA",
  "project_id": "logdate",
  "region": "us-central1",
  "release_sha": "$RELEASE_SHA",
  "runtime": {
    "allow_unauthenticated": true,
    "ingress": "INGRESS_TRAFFIC_ALL",
    "port": 8080,
    "liveness_probe": {"failure_threshold": 3, "initial_delay_seconds": 15, "path": "/health", "period_seconds": 30, "port": 8080, "timeout_seconds": 5},
    "request_concurrency": 16,
    "resources": {"cpu": "1", "cpu_idle": true, "memory": "512Mi", "startup_cpu_boost": true},
    "scaling": {"max_instances": 10, "min_instances": 1},
    "startup_probe": {"failure_threshold": 12, "path": "/health", "period_seconds": 5, "port": 8080, "timeout_seconds": 5},
    "timeout_seconds": 60
  },
  "runtime_service_account": "logdate-runtime@logdate.iam.gserviceaccount.com",
  "secret_env": {
    "DATABASE_PASSWORD": {"secret_id": "logdate-db-password", "version": "11"},
    "DATABASE_URL": {"secret_id": "logdate-db-url", "version": "17"},
    "DATABASE_USER": {"secret_id": "logdate-db-user", "version": "7"},
    "HEALTH_INTERNAL_TOKEN": {"secret_id": "logdate-health-internal-token", "version": "13"},
    "JWT_SECRET": {"secret_id": "logdate-jwt-secret", "version": "3"},
    "SERVER_ENCRYPTION_KEY": {"secret_id": "logdate-server-encryption-key", "version": "5"},
    "SERVER_ENCRYPTION_KEY_ID": {"secret_id": "logdate-server-encryption-key-id", "version": "2"}
  },
  "service_name": "logdate-server"
}
EOF
jq -S . "$FIXTURE_DIR/production-contract.json" >"$FIXTURE_DIR/production-contract.sorted.json"
cmp -s "$FIXTURE_DIR/production-contract.sorted.json" "$PRODUCTION_OUT" || fail "production contract did not match the hand-checked canonical fixture"
pass
assert_equals "production" "$(jq -r '.environment' "$PRODUCTION_OUT")"
assert_equals "https://cloud.logdate.app" "$(jq -r '.canonical_origin' "$PRODUCTION_OUT")"
assert_equals "us-central1-docker.pkg.dev/logdate/logdate/logdate-server:$RELEASE_SHA" "$(jq -r '.image' "$PRODUCTION_OUT")"
assert_equals "17" "$(jq -r '.secret_env.DATABASE_URL.version' "$PRODUCTION_OUT")"
assert_equals "2" "$(jq -r '.env_vars.ANDROID_CERT_FINGERPRINTS | split(",") | length' "$PRODUCTION_OUT")"
assert_equals "10" "$(jq -r '.runtime.scaling.max_instances' "$PRODUCTION_OUT")"
assert_equals "false" "$(jq -r 'has("android_signing_certificates")' "$PRODUCTION_OUT")"
assert_equals "1" "$(jq -s 'length' "$PRODUCTION_OUT")"
assert_equals "object" "$(jq -r 'type' "$PRODUCTION_OUT")"
assert_equals "false" "$(jq -r '.env_vars | has("INSTANCE_CONNECTION_NAME") or has("DB_NAME")' "$PRODUCTION_OUT")"
assert_not_contains ':latest' "$(cat "$STAGING_OUT" "$PRODUCTION_OUT")"
assert_not_contains 'us-docker.pkg.dev/cloudrun/container/hello' "$(cat "$STAGING_OUT" "$PRODUCTION_OUT")"

expect_failure() {
    local label="$1" environment="$2" release_sha="$3" fixture="$4" expected_error="$5"
    local output_file="$TMP_DIR/$label.out" error_file="$TMP_DIR/$label.err" status
    set +e
    run_renderer "$environment" "$release_sha" "$fixture" "$output_file" "$error_file"
    status=$?
    set -e
    [[ "$status" != "0" ]] || fail "$label unexpectedly succeeded"
    pass
    assert_equals "" "$(cat "$output_file")"
    if ! grep -Fq -- "$expected_error" "$error_file"; then
        fail "$label expected stderr to contain '$expected_error'; got: $(cat "$error_file")"
    fi
    pass
}

expect_failure invalid-environment preview "$RELEASE_SHA" staging-source.json "environment must be staging or production"
expect_failure invalid-sha staging ABCDEF staging-source.json "release SHA must be 40 lowercase hexadecimal characters"

FAIL_TERRAFORM_INIT="true" expect_failure \
    terraform-init-failure staging "$RELEASE_SHA" staging-source.json "Terraform backend-free initialization failed"

for console_mode in noisy multi malformed null unknown truncated; do
    FAKE_CONSOLE_MODE="$console_mode" expect_failure \
        "console-$console_mode" staging "$RELEASE_SHA" staging-source.json "Terraform console did not return one concrete JSON object"
done

FAKE_CONSOLE_MODE="prefix-failure" expect_failure \
    console-prefix-failure staging "$RELEASE_SHA" staging-source.json "Terraform console failed"

FAIL_JQ_SORT="true" expect_failure jq-sort-failure staging "$RELEASE_SHA" staging-source.json "failed to sort the validated deployment contract"

jq '.env_vars.LOGDATE_PUBLIC_ORIGIN = "http://cloud-staging.logdate.app"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/http-origin.json"
expect_failure http-origin staging "$RELEASE_SHA" http-origin.json "LOGDATE_PUBLIC_ORIGIN must be the canonical HTTPS origin"

jq '.android_signing_certificates.staging.apk_key_hash_origin = "android:apk-key-hash:ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/certificate-mismatch.json"
expect_failure certificate-mismatch staging "$RELEASE_SHA" certificate-mismatch.json "Android certificate fingerprints and apk-key-hash origins must match exactly"

jq '.env_vars.ANDROID_CERT_FINGERPRINTS = "00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/reserved-aggregate.json"
expect_failure reserved-aggregate staging "$RELEASE_SHA" reserved-aggregate.json "cloud_run_env must not set reserved Android aggregate keys"

jq '.secret_env.JWT_SECRET.version = "latest"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/latest-version.json"
expect_failure latest-version staging "$RELEASE_SHA" latest-version.json "JWT_SECRET secret version must be an exact positive integer"

numeric_version_cases=(
    'del(.secret_env.JWT_SECRET.version)'
    '.secret_env.JWT_SECRET.version = "0"'
    '.secret_env.JWT_SECRET.version = "01"'
    '.secret_env.JWT_SECRET.version = "-1"'
    '.secret_env.JWT_SECRET.version = "+1"'
    '.secret_env.JWT_SECRET.version = "1.0"'
    '.secret_env.JWT_SECRET.version = " 1"'
    '.secret_env.JWT_SECRET.version = true'
    '.secret_env.JWT_SECRET.version = 1'
)
numeric_version_labels=(omitted zero leading-zero negative signed decimal whitespace boolean numeric)
for index in "${!numeric_version_cases[@]}"; do
    label="${numeric_version_labels[$index]}-version"
    jq "${numeric_version_cases[$index]}" "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/$label.json"
    expect_failure "$label" staging "$RELEASE_SHA" "$label.json" "JWT_SECRET secret version must be an exact positive integer"
done

jq '.secret_env.SENTRY_DSN = {"secret_id":"logdate-sentry-dsn","version":"latest"}' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/optional-latest-version.json"
expect_failure optional-latest-version staging "$RELEASE_SHA" optional-latest-version.json "SENTRY_DSN secret version must be an exact positive integer"

jq --arg sentinel "$CREDENTIAL_SENTINEL" '.secret_env.JWT_SECRET = {"secret_id":$sentinel,"version":"latest"}' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/credential-sentinel.json"
expect_failure credential-sentinel staging "$RELEASE_SHA" credential-sentinel.json "JWT_SECRET secret version must be an exact positive integer"

jq 'del(.env_vars.DB_NAME)' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/missing-database.json"
expect_failure missing-database staging "$RELEASE_SHA" missing-database.json "DB_NAME is required with INSTANCE_CONNECTION_NAME"

jq '.env_vars.DB_NAME = " "' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/blank-database.json"
expect_failure blank-database staging "$RELEASE_SHA" blank-database.json "DB_NAME is required with INSTANCE_CONNECTION_NAME"

jq '.secret_env.DATABASE_URL = {"secret_id":"logdate-db-url","version":"17"}' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/mixed-database.json"
expect_failure mixed-database staging "$RELEASE_SHA" mixed-database.json "database contract must select exactly one of connector or URL mode"

jq '.secret_env.DATABASE_URL.secret_id = " "' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/blank-database-url.json"
expect_failure blank-database-url production "$RELEASE_SHA" blank-database-url.json "DATABASE_URL secret ID must be a non-empty single-line string"

jq '.runtime.scaling.max_instances = 0' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-runtime.json"
expect_failure invalid-runtime staging "$RELEASE_SHA" invalid-runtime.json "runtime scaling must have max_instances greater than or equal to min_instances and at least 1"

jq '.runtime.port = 0' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-runtime-port.json"
expect_failure invalid-runtime-port staging "$RELEASE_SHA" invalid-runtime-port.json "runtime port must be 8080"

jq '.runtime.allow_unauthenticated = false' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/private-runtime.json"
expect_failure private-runtime staging "$RELEASE_SHA" private-runtime.json "first-party runtime must allow unauthenticated canonical traffic"

jq '.runtime.ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/internal-ingress.json"
expect_failure internal-ingress staging "$RELEASE_SHA" internal-ingress.json "first-party runtime ingress must be INGRESS_TRAFFIC_ALL"

jq 'del(.runtime.startup_probe.failure_threshold)' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/incomplete-runtime.json"
expect_failure incomplete-runtime staging "$RELEASE_SHA" incomplete-runtime.json "runtime.startup_probe must contain exactly"

jq '.runtime.startup_probe.port = 0' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-probe-port.json"
expect_failure invalid-probe-port staging "$RELEASE_SHA" invalid-probe-port.json "runtime probe ports must be 8080"

jq '.runtime.liveness_probe.path = "/ready"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-probe-path.json"
expect_failure invalid-probe-path staging "$RELEASE_SHA" invalid-probe-path.json "runtime probe paths must be /health"

jq '.runtime.resources.cpu = ""' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-resource.json"
expect_failure invalid-resource staging "$RELEASE_SHA" invalid-resource.json "runtime CPU and memory must be non-empty strings"

jq '.runtime.resources.cpu = "garbage"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-cpu.json"
expect_failure invalid-cpu staging "$RELEASE_SHA" invalid-cpu.json "runtime CPU must be a positive numeric value"

jq '.runtime.resources.memory = "0Mi"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-memory.json"
expect_failure invalid-memory staging "$RELEASE_SHA" invalid-memory.json "runtime memory must be a positive Mi or Gi value"

jq '.runtime.request_concurrency = 0' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-concurrency.json"
expect_failure invalid-concurrency staging "$RELEASE_SHA" invalid-concurrency.json "runtime request_concurrency must be a positive integer"

jq '.runtime.timeout_seconds = 0' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/invalid-timeout.json"
expect_failure invalid-timeout staging "$RELEASE_SHA" invalid-timeout.json "runtime timeout_seconds must be a positive integer"

jq '.runtime.scaling.min_instances = -1' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/negative-scaling.json"
expect_failure negative-scaling staging "$RELEASE_SHA" negative-scaling.json "runtime scaling must use non-negative integers"

jq '.unexpected = true' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/unexpected-top-level.json"
expect_failure unexpected-top-level staging "$RELEASE_SHA" unexpected-top-level.json "Terraform source projection contains unexpected keys"

jq '.env_vars.UNEXPECTED_ENV = "value"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/unexpected-env.json"
expect_failure unexpected-env staging "$RELEASE_SHA" unexpected-env.json "environment contract contains unexpected keys"

jq '.secret_env.UNEXPECTED_SECRET = {"secret_id":"unexpected","version":"1"}' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/unexpected-secret.json"
expect_failure unexpected-secret staging "$RELEASE_SHA" unexpected-secret.json "secret contract contains unexpected keys"

jq '.runtime.unexpected = true' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/unexpected-runtime.json"
expect_failure unexpected-runtime staging "$RELEASE_SHA" unexpected-runtime.json "runtime must contain exactly"

jq '.env_vars.AUTO_MIGRATE = "true"' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/auto-migrate.json"
expect_failure auto-migrate staging "$RELEASE_SHA" auto-migrate.json "AUTO_MIGRATE must be false"

jq 'del(.android_signing_certificates.upload)' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/missing-upload-set.json"
expect_failure missing-upload-set production "$RELEASE_SHA" missing-upload-set.json "production requires separately identified Android upload certificate fingerprints and origins"

jq '.android_signing_certificates.play_app_signing = .android_signing_certificates.upload' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/duplicate-production-set.json"
expect_failure duplicate-production-set production "$RELEASE_SHA" duplicate-production-set.json "production upload and Play app-signing certificates must be distinct"

jq '.android_signing_certificates.upload = {"fingerprint":"DF:32:69:D4:DC:C9:C4:FE:72:FE:61:62:A0:F4:E9:EE:5F:04:14:47:DC:B3:8E:F6:A9:25:76:FC:38:90:DB:C7","apk_key_hash_origin":"android:apk-key-hash:3zJp1NzJxP5y_mFioPTp7l8EFEfcs472qSV2_DiQ28c"}' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/debug-production-set.json"
expect_failure debug-production-set production "$RELEASE_SHA" debug-production-set.json "production certificate sets may not contain the known debug certificate"

jq '.android_signing_certificates.upload = {"fingerprint":"00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00","apk_key_hash_origin":"android:apk-key-hash:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/placeholder-production-set.json"
expect_failure placeholder-production-set production "$RELEASE_SHA" placeholder-production-set.json "production certificate sets may not contain placeholder certificates"

jq '.android_signing_certificates.upload = {"fingerprint":"00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F","apk_key_hash_origin":"android:apk-key-hash:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"}' "$FIXTURE_DIR/production-source.json" >"$FIXTURE_DIR/sequential-production-set.json"
expect_failure sequential-production-set production "$RELEASE_SHA" sequential-production-set.json "production certificate sets may not contain placeholder certificates"

jq '.android_signing_certificates.upload = .android_signing_certificates.staging' "$FIXTURE_DIR/staging-source.json" >"$FIXTURE_DIR/unexpected-staging-role.json"
expect_failure unexpected-staging-role staging "$RELEASE_SHA" unexpected-staging-role.json "staging requires exactly the staging signing certificate"

expect_failure current-staging staging "$RELEASE_SHA" current-staging-source.json "staging requires exactly the staging signing certificate"
expect_failure current-production production "$RELEASE_SHA" current-production-source.json "production requires separately identified Android upload certificate fingerprints and origins"

while IFS= read -r data_dir; do
    [[ ! -e "$data_dir" ]] || fail "expected renderer temporary directory to be removed: $data_dir"
done < <(sort -u "$LOG_DIR/data-dirs.log")
pass
while IFS= read -r state_path; do
    [[ ! -e "$state_path" ]] || fail "expected renderer private state to be removed: $state_path"
done < <(sort -u "$LOG_DIR/state-paths.log")
pass
assert_equals "$CHECKOUT_STATE_CHECKSUM" "$(shasum -a 256 "$CHECKOUT_STATE" | awk '{print $1}')"
assert_equals "$STAGING_TFVARS_CHECKSUM" "$(shasum -a 256 infra/terraform/staging.tfvars | awk '{print $1}')"
assert_equals "$PRODUCTION_TFVARS_CHECKSUM" "$(shasum -a 256 infra/terraform/production.tfvars | awk '{print $1}')"
assert_equals "$LOCKFILE_CHECKSUM" "$(shasum -a 256 infra/terraform/.terraform.lock.hcl | awk '{print $1}')"
assert_file_contains 'checkout-plugin-canary' "$CHECKOUT_PLUGIN_CANARY"
assert_file_contains 'checkout-workspace-canary' "$CHECKOUT_WORKSPACE_CANARY"
assert_file_contains 'checkout-poison' "$CHECKOUT_TFVARS_CANARY"
assert_file_contains 'allow_unauthenticated = false' "$CHECKOUT_AUTO_TFVARS_CANARY"
assert_file_not_contains 'backend-config' "$LOG_DIR/terraform.log"
assert_file_not_contains 'wrong.tfvars' "$LOG_DIR/terraform.log"
assert_file_contains 'jsonencode(' "$LOG_DIR/console-expression.log"
assert_file_contains 'var.android_signing_certificates' "$LOG_DIR/console-expression.log"
assert_file_contains 'if certificate != null' "$LOG_DIR/console-expression.log"
assert_file_not_contains 'google_sql_' "$LOG_DIR/console-expression.log"
assert_file_not_contains 'google_service_account' "$LOG_DIR/console-expression.log"
assert_file_not_contains 'local.cloud_sql_env' "$LOG_DIR/console-expression.log"
console_count="$(grep -c '\[init\]' "$LOG_DIR/terraform.log")"
unique_data_dir_count="$(sort -u "$LOG_DIR/data-dirs.log" | wc -l | tr -d ' ')"
assert_equals "$console_count" "$unique_data_dir_count"
all_error_output="$(find "$TMP_DIR" -maxdepth 1 -name '*.err' -type f -exec cat {} +)"
assert_not_contains "$CREDENTIAL_SENTINEL" "$all_error_output"

print_pass_summary "Cloud Run contract renderer"
