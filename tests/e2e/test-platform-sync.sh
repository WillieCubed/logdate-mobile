#!/bin/bash

#
# Platform Sync E2E Test
#
# Runs the one journey that spans both halves of the product: an emulator creating a real account
# against a real server on this machine, through the onboarding UI, with a WebAuthn ceremony the
# relying party actually verifies.
#
# Usage:
#   ./tests/e2e/test-platform-sync.sh [options]
#
# Examples:
#   ./tests/e2e/test-platform-sync.sh                 # Run the journey
#   ./tests/e2e/test-platform-sync.sh --preflight     # Only check the device rig
#   ./tests/e2e/test-platform-sync.sh --verbose       # Show Gradle output
#

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

VERBOSE=false
PREFLIGHT_ONLY=false
DEVICE_TASK="largeScreenTabletApi35DebugAndroidTest"
TEST_CLASS="app.logdate.client.e2e.PlatformSyncJourneyE2ETest"
SERVER_LOG="$(mktemp -t logdate-platform-sync-server)"
SERVER_PID=""

show_help() {
    cat <<'EOF'
Platform Sync E2E Test

Usage: ./tests/e2e/test-platform-sync.sh [options]

Options:
  --preflight   Only run PlatformSyncPreflightTest (device rig checks)
  --device NAME Managed device task to run (default: largeScreenTabletApi35DebugAndroidTest)
  --verbose     Show full Gradle output
  --help        Show this help message

Requires Docker (for Postgres) and the Android SDK. Uses Gradle Managed Devices only; no
physical device is ever targeted.
EOF
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --preflight) PREFLIGHT_ONLY=true; TEST_CLASS="app.logdate.client.e2e.PlatformSyncPreflightTest"; shift ;;
        --device) DEVICE_TASK="${2:?--device needs a task name}"; shift 2 ;;
        --verbose) VERBOSE=true; shift ;;
        --help) show_help; exit 0 ;;
        *) echo "Unknown option: $1" >&2; show_help; exit 1 ;;
    esac
done

info()  { echo -e "${BLUE}==>${NC} $1"; }
ok()    { echo -e "${GREEN}✓${NC} $1"; }
warn()  { echo -e "${YELLOW}!${NC} $1"; }
fail()  { echo -e "${RED}✗${NC} $1" >&2; }

cleanup() {
    local status=$?
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        info "Stopping server"
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    pkill -f "app.logdate.server.ApplicationKt" 2>/dev/null || true
    if [[ $status -ne 0 ]]; then
        fail "Failed. Server log: $SERVER_LOG"
    fi
    exit $status
}
trap cleanup EXIT INT TERM

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║${NC} Platform Sync E2E"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ---------------------------------------------------------------------------
# 1. Build the APKs. The signing certificate of the debug build decides the
#    WebAuthn origin, so it has to exist before that origin can be derived.
# ---------------------------------------------------------------------------
# The address the emulator reaches this machine on.
EMULATOR_SERVER="http://10.0.2.2:8765"

# Baked into the build rather than overridden at runtime. A Koin override only sets the initial
# value: the account flow's server picker then writes the selected preset back, which sent signup
# to production while only the health and descriptor probes reached this machine. Building with
# logdate.backendUrl makes this server the app's own default, so there is no production preset to
# fall back to.
info "Building app and test authenticator against $EMULATOR_SERVER"
./gradlew :app:android-main:assembleDebug :tools:passkey-test-provider:assembleDebug \
    "-Plogdate.backendUrl=$EMULATOR_SERVER" \
    --console=plain $([ "$VERBOSE" = true ] || echo "-q")
ok "APKs built"

# ---------------------------------------------------------------------------
# 2. Derive the apk-key-hash origin from the APK that was just built, rather
#    than pinning it. A hand-copied fingerprint is exactly what
#    scripts/android-signer-evidence.sh exists to stop going stale.
# ---------------------------------------------------------------------------
APKSIGNER="$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[[ -x "$APKSIGNER" ]] || { fail "apksigner not found; install Android build-tools"; exit 1; }

CERT_HEX="$("$APKSIGNER" verify --print-certs \
    app/android-main/build/outputs/apk/debug/android-main-debug.apk \
    | grep -im1 "SHA-256 digest" | awk '{print $NF}')"
[[ -n "$CERT_HEX" ]] || { fail "Could not read the debug signing certificate"; exit 1; }

