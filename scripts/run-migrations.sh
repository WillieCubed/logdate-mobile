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
#   --user-secret       Secret Manager ID for DB user (default: logdate-db-user)
#   --password-secret   Secret Manager ID for DB password (default: logdate-db-password)
#   --validate-passkey-fk   Run V18→V19 orphan-passkey constraint validation post-migrate.
#                           Idempotent (re-validating an already-valid constraint is a no-op).
#                           Recommended on first deploy after V19; harmless thereafter.
#
# Exit:
#   0 — migrations applied (or already at target)
#   non-zero — migration failed; deploy workflow should abort

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-}"
INSTANCE_NAME="${INSTANCE_NAME:-logdate-db}"
DATABASE_NAME="${DATABASE_NAME:-logdate}"
USER_SECRET_ID="${USER_SECRET_ID:-logdate-db-user}"
PASSWORD_SECRET_ID="${PASSWORD_SECRET_ID:-logdate-db-password}"
VALIDATE_PASSKEY_FK="false"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project-id) PROJECT_ID="$2"; shift 2 ;;
        --region) REGION="$2"; shift 2 ;;
        --instance-name) INSTANCE_NAME="$2"; shift 2 ;;
        --database-name) DATABASE_NAME="$2"; shift 2 ;;
        --user-secret) USER_SECRET_ID="$2"; shift 2 ;;
        --password-secret) PASSWORD_SECRET_ID="$2"; shift 2 ;;
        --validate-passkey-fk) VALIDATE_PASSKEY_FK="true"; shift ;;
        --help|-h)
            sed -n '3,28p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

if [[ -z "$PROJECT_ID" || -z "$REGION" ]]; then
    echo "--project-id and --region are required." >&2
    exit 1
fi

for cmd in gcloud docker; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Missing required command: $cmd" >&2
        exit 1
    fi
done

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    echo "gcloud auth required (run 'gcloud auth login' or rely on workflow auth)." >&2
    exit 1
fi

INSTANCE_CONNECTION_NAME="${PROJECT_ID}:${REGION}:${INSTANCE_NAME}"
echo "Migration target: ${INSTANCE_CONNECTION_NAME} → ${DATABASE_NAME}"

WORKDIR="$(mktemp -d)"
trap 'cleanup' EXIT

PROXY_PID=""
cleanup() {
    if [[ -n "$PROXY_PID" ]] && kill -0 "$PROXY_PID" 2>/dev/null; then
        kill "$PROXY_PID" 2>/dev/null || true
        wait "$PROXY_PID" 2>/dev/null || true
    fi
    rm -rf "$WORKDIR"
}

# Fetch DB user/password from Secret Manager. Production validator forbids
# embedded credentials in DATABASE_URL when using socket connections, so we
# read user + password from their dedicated secrets.
DB_USER="$(gcloud secrets versions access latest --secret "$USER_SECRET_ID" --project "$PROJECT_ID")"
DB_PASSWORD=$(gcloud secrets versions access latest --secret "$PASSWORD_SECRET_ID" --project "$PROJECT_ID")

if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
    echo "Empty DB user or password from Secret Manager — verify secrets exist and the runtime SA has secretmanager.secretAccessor." >&2
    exit 1
fi

# Download cloud-sql-proxy v2 if not present. Pinned version so reproducible
# in CI; the proxy is small (~20 MB) and tolerates GitHub-hosted runner refreshes.
PROXY_BIN="${WORKDIR}/cloud-sql-proxy"
if ! command -v cloud-sql-proxy >/dev/null 2>&1; then
    PROXY_VERSION="v2.13.2"
    PROXY_OS="linux"
    PROXY_ARCH="amd64"
    case "$(uname -s)" in
        Darwin) PROXY_OS="darwin" ;;
        Linux) PROXY_OS="linux" ;;
        *) echo "Unsupported OS for proxy auto-download: $(uname -s)" >&2; exit 1 ;;
    esac
    case "$(uname -m)" in
        arm64|aarch64) PROXY_ARCH="arm64" ;;
        x86_64) PROXY_ARCH="amd64" ;;
    esac
    URL="https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/${PROXY_VERSION}/cloud-sql-proxy.${PROXY_OS}.${PROXY_ARCH}"
    curl -fsSL "$URL" -o "$PROXY_BIN"
    chmod +x "$PROXY_BIN"
