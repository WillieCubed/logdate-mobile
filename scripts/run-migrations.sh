#!/usr/bin/env bash
set -euo pipefail

# Run Flyway migrations against a LogDate Cloud SQL instance via the Cloud
# SQL Auth Proxy. Designed to be invoked from the Deploy Server workflow
# *before* the Cloud Run revision rollout, so the new code never sees an
# old schema. Also runnable manually (with gcloud auth + a recent
# cloud-sql-proxy in PATH or via auto-download) for ad-hoc rescue.
#
# Inputs (env or flags, in order of precedence):
#   --project-id        GCP project ID (required)
#   --region            GCP region (required)
#   --instance-name     Cloud SQL instance name (default: logdate-db)
#   --database-name     Database name (default: logdate)
#   --url-secret        Secret Manager ID for JDBC URL (default: logdate-db-url)
#   --user-secret       Secret Manager ID for DB user (default: logdate-db-user)
#   --password-secret   Secret Manager ID for DB password (default: logdate-db-password)
#   --validate-passkey-fk   Run V18→V19 orphan-passkey constraint validation post-migrate.
#                           Idempotent (re-validating an already-valid constraint is a no-op).
#                           Recommended on first deploy after V19; harmless thereafter.
#
# Exit:
#   0 — migrations applied (or already at target)
#   non-zero — migration failed; deploy workflow should abort

DEFAULT_CLOUD_SQL_PROXY_VERSION="v2.21.3"
DEFAULT_FLYWAY_VERSION="12.4.0"
DEFAULT_POSTGRES_IMAGE="postgres:16-alpine"

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-}"
INSTANCE_NAME="${INSTANCE_NAME:-logdate-db}"
DATABASE_NAME="${DATABASE_NAME:-logdate}"
URL_SECRET_ID="${URL_SECRET_ID:-logdate-db-url}"
USER_SECRET_ID="${USER_SECRET_ID:-logdate-db-user}"
PASSWORD_SECRET_ID="${PASSWORD_SECRET_ID:-logdate-db-password}"
CLOUD_SQL_PROXY_VERSION="${CLOUD_SQL_PROXY_VERSION:-$DEFAULT_CLOUD_SQL_PROXY_VERSION}"
FLYWAY_VERSION="${FLYWAY_VERSION:-$DEFAULT_FLYWAY_VERSION}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-$DEFAULT_POSTGRES_IMAGE}"
PROXY_PORT="${PROXY_PORT:-15432}"
VALIDATE_PASSKEY_FK="false"
WORKDIR=""
PROXY_PID=""

log() {
    printf '%s\n' "$*"
}

