#!/usr/bin/env bash
# Real-behavior regression tests for the deployment migration helper.

set -euo pipefail

# shellcheck source=scripts/tests/lib/assertions.sh
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/run-migrations.sh"
DATABASE_PASSWORD_SENTINEL="MIGRATION_DATABASE_PASSWORD_SENTINEL_DO_NOT_LEAK"
TEST_DATABASE_URL="jdbc:postgresql://neon.example.test/logdate?sslmode=require"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
LOG_DIR="$TMP_DIR/logs"
MISSING_LEGACY_LOG_DIR="$TMP_DIR/logs-legacy-missing-url"
MALFORMED_LEGACY_LOG_DIR="$TMP_DIR/logs-legacy-malformed-url"
CONTRACT_FILE="$TMP_DIR/staging-contract.json"
OUTPUT_FILE="$TMP_DIR/migrations.out"

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p \
    "$FAKE_BIN" \
    "$LOG_DIR" \
    "$MISSING_LEGACY_LOG_DIR" \
    "$MALFORMED_LEGACY_LOG_DIR"

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
  "env_vars": {},
  "secret_env": {
    "DATABASE_URL": { "secret_id": "logdate-db-url", "version": "17" },
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
            logdate-db-url:17)
                if [[ -n "${TEST_MISSING_DATABASE_URL:-}" ]]; then
                    exit 1
                elif [[ -n "${TEST_MALFORMED_DATABASE_URL:-}" ]]; then
                    printf 'jdbc:postgresql://neon.example.test/logdate\nFLYWAY_USER=attacker'
                else
                    printf '%s' "${TEST_DATABASE_URL:-jdbc:postgresql://contract-neon.example.test/logdate?sslmode=verify-full&channelBinding=require}"
                fi
                ;;
            logdate-db-user:7) printf 'migration-user' ;;
            logdate-db-password:11) printf '%s' "${DATABASE_PASSWORD_SENTINEL:?}" ;;
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

# run-migrations.sh no longer downloads anything: Cloud SQL and its auth proxy
# are gone, and the Neon URL comes from Secret Manager. Any curl invocation is
# therefore a regression, so the stub records it and fails.
cat >"$FAKE_BIN/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ -n "${TEST_LOG_DIR:-}" ]]; then
    printf 'curl %s\n' "$*" >>"$TEST_LOG_DIR/curl.log"
fi
echo "Unexpected curl invocation: $*" >&2
exit 1
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

require_env_line() {
    local expected="$1"
    grep -Fxq "$expected" "$env_file" || {
        printf 'missing expected environment key: %s\n' "${expected%%=*}" >&2
        exit 1
    }
}

require_env_key_absent() {
    local key="$1"
    ! grep -Fq "${key}=" "$env_file" || {
        printf 'unexpected environment key: %s\n' "$key" >&2
        exit 1
    }
}

case "$image" in
    flyway/flyway:12.4.0)
        if [[ -n "${TEST_EXPECT_DIRECT_URL:-}" ]]; then
            require_env_line "FLYWAY_URL=${EXPECTED_FLYWAY_URL:?}"
        else
            require_env_line 'FLYWAY_URL=jdbc:postgresql://127.0.0.1:15432/logdate'
        fi
        require_env_line 'FLYWAY_USER=migration-user'
        require_env_line "FLYWAY_PASSWORD=${DATABASE_PASSWORD_SENTINEL:?}"
        require_env_line 'FLYWAY_CONNECT_RETRIES=10'
        if [[ -z "${TEST_EXPECT_DIRECT_URL:-}" ]]; then
            python3 - <<'PY'
import socket

with socket.create_connection(("127.0.0.1", 15432), timeout=1):
    pass
