#!/usr/bin/env bash
set -euo pipefail
umask 077

# Run Flyway migrations through a pinned Cloud SQL Auth Proxy. The deployment
# contract is the only source of database identity and exact secret versions;
# official callers cannot independently select a project, instance, database,
# or credential secret.

readonly CLOUD_SQL_PROXY_VERSION="v2.21.3"
readonly FLYWAY_IMAGE="flyway/flyway:12.4.0"
readonly POSTGRES_IMAGE="postgres:16-alpine"

CONTRACT_FILE=""
REQUESTED_ENVIRONMENT=""
LEGACY_CONFIG="false"
LEGACY_PROJECT_ID=""
LEGACY_REGION=""
LEGACY_INSTANCE_NAME="logdate-db"
LEGACY_DATABASE_NAME="logdate"
INDEPENDENT_SELECTOR_USED="false"
PROXY_PORT="${PROXY_PORT:-15432}"
VALIDATE_PASSKEY_FK="false"
WORKDIR=""
PROXY_PID=""

log() {
    printf '%s\n' "$*"
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Run Flyway migrations against the Cloud SQL database in a rendered deployment contract.

Inputs:
  --contract-file PATH          Rendered deployment contract (required)
  --environment NAME           Expected contract environment (required)
  --legacy-config              Temporary explicit compatibility mode for the
                               current workflow's non-secret target selectors
  --project-id PROJECT_ID      Legacy mode only
  --region REGION              Legacy mode only
  --instance-name NAME         Legacy mode only (default: logdate-db)
  --database-name NAME         Legacy mode only (default: logdate)
  --validate-passkey-fk        Validate passkeys.account_id_fkey after migrations
  --help, -h                    Show this help

Pinned tools:
  Cloud SQL Auth Proxy v2.21.3
  flyway/flyway:12.4.0
  postgres:16-alpine

The contract supplies project_id, INSTANCE_CONNECTION_NAME, DB_NAME, and exact
numeric DATABASE_USER/DATABASE_PASSWORD Secret Manager versions. Independent
database, instance, and secret overrides are rejected.
EOF
}

cleanup() {
    if [[ -n "$PROXY_PID" ]] && kill -0 "$PROXY_PID" 2>/dev/null; then
        kill "$PROXY_PID" 2>/dev/null || true
        wait "$PROXY_PID" 2>/dev/null || true
    fi
    if [[ -n "$WORKDIR" ]]; then
        rm -rf "$WORKDIR"
    fi
}

require_arg_value() {
    local flag="$1" value="${2:-}"
    if [[ -z "$value" || "$value" == --* ]]; then
        die "$flag requires a value."
    fi
}

reject_independent_environment_overrides() {
    local name
    for name in \
        PROJECT_ID REGION INSTANCE_NAME DATABASE_NAME \
        URL_SECRET_ID USER_SECRET_ID PASSWORD_SECRET_ID \
        DATABASE_URL INSTANCE_CONNECTION_NAME DB_NAME \
        DATABASE_USER DATABASE_PASSWORD; do
        if [[ -n "${!name:-}" ]]; then
            die "independent database overrides are not supported with --contract-file (unset $name)."
        fi
    done
}

validate_legacy_environment() {
    if [[ -n "${PROJECT_ID:-}" && "$PROJECT_ID" != "$LEGACY_PROJECT_ID" ]]; then
        die "legacy PROJECT_ID environment does not match --project-id."
    fi
    if [[ -n "${REGION:-}" && "$REGION" != "$LEGACY_REGION" ]]; then
        die "legacy REGION environment does not match --region."
    fi

    local name
    for name in \
        INSTANCE_NAME DATABASE_NAME URL_SECRET_ID USER_SECRET_ID PASSWORD_SECRET_ID \
        DATABASE_URL INSTANCE_CONNECTION_NAME DB_NAME DATABASE_USER DATABASE_PASSWORD; do
        if [[ -n "${!name:-}" ]]; then
            die "independent database overrides are not supported in --legacy-config mode (unset $name)."
        fi
    done
}