usage() {
    cat <<'EOF'
Run Flyway migrations against a LogDate Cloud SQL instance via the Cloud SQL Auth Proxy.

Inputs:
  --project-id PROJECT_ID       GCP project ID (required)
  --region REGION              GCP region (required)
  --instance-name NAME          Cloud SQL instance name (default: logdate-db)
  --database-name NAME          Database name (default: logdate)
  --url-secret SECRET_ID        Secret Manager ID for JDBC URL (default: logdate-db-url)
  --user-secret SECRET_ID       Secret Manager ID for DB user (default: logdate-db-user)
  --password-secret SECRET_ID   Secret Manager ID for DB password (default: logdate-db-password)
  --validate-passkey-fk         Validate passkeys.account_id_fkey after migrations
  --help, -h                    Show this help

Environment overrides:
  CLOUD_SQL_PROXY_VERSION       Cloud SQL Auth Proxy version (default: v2.21.3)
  FLYWAY_VERSION                Flyway Docker image version (default: 12.4.0)
  POSTGRES_IMAGE                Postgres image for FK validation (default: postgres:16-alpine)
  PROXY_PORT                    Local Cloud SQL Auth Proxy port (default: 15432)
EOF
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_arg_value() {
    local flag="$1"
    local value="${2:-}"
    if [[ -z "$value" || "$value" == --* ]]; then
        die "$flag requires a value."
    fi
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

download_with_retries() {
    local url="$1"
    local output="$2"

    log "Downloading Cloud SQL Auth Proxy ${CLOUD_SQL_PROXY_VERSION} from ${url}..."
    if ! curl \
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
        -o "$output"; then
        die "Failed to download Cloud SQL Auth Proxy ${CLOUD_SQL_PROXY_VERSION}. Verify CLOUD_SQL_PROXY_VERSION and URL: ${url}"
    fi
}

secret_value_or_empty() {
    local secret_id="$1"
    local value

    if value="$(gcloud secrets versions access latest --secret "$secret_id" --project "$PROJECT_ID" 2>/dev/null)"; then
        printf '%s' "$value"
    fi
}

psql_url_from_jdbc_url() {
    local jdbc_url="$1"
    printf '%s\n' "${jdbc_url#jdbc:}" |
        sed -E \
            -e 's/([?&])channelBinding=[^&]*&/\1/' \
            -e 's/([?&])channelBinding=[^&]*$//' \
            -e 's/\?&/?/' \
            -e 's/[?&]$//'
}

proxy_download_url() {
    local proxy_os="linux"
    local proxy_arch="amd64"

    case "$(uname -s)" in
        Darwin) proxy_os="darwin" ;;
        Linux) proxy_os="linux" ;;
        *) die "Unsupported OS for proxy auto-download: $(uname -s)" ;;
    esac

    case "$(uname -m)" in
        arm64|aarch64) proxy_arch="arm64" ;;
        x86_64) proxy_arch="amd64" ;;
        *) die "Unsupported architecture for proxy auto-download: $(uname -m)" ;;
    esac

    printf 'https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/%s/cloud-sql-proxy.%s.%s\n' \
        "$CLOUD_SQL_PROXY_VERSION" \
        "$proxy_os" \
        "$proxy_arch"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project-id) require_arg_value "$1" "${2:-}"; PROJECT_ID="$2"; shift 2 ;;
        --region) require_arg_value "$1" "${2:-}"; REGION="$2"; shift 2 ;;
        --instance-name) require_arg_value "$1" "${2:-}"; INSTANCE_NAME="$2"; shift 2 ;;
        --database-name) require_arg_value "$1" "${2:-}"; DATABASE_NAME="$2"; shift 2 ;;
        --url-secret) require_arg_value "$1" "${2:-}"; URL_SECRET_ID="$2"; shift 2 ;;
        --user-secret) require_arg_value "$1" "${2:-}"; USER_SECRET_ID="$2"; shift 2 ;;
        --password-secret) require_arg_value "$1" "${2:-}"; PASSWORD_SECRET_ID="$2"; shift 2 ;;
        --validate-passkey-fk) VALIDATE_PASSKEY_FK="true"; shift ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

if [[ -z "$PROJECT_ID" || -z "$REGION" ]]; then
    die "--project-id and --region are required."
fi

if ! [[ "$PROXY_PORT" =~ ^[0-9]+$ ]] || (( PROXY_PORT < 1 || PROXY_PORT > 65535 )); then
    die "PROXY_PORT must be an integer from 1 to 65535."
fi

for cmd in curl docker gcloud; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        die "Missing required command: $cmd"
    fi
done

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    die "gcloud auth required (run 'gcloud auth login' or rely on workflow auth)."
fi

INSTANCE_CONNECTION_NAME="${PROJECT_ID}:${REGION}:${INSTANCE_NAME}"
log "Migration target: ${INSTANCE_CONNECTION_NAME} → ${DATABASE_NAME}"

WORKDIR="$(mktemp -d)"
trap 'cleanup' EXIT

# Fetch DB user/password from Secret Manager. Production validator forbids
# embedded credentials in DATABASE_URL when using socket connections, so we
# read user + password from their dedicated secrets.
DB_URL="$(secret_value_or_empty "$URL_SECRET_ID")"
DB_USER="$(gcloud secrets versions access latest --secret "$USER_SECRET_ID" --project "$PROJECT_ID")"
DB_PASSWORD=$(gcloud secrets versions access latest --secret "$PASSWORD_SECRET_ID" --project "$PROJECT_ID")

if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
    die "Empty DB user or password from Secret Manager; verify secrets exist and the deploy identity has secretmanager.secretAccessor."
fi

if [[ -n "$DB_URL" ]]; then
    log "Using JDBC URL from Secret Manager secret ${URL_SECRET_ID}."