PY
        fi
        touch "$LOG_DIR/flyway-called"
        ;;
    postgres:16-alpine)
        if [[ -n "${TEST_EXPECT_DIRECT_URL:-}" ]]; then
            require_env_line "PGHOST=${EXPECTED_PGHOST:?}"
            require_env_line "PGPORT=${EXPECTED_PGPORT:?}"
            if [[ -n "${EXPECTED_PGSSLMODE:-}" ]]; then
                require_env_line "PGSSLMODE=${EXPECTED_PGSSLMODE}"
            else
                require_env_key_absent 'PGSSLMODE'
            fi
            if [[ -n "${EXPECTED_PGCHANNELBINDING:-}" ]]; then
                require_env_line "PGCHANNELBINDING=${EXPECTED_PGCHANNELBINDING}"
            else
                require_env_key_absent 'PGCHANNELBINDING'
            fi
        else
            require_env_line 'PGHOST=127.0.0.1'
            require_env_line 'PGPORT=15432'
        fi
        require_env_line 'PGDATABASE=logdate'
        require_env_line 'PGUSER=migration-user'
        require_env_line "PGPASSWORD=${DATABASE_PASSWORD_SENTINEL:?}"
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

assert_direct_neon_path_did_not_start_proxy() {
    local log_dir="$1"

    for path in \
        "$log_dir/curl.log" \
        "$log_dir/proxy.log" \
        "$log_dir/proxy-pid"; do
        [[ ! -e "$path" ]] || fail "direct Neon migration must not create $path"
        pass
    done
}

assert_rejected_before_database_clients() {
    local log_dir="$1"

    for path in \
        "$log_dir/docker.log" \
        "$log_dir/flyway-called" \
        "$log_dir/psql-called"; do
        [[ ! -e "$path" ]] || fail "invalid database URL must not create $path"
        pass
    done
    assert_direct_neon_path_did_not_start_proxy "$log_dir"
}

run_direct_url_success_case() {
    local name="$1" url="$2" expected_flyway_url="$3" expected_host="$4"
    local expected_port="$5" expected_sslmode="$6" expected_channel_binding="$7"
    local log_dir="$TMP_DIR/logs-legacy-$name"
    local output_file="$TMP_DIR/migrations-legacy-$name.out"
    local output status security_evidence env_path

    mkdir -p "$log_dir"
    set +e
    PATH="$FAKE_BIN:$PATH" \
    TEST_LOG_DIR="$log_dir" \
    DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
    TEST_DATABASE_URL="$url" \
    TEST_EXPECT_DIRECT_URL=1 \
    EXPECTED_FLYWAY_URL="$expected_flyway_url" \
    EXPECTED_PGHOST="$expected_host" \
    EXPECTED_PGPORT="$expected_port" \
    EXPECTED_PGSSLMODE="$expected_sslmode" \
    EXPECTED_PGCHANNELBINDING="$expected_channel_binding" \
    bash -x "$SCRIPT" \
        --contract-file "$CONTRACT_FILE" \
        --environment staging \
        --validate-passkey-fk >"$output_file" 2>&1
    status=$?
    set -e

    output="$(cat "$output_file")"
    if [[ "$status" != "0" ]]; then
        echo "$output"
    fi
    assert_exit_code 0 "$status"
    assert_contains 'Migration target: pinned runtime DATABASE_URL secret.' "$output"
    assert_contains '[access][17][--secret=logdate-db-url][--project=logdate-contract-test]' "$(cat "$log_dir/gcloud.log")"
    [[ -f "$log_dir/flyway-called" ]] || fail "expected Flyway container to run for $name"
    pass
    [[ -f "$log_dir/psql-called" ]] || fail "expected PostgreSQL validation container to run for $name"
    pass
    assert_direct_neon_path_did_not_start_proxy "$log_dir"

    security_evidence="$(cat "$output_file" "$log_dir/gcloud.log" "$log_dir/docker.log")"
    assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$security_evidence"
    assert_not_contains 'migration-user' "$security_evidence"
    assert_not_contains "$url" "$security_evidence"
    while IFS= read -r env_path; do
        [[ ! -e "$env_path" ]] || fail "expected trap to remove $env_path"
        pass
    done <"$log_dir/env-paths.log"
}