download_proxy() {
    local output="$1"
    local proxy_os="linux" proxy_arch="amd64"

    case "$(uname -s)" in
        Darwin) proxy_os="darwin" ;;
        Linux) proxy_os="linux" ;;
        *) die "Unsupported OS for Cloud SQL Auth Proxy: $(uname -s)" ;;
    esac
    case "$(uname -m)" in
        arm64|aarch64) proxy_arch="arm64" ;;
        x86_64) proxy_arch="amd64" ;;
        *) die "Unsupported architecture for Cloud SQL Auth Proxy: $(uname -m)" ;;
    esac

    local url="https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/${CLOUD_SQL_PROXY_VERSION}/cloud-sql-proxy.${proxy_os}.${proxy_arch}"
    log "Downloading Cloud SQL Auth Proxy ${CLOUD_SQL_PROXY_VERSION}..."
    curl \
        --fail \
        --location \
        --show-error \
        --silent \
        --retry 5 \
        --retry-delay 2 \
        --retry-all-errors \
        --connect-timeout 10 \
        --max-time 120 \
        "$url" \
        --output "$output"
    chmod 700 "$output"
}

write_flyway_env_file() {
    local user_file="$1" password_file="$2" output_file="$3" jdbc_url="$4"
    python3 - "$user_file" "$password_file" "$output_file" "$jdbc_url" <<'PY'
import os
import pathlib
import sys

user_path, password_path, output_path, jdbc_url = sys.argv[1:]
user = pathlib.Path(user_path).read_text()
password = pathlib.Path(password_path).read_text()
if not user or not password or any(character in user + password for character in "\r\n"):
    raise SystemExit("database secrets must be non-empty single-line values")
pathlib.Path(output_path).write_text(
    f"FLYWAY_URL={jdbc_url}\n"
    f"FLYWAY_USER={user}\n"
    f"FLYWAY_PASSWORD={password}\n"
    "FLYWAY_CONNECT_RETRIES=10\n"
)
os.chmod(output_path, 0o600)
PY
}

write_psql_env_file() {
    local user_file="$1" password_file="$2" output_file="$3" database_name="$4"
    python3 - "$user_file" "$password_file" "$output_file" "$database_name" "$PROXY_PORT" <<'PY'
import os
import pathlib
import sys

user_path, password_path, output_path, database_name, port = sys.argv[1:]
user = pathlib.Path(user_path).read_text()
password = pathlib.Path(password_path).read_text()
if not user or not password or any(character in user + password for character in "\r\n"):
    raise SystemExit("database secrets must be non-empty single-line values")
pathlib.Path(output_path).write_text(
    "PGHOST=127.0.0.1\n"
    f"PGPORT={port}\n"
    f"PGDATABASE={database_name}\n"
    f"PGUSER={user}\n"
    f"PGPASSWORD={password}\n"
)
os.chmod(output_path, 0o600)
PY
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --contract-file)
            require_arg_value "$1" "${2:-}"
            CONTRACT_FILE="$2"
            shift 2
            ;;
        --legacy-config)
            LEGACY_CONFIG="true"
            shift
            ;;
        --environment)
            require_arg_value "$1" "${2:-}"
            REQUESTED_ENVIRONMENT="$2"
            shift 2
            ;;
        --validate-passkey-fk)
            VALIDATE_PASSKEY_FK="true"
            shift
            ;;
        --project-id)
            require_arg_value "$1" "${2:-}"
            LEGACY_PROJECT_ID="$2"
            INDEPENDENT_SELECTOR_USED="true"
            shift 2
            ;;
        --region)
            require_arg_value "$1" "${2:-}"
            LEGACY_REGION="$2"
            INDEPENDENT_SELECTOR_USED="true"
            shift 2
            ;;
        --instance-name)
            require_arg_value "$1" "${2:-}"
            LEGACY_INSTANCE_NAME="$2"
            INDEPENDENT_SELECTOR_USED="true"
            shift 2
            ;;
        --database-name)
            require_arg_value "$1" "${2:-}"
            LEGACY_DATABASE_NAME="$2"
            INDEPENDENT_SELECTOR_USED="true"
            shift 2
            ;;
        --url-secret|--user-secret|--password-secret)
            die "independent secret selectors are not supported ($1)."
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

if [[ "$LEGACY_CONFIG" == "true" ]]; then
    [[ -z "$CONTRACT_FILE" ]] || die "--legacy-config cannot be combined with --contract-file."
    [[ -z "$REQUESTED_ENVIRONMENT" ]] || die "--legacy-config cannot be combined with --environment."
    [[ -n "$LEGACY_PROJECT_ID" && -n "$LEGACY_REGION" ]] || die "--legacy-config requires --project-id and --region."
    validate_legacy_environment