ANDROID_ORIGIN="android:apk-key-hash:$(python3 -c "
import base64
print(base64.urlsafe_b64encode(bytes.fromhex('$CERT_HEX')).decode().rstrip('='))
")"
ok "Origin for this build: $ANDROID_ORIGIN"

# ---------------------------------------------------------------------------
# 3. Postgres. The server silently drops to in-memory repositories without
#    credentials, which would let a sync test pass while storing nothing.
# ---------------------------------------------------------------------------
info "Starting Postgres"
docker compose up logdate-postgres -d >/dev/null
for _ in $(seq 1 60); do
    [[ "$(docker inspect --format='{{.State.Health.Status}}' logdate-dev-postgres 2>/dev/null)" == "healthy" ]] && break
    sleep 1
done
ok "Postgres healthy"

# ---------------------------------------------------------------------------
# 4. The server. Strict verification is off by default outside production, and
#    that path does no cryptography at all -- it takes clientDataJSON and
#    attestationObject as opaque strings. Left on the default this whole test
#    would prove sign-in while verifying nothing.
# ---------------------------------------------------------------------------
# A server left over from an earlier run would keep the port and answer with whatever origins it
# was started with, so the journey would fail against a process this script never configured.
if lsof -ti tcp:8765 >/dev/null 2>&1; then
    warn "Port 8765 is already in use; stopping the process holding it"
    lsof -ti tcp:8765 | xargs kill 2>/dev/null || true
    for _ in $(seq 1 20); do lsof -ti tcp:8765 >/dev/null 2>&1 || break; sleep 1; done
fi

# did:web rather than the default did:plc: minting a PLC operation goes through the shared
# atproto-plc module, where an https service endpoint is a protocol requirement rather than a
# server policy, and this server is deliberately plain http on loopback.
# ATPROTO_OAUTH_ISSUER is pinned because it defaults to the PDS endpoint and must be https;
# OAuth plays no part in this journey, so it keeps the canonical value while the descriptor
# above points at this machine.
info "Starting server on 8765"
DATABASE_URL="jdbc:postgresql://localhost:15432/logdate" \
DATABASE_USER=logdate \
DATABASE_PASSWORD=logdate \
HEALTH_INTERNAL_TOKEN=platform-sync-e2e \
ATPROTO_PDS_SERVICE_URL="$EMULATOR_SERVER" \
ATPROTO_OAUTH_ISSUER=https://logdate.app \
ATPROTO_HOSTED_DID_METHOD=web \
WEBAUTHN_STRICT_VERIFICATION=true \
WEBAUTHN_RP_ID=logdate.app \
WEBAUTHN_ORIGIN=https://logdate.app \
WEBAUTHN_ALLOWED_ORIGINS="$ANDROID_ORIGIN" \
    ./gradlew :server:run --console=plain >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

for _ in $(seq 1 120); do
    curl -fsS -o /dev/null --max-time 2 http://localhost:8765/health 2>/dev/null && break
    if grep -qE "FAILURE:|Exception in thread" "$SERVER_LOG" 2>/dev/null; then
        fail "Server failed to start"; tail -30 "$SERVER_LOG"; exit 1
    fi
    sleep 1
done
curl -fsS -o /dev/null --max-time 2 http://localhost:8765/health || { fail "Server never became ready"; exit 1; }

DB_CONNECTED="$(curl -fsS -H "X-LogDate-Health-Token: platform-sync-e2e" \
    http://localhost:8765/health | python3 -c "import json,sys; print(json.load(sys.stdin).get('db_connected'))")"
if [[ "$DB_CONNECTED" != "True" ]]; then
    fail "Server is up but not database-backed (db_connected=$DB_CONNECTED). It would store nothing."
    exit 1
fi
ok "Server ready and database-backed"

# ---------------------------------------------------------------------------
# 5. The journey, on a managed device. Never a physical one.
# ---------------------------------------------------------------------------
info "Running $TEST_CLASS on $DEVICE_TASK"
GRADLE_ARGS=(
    ":app:android-main:$DEVICE_TASK"
    "-Plogdate.backendUrl=$EMULATOR_SERVER"
    "-Plogdate.androidTestClass=$TEST_CLASS"
    "-Plogdate.androidTestCoverage=false"
    "--console=plain"
)
[ "$VERBOSE" = true ] && GRADLE_ARGS+=("--info")

if ./gradlew "${GRADLE_ARGS[@]}"; then
    echo ""
    ok "Platform sync e2e passed"
else
    echo ""
    fail "Platform sync e2e failed"
    warn "Server log: $SERVER_LOG"
    exit 1
fi