run_invalid_direct_url_case() {
    local name="$1" url="$2"
    local log_dir="$TMP_DIR/logs-legacy-invalid-$name"
    local output_file="$TMP_DIR/migrations-legacy-invalid-$name.out"
    local output status security_evidence

    mkdir -p "$log_dir"
    set +e
    PATH="$FAKE_BIN:$PATH" \
    TEST_LOG_DIR="$log_dir" \
    DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
    TEST_DATABASE_URL="$url" \
    bash "$SCRIPT" \
        --contract-file "$CONTRACT_FILE" \
        --environment staging \
        --validate-passkey-fk >"$output_file" 2>&1
    status=$?
    set -e

    output="$(cat "$output_file")"
    assert_exit_code 1 "$status"
    assert_rejected_before_database_clients "$log_dir"
    security_evidence="$(cat "$output_file" "$log_dir/gcloud.log")"
    assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$security_evidence"
    assert_not_contains "$url" "$security_evidence"
}

set +e
PATH="$FAKE_BIN:$PATH" \
TEST_LOG_DIR="$LOG_DIR" \
DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
TEST_EXPECT_DIRECT_URL=1 \
EXPECTED_FLYWAY_URL="jdbc:postgresql://contract-neon.example.test/logdate?sslmode=verify-full&channelBinding=require" \
EXPECTED_PGHOST="contract-neon.example.test" \
EXPECTED_PGPORT="5432" \
EXPECTED_PGSSLMODE="verify-full" \
EXPECTED_PGCHANNELBINDING="require" \
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
assert_contains 'Migration target: pinned runtime DATABASE_URL secret.' "$output"
assert_contains 'Flyway migrate complete.' "$output"
assert_contains 'Passkey FK validated.' "$output"
assert_contains '[access][17][--secret=logdate-db-url][--project=logdate-contract-test]' "$(cat "$LOG_DIR/gcloud.log")"
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

security_evidence="$(cat "$OUTPUT_FILE" "$LOG_DIR/gcloud.log" "$LOG_DIR/docker.log")"
assert_not_contains "$DATABASE_PASSWORD_SENTINEL" "$security_evidence"
assert_not_contains 'migration-user' "$security_evidence"

while IFS= read -r env_path; do
    [[ ! -e "$env_path" ]] || fail "expected trap to remove $env_path"
    pass
done <"$LOG_DIR/env-paths.log"
assert_direct_neon_path_did_not_start_proxy "$LOG_DIR"

while IFS='|' read -r name url expected_flyway_url expected_host expected_port expected_sslmode expected_channel_binding; do
    [[ -n "$name" ]] || continue
    run_direct_url_success_case \
        "$name" \
        "$url" \
        "$expected_flyway_url" \
        "$expected_host" \
        "$expected_port" \
        "$expected_sslmode" \
        "$expected_channel_binding"
done <<'EOF'
jdbc-ipv6|jdbc:postgresql://[2001:db8::7]:6543/logdate?sslmode=verify-full&channelBinding=require|jdbc:postgresql://[2001:db8::7]:6543/logdate?sslmode=verify-full&channelBinding=require|2001:db8::7|6543|verify-full|require
postgres-normalization|postgres://legacy-neon.example.test/logdate?sslmode=require|jdbc:postgresql://legacy-neon.example.test/logdate?sslmode=require|legacy-neon.example.test|5432|require|
postgresql-normalization|postgresql://legacy-neon.example.test/logdate?channelBinding=prefer|jdbc:postgresql://legacy-neon.example.test/logdate?channelBinding=prefer|legacy-neon.example.test|5432||prefer
EOF