else
    [[ -n "$CONTRACT_FILE" ]] || die "--contract-file is required."
    [[ -n "$REQUESTED_ENVIRONMENT" ]] || die "--environment is required."
    [[ -f "$CONTRACT_FILE" ]] || die "contract file not found: $CONTRACT_FILE"
    if [[ "$INDEPENDENT_SELECTOR_USED" == "true" ]]; then
        die "independent database overrides are not supported with --contract-file."
    fi
    reject_independent_environment_overrides
fi

if ! [[ "$PROXY_PORT" =~ ^[0-9]+$ ]] || (( PROXY_PORT < 1 || PROXY_PORT > 65535 )); then
    die "PROXY_PORT must be an integer from 1 to 65535."
fi

for cmd in curl docker gcloud python3; do
    command -v "$cmd" >/dev/null 2>&1 || die "Missing required command: $cmd"
done

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    die "gcloud auth required (use workflow identity or Application Default Credentials)."
fi

WORKDIR="$(mktemp -d)"
chmod 700 "$WORKDIR"
trap cleanup EXIT

if [[ "$LEGACY_CONFIG" == "true" ]]; then
    PROJECT_ID="$LEGACY_PROJECT_ID"
    INSTANCE_CONNECTION_NAME="${LEGACY_PROJECT_ID}:${LEGACY_REGION}:${LEGACY_INSTANCE_NAME}"
    DATABASE_NAME="$LEGACY_DATABASE_NAME"
    USER_SECRET_ID="logdate-db-user"
    USER_SECRET_VERSION="latest"
    PASSWORD_SECRET_ID="logdate-db-password"
    PASSWORD_SECRET_VERSION="latest"
    log "TEMPORARY COMPATIBILITY MODE: --legacy-config; Task 6 must remove this path."
else
    PARSED_CONTRACT="$WORKDIR/parsed-contract"
    python3 - "$CONTRACT_FILE" >"$PARSED_CONTRACT" <<'PY'
import json
import pathlib
import re
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text())

def require_string(value, label):
    if not isinstance(value, str) or not value or any(character in value for character in "\r\n\t"):
        raise SystemExit(f"{label} must be a non-empty single-line string")
    return value

environment = require_string(contract.get("environment"), "contract environment")
project_id = require_string(contract.get("project_id"), "contract project_id")
plain_env = contract.get("env_vars") or {}
secret_env = contract.get("secret_env") or {}
connection_name = require_string(plain_env.get("INSTANCE_CONNECTION_NAME"), "INSTANCE_CONNECTION_NAME")
database_name = require_string(plain_env.get("DB_NAME"), "DB_NAME")

parts = connection_name.split(":")
if len(parts) != 3 or parts[0] != project_id or not all(parts):
    raise SystemExit("INSTANCE_CONNECTION_NAME must be project:region:instance and match project_id")

def secret_mapping(name):
    mapping = secret_env.get(name) or {}
    secret_id = require_string(mapping.get("secret_id"), f"{name} secret ID")
    version = mapping.get("version")
    if not isinstance(version, str) or not re.fullmatch(r"[1-9][0-9]*", version):
        raise SystemExit(f"{name} secret version must be an exact numeric version")
    return secret_id, version

user_secret_id, user_secret_version = secret_mapping("DATABASE_USER")
password_secret_id, password_secret_version = secret_mapping("DATABASE_PASSWORD")
for value in (
    environment,
    project_id,
    connection_name,
    database_name,
    user_secret_id,
    user_secret_version,
    password_secret_id,
    password_secret_version,
):
    print(value)
PY
    chmod 600 "$PARSED_CONTRACT"

    CONTRACT_ENVIRONMENT="$(sed -n '1p' "$PARSED_CONTRACT")"
    PROJECT_ID="$(sed -n '2p' "$PARSED_CONTRACT")"
    INSTANCE_CONNECTION_NAME="$(sed -n '3p' "$PARSED_CONTRACT")"
    DATABASE_NAME="$(sed -n '4p' "$PARSED_CONTRACT")"
    USER_SECRET_ID="$(sed -n '5p' "$PARSED_CONTRACT")"
    USER_SECRET_VERSION="$(sed -n '6p' "$PARSED_CONTRACT")"
    PASSWORD_SECRET_ID="$(sed -n '7p' "$PARSED_CONTRACT")"
    PASSWORD_SECRET_VERSION="$(sed -n '8p' "$PARSED_CONTRACT")"

    if [[ "$CONTRACT_ENVIRONMENT" != "$REQUESTED_ENVIRONMENT" ]]; then
        die "contract environment '$CONTRACT_ENVIRONMENT' does not match requested environment '$REQUESTED_ENVIRONMENT'."
    fi