else
    PROXY_BIN="cloud-sql-proxy"
fi

# Pick a free local port. cloud-sql-proxy v2 binds to 127.0.0.1 by default
# and rotates port automatically only if requested; fix one explicitly so the
# Flyway invocation below uses it deterministically.
PROXY_PORT=15432

echo "Starting Cloud SQL Auth Proxy on 127.0.0.1:${PROXY_PORT}..."
"$PROXY_BIN" \
    --address 127.0.0.1 \
    --port "$PROXY_PORT" \
    "$INSTANCE_CONNECTION_NAME" \
    >"${WORKDIR}/proxy.log" 2>&1 &
PROXY_PID=$!

# Wait for proxy to accept connections (typically <2s).
for _ in $(seq 1 30); do
    if (echo > /dev/tcp/127.0.0.1/${PROXY_PORT}) 2>/dev/null; then
        break
    fi
    sleep 1
done
if ! (echo > /dev/tcp/127.0.0.1/${PROXY_PORT}) 2>/dev/null; then
    echo "Cloud SQL Auth Proxy failed to start. Tail of proxy log:" >&2
    tail -n 40 "${WORKDIR}/proxy.log" >&2 || true
    exit 1
fi
echo "Proxy ready."

# Resolve the migrations directory. The script lives at scripts/, so the
# repo root is one level up; migrations are under server/src/main/resources/db/migration.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/server/src/main/resources/db/migration"

if [[ ! -d "$MIGRATIONS_DIR" ]]; then
    echo "Migrations directory not found: $MIGRATIONS_DIR" >&2
    exit 1
fi

# Run Flyway via Docker. --network host so the container can reach 127.0.0.1
# where the proxy is listening.
FLYWAY_VERSION="12.4.0"
FLYWAY_IMAGE="flyway/flyway:${FLYWAY_VERSION}"

echo "Running Flyway migrate (image: ${FLYWAY_IMAGE})..."
docker run --rm \
    --network host \
    -v "${MIGRATIONS_DIR}:/flyway/sql:ro" \
    -e "FLYWAY_URL=jdbc:postgresql://127.0.0.1:${PROXY_PORT}/${DATABASE_NAME}" \
    -e "FLYWAY_USER=${DB_USER}" \
    -e "FLYWAY_PASSWORD=${DB_PASSWORD}" \
    -e "FLYWAY_CONNECT_RETRIES=10" \
    "$FLYWAY_IMAGE" \
    -baselineOnMigrate=false \
    -outOfOrder=false \
    info migrate info

echo "Flyway migrate complete."

# V18 dropped the passkeys → accounts FK to repair an orphan-window bug.
# V19 restores it as NOT VALID for instant rollout — existing rows aren't
# scanned. To finalize the constraint, run VALIDATE CONSTRAINT once after
# V19 is applied. Idempotent: validating an already-valid constraint is a no-op.
if [[ "$VALIDATE_PASSKEY_FK" == "true" ]]; then
    echo "Validating passkeys.account_id_fkey..."
    docker run --rm \
        --network host \
        -e "PGPASSWORD=${DB_PASSWORD}" \
        postgres:16-alpine \
        psql \
        --host 127.0.0.1 --port "${PROXY_PORT}" \
        --username "${DB_USER}" --dbname "${DATABASE_NAME}" \
        --no-psqlrc --no-align --tuples-only \
        --command "ALTER TABLE passkeys VALIDATE CONSTRAINT passkeys_account_id_fkey;"
    echo "Passkey FK validated."
fi

echo "Migrations complete for ${INSTANCE_CONNECTION_NAME}/${DATABASE_NAME}."
