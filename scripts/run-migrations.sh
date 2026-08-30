#!/usr/bin/env bash
set -euo pipefail
umask 077

# Run Flyway migrations for a rendered deployment contract. The contract pins
# the same Neon secret versions the Cloud Run candidate mounts, so migrations
# and the runtime can never address different databases.

readonly FLYWAY_IMAGE="flyway/flyway:12.4.0"
readonly POSTGRES_IMAGE="postgres:16-alpine"

CONTRACT_FILE=""
REQUESTED_ENVIRONMENT=""
VALIDATE_PASSKEY_FK="false"
WORKDIR=""

log() {
    printf '%s\n' "$*"
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Run Flyway migrations against the database selected by a rendered deployment contract.

Inputs:
  --contract-file PATH          Rendered deployment contract (required)
  --environment NAME           Expected contract environment (required)
  --validate-passkey-fk        Validate passkeys.account_id_fkey after migrations
  --help, -h                    Show this help

Pinned tools:
  flyway/flyway:12.4.0
  postgres:16-alpine

The contract supplies project_id and exact numeric DATABASE_URL,
DATABASE_USER, and DATABASE_PASSWORD Secret Manager versions. Independent
database and secret overrides are rejected.
EOF
}

cleanup() {
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

write_database_env_files() {
    local user_file="$1" password_file="$2" jdbc_url_file="$3" flyway_file="$4" psql_file="$5"
    python3 - "$user_file" "$password_file" "$jdbc_url_file" "$flyway_file" "$psql_file" <<'PY'
import ipaddress
import os
import pathlib
import re
import sys
import urllib.parse

user_path, password_path, jdbc_url_path, flyway_path, psql_path = sys.argv[1:]
user = pathlib.Path(user_path).read_text()
password = pathlib.Path(password_path).read_text()
raw_url = pathlib.Path(jdbc_url_path).read_text()
if not user or not password or any(character in user + password for character in "\r\n"):
    raise SystemExit("database secrets must be non-empty single-line values")
if not raw_url or raw_url != raw_url.strip() or any(character in raw_url for character in "\r\n\t"):
    raise SystemExit("database URL must be a single-line PostgreSQL JDBC URL")

candidate = raw_url.removeprefix("jdbc:")
try:
    parsed = urllib.parse.urlsplit(candidate)
    host = parsed.hostname
    port = parsed.port
except ValueError:
    raise SystemExit("database URL authority is malformed") from None
if port is not None and not 1 <= port <= 65535:
    raise SystemExit("database URL authority is malformed")
if (
    parsed.scheme not in {"postgres", "postgresql"}
    or not host
    or parsed.username is not None
    or parsed.password is not None
    or parsed.fragment
    or "," in parsed.netloc
):
    raise SystemExit("database URL must name one PostgreSQL host without embedded credentials")
try:
    ipaddress.ip_address(host)
except ValueError:
    labels = host.split(".")
    if any(not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label) for label in labels):
        raise SystemExit("database URL host is malformed") from None

database_name = urllib.parse.unquote(parsed.path.removeprefix("/"))
if not re.fullmatch(r"[A-Za-z0-9_.-]+", database_name):
    raise SystemExit("database URL must name exactly one safe database")

try:
    query_pairs = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True, strict_parsing=True)
except ValueError:
    raise SystemExit("database URL query is malformed") from None
supported_query_keys = {"sslmode", "channelBinding"}
if any(key.lower() in {"user", "username", "password"} for key, _ in query_pairs):
    raise SystemExit("database URL query must not contain credentials")
if any(key not in supported_query_keys for key, _ in query_pairs):
    raise SystemExit("database URL contains an unsupported connection parameter")
if len({key for key, _ in query_pairs}) != len(query_pairs):
    raise SystemExit("database URL connection parameters must not repeat")
query = dict(query_pairs)
sslmode = query.get("sslmode")
if sslmode is not None and sslmode not in {
    "disable", "allow", "prefer", "require", "verify-ca", "verify-full"
}:
    raise SystemExit("database URL sslmode is invalid")
channel_binding = query.get("channelBinding")
if channel_binding is not None and channel_binding not in {"disable", "prefer", "require"}:
    raise SystemExit("database URL channelBinding is invalid")

authority_host = f"[{host}]" if ":" in host else host
authority = authority_host if port is None else f"{authority_host}:{port}"
normalized_query = urllib.parse.urlencode(query_pairs)
jdbc_url = f"jdbc:postgresql://{authority}/{database_name}"
if normalized_query:
    jdbc_url = f"{jdbc_url}?{normalized_query}"

pathlib.Path(flyway_path).write_text(
    f"FLYWAY_URL={jdbc_url}\n"
    f"FLYWAY_USER={user}\n"
    f"FLYWAY_PASSWORD={password}\n"
    "FLYWAY_CONNECT_RETRIES=10\n"
)
lines = [
    f"PGHOST={host}",
    f"PGPORT={port or 5432}",
    f"PGDATABASE={database_name}",
    f"PGUSER={user}",
    f"PGPASSWORD={password}",
]
if sslmode is not None:
    lines.append(f"PGSSLMODE={sslmode}")