fi

log "Migration target: ${INSTANCE_CONNECTION_NAME} → ${DATABASE_NAME}"

DB_USER_FILE="$WORKDIR/database-user"
DB_PASSWORD_FILE="$WORKDIR/database-password"
gcloud secrets versions access "$USER_SECRET_VERSION" \
    --secret="$USER_SECRET_ID" \
    --project="$PROJECT_ID" >"$DB_USER_FILE"
gcloud secrets versions access "$PASSWORD_SECRET_VERSION" \
    --secret="$PASSWORD_SECRET_ID" \
    --project="$PROJECT_ID" >"$DB_PASSWORD_FILE"
chmod 600 "$DB_USER_FILE" "$DB_PASSWORD_FILE"
[[ -s "$DB_USER_FILE" && -s "$DB_PASSWORD_FILE" ]] || die "database credential secrets must not be empty."

PROXY_BIN="$WORKDIR/cloud-sql-proxy"
download_proxy "$PROXY_BIN"
log "Starting Cloud SQL Auth Proxy on 127.0.0.1:${PROXY_PORT}..."
"$PROXY_BIN" \
    --address 127.0.0.1 \
    --port "$PROXY_PORT" \
    "$INSTANCE_CONNECTION_NAME" >"$WORKDIR/proxy.log" 2>&1 &
PROXY_PID=$!

for _ in $(seq 1 30); do
    if (echo >"/dev/tcp/127.0.0.1/${PROXY_PORT}") 2>/dev/null; then
        break
    fi
    if ! kill -0 "$PROXY_PID" 2>/dev/null; then
        tail -n 40 "$WORKDIR/proxy.log" >&2 || true
        die "Cloud SQL Auth Proxy exited before accepting connections."
    fi
    sleep 1
done
if ! (echo >"/dev/tcp/127.0.0.1/${PROXY_PORT}") 2>/dev/null; then
    tail -n 40 "$WORKDIR/proxy.log" >&2 || true
    die "Cloud SQL Auth Proxy failed to become ready."
fi
log "Proxy ready."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/server/src/main/resources/db/migration"
[[ -d "$MIGRATIONS_DIR" ]] || die "Migrations directory not found: $MIGRATIONS_DIR"

FLYWAY_ENV_FILE="$WORKDIR/flyway.env"
JDBC_URL="jdbc:postgresql://127.0.0.1:${PROXY_PORT}/${DATABASE_NAME}"
write_flyway_env_file "$DB_USER_FILE" "$DB_PASSWORD_FILE" "$FLYWAY_ENV_FILE" "$JDBC_URL"

log "Running Flyway migrate (image: ${FLYWAY_IMAGE})..."
docker run --rm \
    --network host \
    --env-file "$FLYWAY_ENV_FILE" \
    --volume "${MIGRATIONS_DIR}:/flyway/sql:ro" \
    "$FLYWAY_IMAGE" \
    -baselineOnMigrate=false \
    -outOfOrder=false \
    info migrate info
log "Flyway migrate complete."

if [[ "$VALIDATE_PASSKEY_FK" == "true" ]]; then
    PSQL_ENV_FILE="$WORKDIR/psql.env"
    write_psql_env_file "$DB_USER_FILE" "$DB_PASSWORD_FILE" "$PSQL_ENV_FILE" "$DATABASE_NAME"
    log "Validating passkeys.account_id_fkey..."
    docker run --rm \
        --network host \
        --env-file "$PSQL_ENV_FILE" \
        "$POSTGRES_IMAGE" \
        psql \
        --no-psqlrc \
        --no-align \
        --tuples-only \
        --command "ALTER TABLE passkeys VALIDATE CONSTRAINT passkeys_account_id_fkey;"
    log "Passkey FK validated."
fi

log "Migrations complete for ${INSTANCE_CONNECTION_NAME}/${DATABASE_NAME}."