invalid_direct_url_cases=(
    'userinfo-credentials|jdbc:postgresql://migration-user:embedded-password@legacy-neon.example.test/logdate?sslmode=require'
    'query-user|jdbc:postgresql://legacy-neon.example.test/logdate?user=attacker'
    'query-username|jdbc:postgresql://legacy-neon.example.test/logdate?username=attacker'
    'query-password|jdbc:postgresql://legacy-neon.example.test/logdate?password=attacker'
    'missing-host|jdbc:postgresql:///logdate?sslmode=require'
    'multi-host|jdbc:postgresql://first.example.test,second.example.test/logdate?sslmode=require'
    'malformed-port|jdbc:postgresql://legacy-neon.example.test:not-a-port/logdate?sslmode=require'
    'zero-port|jdbc:postgresql://legacy-neon.example.test:0/logdate?sslmode=require'
    'unsafe-database-path|jdbc:postgresql://legacy-neon.example.test/logdate/other?sslmode=require'
    'fragment|jdbc:postgresql://legacy-neon.example.test/logdate?sslmode=require#fragment'
    $'tab|jdbc:postgresql://legacy-neon.example.test/logdate\t?sslmode=require'
    $'newline|jdbc:postgresql://legacy-neon.example.test/logdate\n?sslmode=require'
    'duplicate-parameter|jdbc:postgresql://legacy-neon.example.test/logdate?sslmode=require&sslmode=verify-full'
    'unsupported-parameter|jdbc:postgresql://legacy-neon.example.test/logdate?applicationName=unsafe'
    'invalid-sslmode|jdbc:postgresql://legacy-neon.example.test/logdate?sslmode=unsafe'
    'invalid-channel-binding|jdbc:postgresql://legacy-neon.example.test/logdate?channelBinding=unsafe'
)

for case_definition in "${invalid_direct_url_cases[@]}"; do
    name="${case_definition%%|*}"
    url="${case_definition#*|}"
    run_invalid_direct_url_case "$name" "$url"
done

set +e
missing_legacy_url_output="$(
    TEST_LOG_DIR="$MISSING_LEGACY_LOG_DIR" \
    TEST_MISSING_DATABASE_URL=1 \
    DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
    PATH="$FAKE_BIN:$PATH" \
    "$SCRIPT" \
        --contract-file "$CONTRACT_FILE" \
        --environment staging 2>&1
)"
missing_legacy_url_status=$?
malformed_legacy_url_output="$(
    TEST_LOG_DIR="$MALFORMED_LEGACY_LOG_DIR" \
    TEST_MALFORMED_DATABASE_URL=1 \
    DATABASE_PASSWORD_SENTINEL="$DATABASE_PASSWORD_SENTINEL" \
    PATH="$FAKE_BIN:$PATH" \
    "$SCRIPT" \
        --contract-file "$CONTRACT_FILE" \
        --environment staging 2>&1
)"
malformed_legacy_url_status=$?
set -e

assert_exit_code 1 "$missing_legacy_url_status"
assert_contains 'pinned runtime database URL secret is unavailable' "$missing_legacy_url_output"
[[ ! -e "$MISSING_LEGACY_LOG_DIR/docker.log" ]] || fail "missing legacy URL must fail before Docker"
pass
[[ ! -e "$MISSING_LEGACY_LOG_DIR/curl.log" ]] || fail "missing legacy URL must not fall back to Cloud SQL"
pass
assert_exit_code 1 "$malformed_legacy_url_status"
assert_contains 'database URL must be a single-line PostgreSQL JDBC URL' "$malformed_legacy_url_output"
[[ ! -e "$MALFORMED_LEGACY_LOG_DIR/docker.log" ]] || fail "malformed legacy URL must fail before Docker"
pass
[[ ! -e "$MALFORMED_LEGACY_LOG_DIR/curl.log" ]] || fail "malformed legacy URL must not start Cloud SQL"
pass

workflow_contents="$(cat .github/workflows/deploy-server-cloud-run.yml)"
assert_contains '--contract-file "$CONTRACT_FILE"' "$workflow_contents"
assert_contains '--environment "$ENVIRONMENT"' "$workflow_contents"
assert_not_contains '--legacy-config' "$workflow_contents"
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
assert_contains 'Cloud SQL compatibility mode has been removed' "$flag_override_output"
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
assert_contains 'DATABASE_URL secret ID must be a non-empty single-line string' "$alternate_shape_output"

help_output="$("$SCRIPT" --help)"
assert_contains '--contract-file PATH' "$help_output"
assert_contains '--environment NAME' "$help_output"
assert_not_contains 'v2.21.3' "$help_output"
assert_not_contains 'Cloud SQL' "$help_output"
assert_contains 'flyway/flyway:12.4.0' "$help_output"

print_pass_summary "run-migrations"
