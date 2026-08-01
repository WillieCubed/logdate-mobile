#!/usr/bin/env bash
# Real-behavior regression tests for the contract-driven Cloud SQL migration helper.

set -euo pipefail

# shellcheck source=scripts/tests/lib/assertions.sh
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/run-migrations.sh"
DEFAULT_PROXY_VERSION="v2.21.3"
DEFAULT_PROXY_URL="https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/${DEFAULT_PROXY_VERSION}/cloud-sql-proxy.linux.amd64"
DATABASE_PASSWORD_SENTINEL="MIGRATION_DATABASE_PASSWORD_SENTINEL_DO_NOT_LEAK"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
LOG_DIR="$TMP_DIR/logs"
LEGACY_LOG_DIR="$TMP_DIR/logs-legacy"
CONTRACT_FILE="$TMP_DIR/staging-contract.json"
OUTPUT_FILE="$TMP_DIR/migrations.out"

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN" "$LOG_DIR" "$LEGACY_LOG_DIR"

cat >"$CONTRACT_FILE" <<'EOF'
{
  "environment": "staging",
  "release_sha": "0123456789abcdef0123456789abcdef01234567",
  "project_id": "logdate-contract-test",
  "region": "us-central1",
  "service_name": "logdate-server-staging",
  "canonical_origin": "https://cloud-staging.logdate.app",
  "runtime_service_account": "logdate-runtime@logdate-contract-test.iam.gserviceaccount.com",
  "image": "us-central1-docker.pkg.dev/logdate-contract-test/logdate/logdate-server:0123456789abcdef0123456789abcdef01234567",
  "env_vars": {
    "INSTANCE_CONNECTION_NAME": "logdate-contract-test:us-central1:logdate-db",
    "DB_NAME": "logdate"
  },
  "secret_env": {
    "DATABASE_USER": { "secret_id": "logdate-db-user", "version": "7" },
    "DATABASE_PASSWORD": { "secret_id": "logdate-db-password", "version": "11" }
  },
  "runtime": {}
}
EOF

cat >"$FAKE_BIN/gcloud" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
{
    for arg in "$@"; do
        printf '[%q]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/gcloud.log"

case "${1:-} ${2:-} ${3:-}" in
    "auth print-access-token ")
        printf 'fake-access-token\n'
        ;;
    "secrets versions access")
        version="$4"
        secret_id=""
        for arg in "$@"; do
            case "$arg" in
                --secret=*) secret_id="${arg#--secret=}" ;;
            esac
        done
        case "$secret_id:$version" in
            logdate-db-user:7) printf 'migration-user' ;;
            logdate-db-password:11) printf '%s' "${DATABASE_PASSWORD_SENTINEL:?}" ;;
            logdate-db-user:latest) printf 'migration-user' ;;
            logdate-db-password:latest) printf '%s' "${DATABASE_PASSWORD_SENTINEL:?}" ;;
            *)
                echo "Unexpected secret/version: $secret_id:$version" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        echo "Unexpected gcloud invocation: $*" >&2
        exit 1
        ;;
esac
EOF
chmod +x "$FAKE_BIN/gcloud"

cat >"$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
{
    for arg in "$@"; do
        printf '[%q]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/curl.log"

output_path=""
url=""
next_is_output="false"
for arg in "$@"; do
    if [[ "$next_is_output" == "true" ]]; then
        output_path="$arg"
        next_is_output="false"
        continue
    fi
    case "$arg" in
        -o|--output) next_is_output="true" ;;
        http://*|https://*) url="$arg" ;;
    esac
done

if [[ "$url" != "${EXPECTED_PROXY_URL:?}" || -z "$output_path" ]]; then
    echo "Unexpected curl invocation: $*" >&2
    exit 1
fi

cat >"$output_path" <<'PROXY'
#!/usr/bin/env bash
set -euo pipefail
LOG_DIR="${TEST_LOG_DIR:?}"
{
    for arg in "$@"; do
        printf '[%q]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/proxy.log"
printf '%s\n' "$$" >"$LOG_DIR/proxy-pid"

port=""
next_is_port="false"
for arg in "$@"; do
    if [[ "$next_is_port" == "true" ]]; then
        port="$arg"
        next_is_port="false"
        continue
    fi
    [[ "$arg" == "--port" ]] && next_is_port="true"
done

exec python3 - "$port" <<'PY'
import socket
import sys

server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(("127.0.0.1", int(sys.argv[1])))
server.listen()
while True:
    connection, _ = server.accept()
    connection.close()
PY
PROXY
chmod +x "$output_path"
EOF
chmod +x "$FAKE_BIN/curl"

cat >"$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
{
    for arg in "$@"; do
        printf '[%q]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/docker.log"

env_file=""
image=""
next_is_env_file="false"
for arg in "$@"; do
    if [[ "$next_is_env_file" == "true" ]]; then
        env_file="$arg"
        next_is_env_file="false"
        continue
    fi
    case "$arg" in
        --env-file) next_is_env_file="true" ;;
        flyway/flyway:*|postgres:*) image="$arg" ;;
    esac
done