if channel_binding is not None:
    lines.append(f"PGCHANNELBINDING={channel_binding}")
pathlib.Path(psql_path).write_text("\n".join(lines) + "\n")
os.chmod(flyway_path, 0o600)
os.chmod(psql_path, 0o600)
PY
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --contract-file)
            require_arg_value "$1" "${2:-}"
            CONTRACT_FILE="$2"
            shift 2
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
        --legacy-config|--project-id|--region|--instance-name|--database-name)
            die "Cloud SQL compatibility mode has been removed; pass --contract-file and --environment ($1)."
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

[[ -n "$CONTRACT_FILE" ]] || die "--contract-file is required."
[[ -n "$REQUESTED_ENVIRONMENT" ]] || die "--environment is required."
[[ -f "$CONTRACT_FILE" ]] || die "contract file not found: $CONTRACT_FILE"
reject_independent_environment_overrides

for cmd in docker gcloud python3; do
    command -v "$cmd" >/dev/null 2>&1 || die "Missing required command: $cmd"
done

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    die "gcloud auth required (use workflow identity or Application Default Credentials)."
fi

WORKDIR="$(mktemp -d)"
chmod 700 "$WORKDIR"
trap cleanup EXIT

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
if not isinstance(plain_env, dict) or "INSTANCE_CONNECTION_NAME" in plain_env or "DB_NAME" in plain_env:
    raise SystemExit("contract must not contain Cloud SQL database fields")

def secret_mapping(name):
    mapping = secret_env.get(name) or {}
    secret_id = require_string(mapping.get("secret_id"), f"{name} secret ID")
    version = mapping.get("version")
    if not isinstance(version, str) or not re.fullmatch(r"[1-9][0-9]*", version):
        raise SystemExit(f"{name} secret version must be an exact numeric version")
    return secret_id, version

url_secret_id, url_secret_version = secret_mapping("DATABASE_URL")
user_secret_id, user_secret_version = secret_mapping("DATABASE_USER")
password_secret_id, password_secret_version = secret_mapping("DATABASE_PASSWORD")
for value in (
    environment,
    project_id,
    url_secret_id,
    url_secret_version,
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
URL_SECRET_ID="$(sed -n '3p' "$PARSED_CONTRACT")"
URL_SECRET_VERSION="$(sed -n '4p' "$PARSED_CONTRACT")"
USER_SECRET_ID="$(sed -n '5p' "$PARSED_CONTRACT")"
USER_SECRET_VERSION="$(sed -n '6p' "$PARSED_CONTRACT")"
PASSWORD_SECRET_ID="$(sed -n '7p' "$PARSED_CONTRACT")"
PASSWORD_SECRET_VERSION="$(sed -n '8p' "$PARSED_CONTRACT")"

if [[ "$CONTRACT_ENVIRONMENT" != "$REQUESTED_ENVIRONMENT" ]]; then
    die "contract environment '$CONTRACT_ENVIRONMENT' does not match requested environment '$REQUESTED_ENVIRONMENT'."
fi

DB_USER_FILE="$WORKDIR/database-user"
DB_PASSWORD_FILE="$WORKDIR/database-password"
JDBC_URL_FILE="$WORKDIR/database-url"
gcloud secrets versions access "$USER_SECRET_VERSION" \
    --secret="$USER_SECRET_ID" \
    --project="$PROJECT_ID" >"$DB_USER_FILE"
gcloud secrets versions access "$PASSWORD_SECRET_VERSION" \
    --secret="$PASSWORD_SECRET_ID" \
    --project="$PROJECT_ID" >"$DB_PASSWORD_FILE"
chmod 600 "$DB_USER_FILE" "$DB_PASSWORD_FILE"
[[ -s "$DB_USER_FILE" && -s "$DB_PASSWORD_FILE" ]] || die "database credential secrets must not be empty."

if ! gcloud secrets versions access "$URL_SECRET_VERSION" \
    --secret="$URL_SECRET_ID" \
    --project="$PROJECT_ID" >"$JDBC_URL_FILE"; then
    die "pinned runtime database URL secret is unavailable; refusing to migrate a different target."
fi
chmod 600 "$JDBC_URL_FILE"
[[ -s "$JDBC_URL_FILE" ]] || die "pinned runtime database URL secret must not be empty."
log "Migration target: pinned runtime DATABASE_URL secret."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/server/src/main/resources/db/migration"
[[ -d "$MIGRATIONS_DIR" ]] || die "Migrations directory not found: $MIGRATIONS_DIR"

FLYWAY_ENV_FILE="$WORKDIR/flyway.env"
PSQL_ENV_FILE="$WORKDIR/psql.env"
write_database_env_files \
    "$DB_USER_FILE" \
    "$DB_PASSWORD_FILE" \
    "$JDBC_URL_FILE" \
    "$FLYWAY_ENV_FILE" \
    "$PSQL_ENV_FILE"

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

log "Migrations complete for the pinned runtime database target."
