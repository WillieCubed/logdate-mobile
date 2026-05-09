#!/usr/bin/env bash
# Regression tests for the Cloud SQL migration deploy helper.

set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/run-migrations.sh"
DEFAULT_PROXY_VERSION="v2.21.3"
DEFAULT_PROXY_URL="https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/${DEFAULT_PROXY_VERSION}/cloud-sql-proxy.linux.amd64"

script_contents="$(cat "$SCRIPT")"

assert_contains "DEFAULT_CLOUD_SQL_PROXY_VERSION=\"${DEFAULT_PROXY_VERSION}\"" "$script_contents"
assert_contains "CLOUD_SQL_PROXY_VERSION=\"\${CLOUD_SQL_PROXY_VERSION:-\$DEFAULT_CLOUD_SQL_PROXY_VERSION}\"" "$script_contents"
assert_contains "URL_SECRET_ID=\"\${URL_SECRET_ID:-logdate-db-url}\"" "$script_contents"
assert_contains "Using JDBC URL from Secret Manager secret" "$script_contents"
assert_contains "falling back to Cloud SQL Auth Proxy" "$script_contents"
assert_contains "FLYWAY_URL=\${DB_URL}" "$script_contents"
assert_contains "psql_url_from_jdbc_url" "$script_contents"
assert_contains "download_with_retries" "$script_contents"
assert_contains "--retry-all-errors" "$script_contents"
assert_not_contains "PROXY_VERSION=\"v2.13.2\"" "$script_contents"
assert_not_contains "cloud-sql-proxy/v2.13.2" "$script_contents"

help_output="$(./"$SCRIPT" --help)"
assert_contains "CLOUD_SQL_PROXY_VERSION" "$help_output"
assert_contains "--url-secret SECRET_ID" "$help_output"
assert_contains "default: v2.21.3" "$help_output"
assert_not_contains "DEFAULT_CLOUD_SQL_PROXY_VERSION=" "$help_output"

set +e
missing_arg_output="$(./"$SCRIPT" --project-id --region us-central1 2>&1)"
missing_arg_status=$?
set -e
assert_exit_code 1 "$missing_arg_status"
assert_contains "ERROR: --project-id requires a value." "$missing_arg_output"

set +e
invalid_port_output="$(PROXY_PORT=not-a-port ./"$SCRIPT" --project-id test-project --region us-central1 2>&1)"
invalid_port_status=$?
set -e
assert_exit_code 1 "$invalid_port_status"
assert_contains "ERROR: PROXY_PORT must be an integer from 1 to 65535." "$invalid_port_output"

status="$(curl -I -sS -o /dev/null -w '%{http_code}' "$DEFAULT_PROXY_URL")"
if [[ "$status" != "200" ]]; then
    fail "expected Cloud SQL Auth Proxy URL to return 200, got $status ($DEFAULT_PROXY_URL)"
fi
pass

print_pass_summary "run-migrations"