[[ -n "$env_file" && -f "$env_file" ]] || { echo "missing env file" >&2; exit 1; }
mode="$(stat -f '%Lp' "$env_file" 2>/dev/null || stat -c '%a' "$env_file")"
[[ "$mode" == "600" ]] || { echo "env file mode was $mode" >&2; exit 1; }
printf '%s\n' "$env_file" >>"$LOG_DIR/env-paths.log"

case "$image" in
    flyway/flyway:12.4.0)
        grep -Fxq 'FLYWAY_URL=jdbc:postgresql://127.0.0.1:15432/logdate' "$env_file"
        grep -Fxq 'FLYWAY_USER=migration-user' "$env_file"
        grep -Fxq "FLYWAY_PASSWORD=${DATABASE_PASSWORD_SENTINEL:?}" "$env_file"
        grep -Fxq 'FLYWAY_CONNECT_RETRIES=10' "$env_file"
        python3 - <<'PY'
import socket

with socket.create_connection(("127.0.0.1", 15432), timeout=1):
    pass
PY
        touch "$LOG_DIR/flyway-called"
        ;;
    postgres:16-alpine)
        grep -Fxq 'PGHOST=127.0.0.1' "$env_file"
        grep -Fxq 'PGPORT=15432' "$env_file"
        grep -Fxq 'PGDATABASE=logdate' "$env_file"
        grep -Fxq 'PGUSER=migration-user' "$env_file"
        grep -Fxq "PGPASSWORD=${DATABASE_PASSWORD_SENTINEL:?}" "$env_file"
        touch "$LOG_DIR/psql-called"
        ;;
    *)
        echo "Unexpected image: $image" >&2
        exit 1
        ;;
esac
EOF
chmod +x "$FAKE_BIN/docker"

cat >"$FAKE_BIN/uname" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
    -s) printf 'Linux\n' ;;
    -m) printf 'x86_64\n' ;;
    *) printf 'Linux\n' ;;
esac
EOF
chmod +x "$FAKE_BIN/uname"

set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LOG_DIR" \
EXPECTED_PROXY_URL="$DEFAULT_PROXY_URL" \
DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
bash -x "$SCRIPT" \
    --contract-file "$CONTRACT_FILE" \
    --environment staging \
    --validate-passkey-fk >"$OUTPUT_FILE" 2>&1
status=$?
set -e

output="$(cat "$OUTPUT_FILE")"
if [[ "$status" != "0" ]]; then
    echo "$output"
fi
assert_exit_code 0 "$status"
assert_contains 'Migration target: logdate-contract-test:us-central1:logdate-db' "$output"
assert_contains 'Proxy ready.' "$output"
assert_contains 'Flyway migrate complete.' "$output"
assert_contains 'Passkey FK validated.' "$output"
assert_contains "$DEFAULT_PROXY_URL" "$(cat "$LOG_DIR/curl.log")"
assert_contains '[logdate-contract-test:us-central1:logdate-db]' "$(cat "$LOG_DIR/proxy.log")"
assert_contains '[flyway/flyway:12.4.0]' "$(cat "$LOG_DIR/docker.log")"
assert_contains '[postgres:16-alpine]' "$(cat "$LOG_DIR/docker.log")"
assert_contains '[access][7][--secret=logdate-db-user][--project=logdate-contract-test]' "$(cat "$LOG_DIR/gcloud.log")"
assert_contains '[access][11][--secret=logdate-db-password][--project=logdate-contract-test]' "$(cat "$LOG_DIR/gcloud.log")"
assert_not_contains '[-e]' "$(cat "$LOG_DIR/docker.log")"
assert_not_contains 'socketFactory' "$(cat "$LOG_DIR/docker.log")"
[[ -f "$LOG_DIR/flyway-called" ]] || fail "expected Flyway container to run"
pass
[[ -f "$LOG_DIR/psql-called" ]] || fail "expected PostgreSQL validation container to run"
pass

security_evidence="$(cat "$OUTPUT_FILE" "$LOG_DIR/gcloud.log" "$LOG_DIR/curl.log" "$LOG_DIR/proxy.log" "$LOG_DIR/docker.log")"
assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$security_evidence"
assert_not_contains 'migration-user' "$security_evidence"

while IFS= read -r env_path; do
    [[ ! -e "$env_path" ]] || fail "expected trap to remove $env_path"
    pass
done <"$LOG_DIR/env-paths.log"
proxy_pid="$(cat "$LOG_DIR/proxy-pid")"
if kill -0 "$proxy_pid" 2>/dev/null; then
    fail "expected trap to stop Cloud SQL Auth Proxy process $proxy_pid"
fi
pass

LEGACY_OUTPUT_FILE="$TMP_DIR/migrations-legacy.out"
set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LEGACY_LOG_DIR" \
EXPECTED_PROXY_URL="$DEFAULT_PROXY_URL" \
DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
bash -x "$SCRIPT" \
    --legacy-config \
    --project-id logdate-contract-test \
    --region us-central1 \
    --instance-name logdate-db \
    --database-name logdate >"$LEGACY_OUTPUT_FILE" 2>&1
legacy_status=$?
set -e