else
    log "Secret ${URL_SECRET_ID} is empty or unavailable; falling back to Cloud SQL Auth Proxy."

    # Download cloud-sql-proxy v2 if not present. Pinned version so reproducible
    # in CI; the proxy is small (~20 MB) and tolerates GitHub-hosted runner refreshes.
    PROXY_BIN="${WORKDIR}/cloud-sql-proxy"
    if ! command -v cloud-sql-proxy >/dev/null 2>&1; then
        URL="$(proxy_download_url)"
        download_with_retries "$URL" "$PROXY_BIN"
        chmod +x "$PROXY_BIN"
    else
        PROXY_BIN="cloud-sql-proxy"
        log "Using Cloud SQL Auth Proxy from PATH: $(command -v cloud-sql-proxy)"
    fi

    # Pick a free local port. cloud-sql-proxy v2 binds to 127.0.0.1 by default
    # and rotates port automatically only if requested; fix one explicitly so the
    # Flyway invocation below uses it deterministically.
    log "Starting Cloud SQL Auth Proxy on 127.0.0.1:${PROXY_PORT}..."
    "$PROXY_BIN" \
        --address 127.0.0.1 \
        --port "$PROXY_PORT" \
        "$INSTANCE_CONNECTION_NAME" \
        >"${WORKDIR}/proxy.log" 2>&1 &
    PROXY_PID=$!

    # Wait for proxy to accept connections (typically <2s).
    for _ in $(seq 1 30); do
        if (echo > /dev/tcp/127.0.0.1/"${PROXY_PORT}") 2>/dev/null; then
            break
        fi
        if ! kill -0 "$PROXY_PID" 2>/dev/null; then
            echo "Cloud SQL Auth Proxy exited before accepting connections. Tail of proxy log:" >&2
            tail -n 40 "${WORKDIR}/proxy.log" >&2 || true
            exit 1
        fi
        sleep 1
    done
    if ! (echo > /dev/tcp/127.0.0.1/"${PROXY_PORT}") 2>/dev/null; then
        echo "Cloud SQL Auth Proxy failed to start. Tail of proxy log:" >&2
        tail -n 40 "${WORKDIR}/proxy.log" >&2 || true
        exit 1
    fi
    log "Proxy ready."

    DB_URL="jdbc:postgresql://127.0.0.1:${PROXY_PORT}/${DATABASE_NAME}"
fi

# Resolve the migrations directory. The script lives at scripts/, so the
# repo root is one level up; migrations are under server/src/main/resources/db/migration.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/server/src/main/resources/db/migration"

if [[ ! -d "$MIGRATIONS_DIR" ]]; then
    die "Migrations directory not found: $MIGRATIONS_DIR"
fi

# Run Flyway via Docker. --network host so the container can reach 127.0.0.1
# where the proxy is listening.
FLYWAY_IMAGE="flyway/flyway:${FLYWAY_VERSION}"

log "Running Flyway migrate (image: ${FLYWAY_IMAGE})..."
docker run --rm \
    --network host \
    -v "${MIGRATIONS_DIR}:/flyway/sql:ro" \
    -e "FLYWAY_URL=${DB_URL}" \
    -e "FLYWAY_USER=${DB_USER}" \
    -e "FLYWAY_PASSWORD=${DB_PASSWORD}" \
    -e "FLYWAY_CONNECT_RETRIES=10" \
    "$FLYWAY_IMAGE" \
    -baselineOnMigrate=false \
    -outOfOrder=false \
    info migrate info

log "Flyway migrate complete."

# V18 dropped the passkeys → accounts FK to repair an orphan-window bug.
# V19 restores it as NOT VALID for instant rollout — existing rows aren't
# scanned. To finalize the constraint, run VALIDATE CONSTRAINT once after
# V19 is applied. Idempotent: validating an already-valid constraint is a no-op.
if [[ "$VALIDATE_PASSKEY_FK" == "true" ]]; then
    log "Validating passkeys.account_id_fkey..."
    PSQL_URL="$(psql_url_from_jdbc_url "$DB_URL")"
    docker run --rm \
        --network host \
        -e "PGPASSWORD=${DB_PASSWORD}" \
        -e "PSQL_URL=${PSQL_URL}" \
        "$POSTGRES_IMAGE" \
        psql \
        "${PSQL_URL}" \
        --username "${DB_USER}" \
        --no-psqlrc --no-align --tuples-only \
        --command "ALTER TABLE passkeys VALIDATE CONSTRAINT passkeys_account_id_fkey;"
    log "Passkey FK validated."
fi

log "Migrations complete for ${INSTANCE_CONNECTION_NAME}/${DATABASE_NAME}."