legacy_output="$(cat "$LEGACY_OUTPUT_FILE")"
if [[ "$legacy_status" != "0" ]]; then
    echo "$legacy_output"
fi
assert_exit_code 0 "$legacy_status"
assert_contains 'TEMPORARY COMPATIBILITY MODE: --legacy-config' "$legacy_output"
assert_contains '[access][latest][--secret=logdate-db-user][--project=logdate-contract-test]' "$(cat "$LEGACY_LOG_DIR/gcloud.log")"
assert_contains '[access][latest][--secret=logdate-db-password][--project=logdate-contract-test]' "$(cat "$LEGACY_LOG_DIR/gcloud.log")"
legacy_security_evidence="$(cat "$LEGACY_OUTPUT_FILE" "$LEGACY_LOG_DIR"/gcloud.log "$LEGACY_LOG_DIR"/curl.log "$LEGACY_LOG_DIR"/proxy.log "$LEGACY_LOG_DIR"/docker.log)"
assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$legacy_security_evidence"
assert_not_contains 'migration-user' "$legacy_security_evidence"
while IFS= read -r env_path; do
    [[ ! -e "$env_path" ]] || fail "expected legacy trap to remove $env_path"
    pass
done <"$LEGACY_LOG_DIR/env-paths.log"
legacy_proxy_pid="$(cat "$LEGACY_LOG_DIR/proxy-pid")"
if kill -0 "$legacy_proxy_pid" 2>/dev/null; then
    fail "expected legacy trap to stop Cloud SQL Auth Proxy process $legacy_proxy_pid"
fi
pass

workflow_contents="$(cat .github/workflows/deploy-server-cloud-run.yml)"
assert_contains '--legacy-config' "$workflow_contents"
assert_contains "--project-id \"\${PROJECT_ID}\"" "$workflow_contents"
assert_contains "--region \"\${REGION}\"" "$workflow_contents"
assert_not_contains '--user-secret' "$workflow_contents"
assert_not_contains '--password-secret' "$workflow_contents"

set +e
missing_contract_output="$(PATH="$FAKE_BIN:$PATH" "$SCRIPT" --environment staging 2>&1)"
missing_contract_status=$?
independent_override_output="$({ PROJECT_ID=override PATH="$FAKE_BIN:$PATH" "$SCRIPT" --contract-file "$CONTRACT_FILE" --environment staging; } 2>&1)"
independent_override_status=$?
flag_override_output="$(PATH="$FAKE_BIN:$PATH" "$SCRIPT" --contract-file "$CONTRACT_FILE" --environment staging --database-name other 2>&1)"
flag_override_status=$?
environment_mismatch_output="$(TEST_LOG_DIR="$LOG_DIR" PATH="$FAKE_BIN:$PATH" "$SCRIPT" --contract-file "$CONTRACT_FILE" --environment production 2>&1)"
environment_mismatch_status=$?
set -e

assert_exit_code 1 "$missing_contract_status"
assert_contains '--contract-file is required' "$missing_contract_output"
assert_exit_code 1 "$independent_override_status"
assert_contains 'independent database overrides are not supported' "$independent_override_output"
assert_exit_code 1 "$flag_override_status"
assert_contains 'independent database overrides are not supported' "$flag_override_output"
assert_exit_code 1 "$environment_mismatch_status"
assert_contains "contract environment 'staging' does not match requested environment 'production'" "$environment_mismatch_output"

INVALID_CONTRACT="$TMP_DIR/invalid-version-contract.json"
sed 's/"version": "11"/"version": "latest"/' "$CONTRACT_FILE" >"$INVALID_CONTRACT"
set +e
invalid_version_output="$(TEST_LOG_DIR="$LOG_DIR" PATH="$FAKE_BIN:$PATH" "$SCRIPT" --contract-file "$INVALID_CONTRACT" --environment staging 2>&1)"
invalid_version_status=$?
set -e
assert_exit_code 1 "$invalid_version_status"
assert_contains 'DATABASE_PASSWORD secret version must be an exact numeric version' "$invalid_version_output"

ALTERNATE_SHAPE_CONTRACT="$TMP_DIR/private-alternate-shape-contract.json"
sed -e 's/"env_vars"/"cloud_run_env"/' -e 's/"secret_env"/"cloud_run_secret_env"/' "$CONTRACT_FILE" >"$ALTERNATE_SHAPE_CONTRACT"
set +e
alternate_shape_output="$(TEST_LOG_DIR="$LOG_DIR" PATH="$FAKE_BIN:$PATH" "$SCRIPT" --contract-file "$ALTERNATE_SHAPE_CONTRACT" --environment staging 2>&1)"
alternate_shape_status=$?
set -e
assert_exit_code 1 "$alternate_shape_status"
assert_contains 'INSTANCE_CONNECTION_NAME must be a non-empty single-line string' "$alternate_shape_output"

help_output="$("$SCRIPT" --help)"
assert_contains '--contract-file PATH' "$help_output"
assert_contains '--environment NAME' "$help_output"
assert_contains 'v2.21.3' "$help_output"
assert_contains 'flyway/flyway:12.4.0' "$help_output"

print_pass_summary "run-migrations"
