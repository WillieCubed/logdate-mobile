#!/usr/bin/env bash
# Rehearses publishing LogDate identities from start to finish without touching the public PLC
# directory. It runs everything on this machine:
#
#   - a throwaway PostgreSQL cluster,
#   - the reference PLC directory (bluesky-social/did-method-plc, pinned below), and
#   - a LogDate server built from this checkout,
#
# then walks the steps in docs/runbook/identity-publishing.md: sign test accounts up while
# publishing is off, check their status, switch publishing on, publish a few, publish the rest,
# confirm every identity resolves, publish with a wrong PDS address and recover from it, and
# switch publishing off again. Each step checks its result and the script stops at the first one
# that is wrong.
#
# Usage:
#   ./run identity:rehearse [--accounts N] [--skip-server-build]
#
# Needs Java 17+, Node.js 22+ with corepack, Python 3, curl, and PostgreSQL's server tools
# (initdb, pg_ctl, createdb). Logs, reports and data stay under tmp/identity-publishing-rehearsal/.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$LOGDATE_REPO_ROOT"

# Reference PLC directory: the software that runs plc.directory, pinned to a reviewed commit.
readonly PLC_REPOSITORY="bluesky-social/did-method-plc"
readonly PLC_COMMIT="9c8ea2fe23b89a5c1011246cbb4957dad9dbf7db"
readonly PLC_PNPM="pnpm@11.11.0"
readonly PLC_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/logdate/did-method-plc/$PLC_COMMIT"
readonly SERVER_BIN="server/build/install/server/bin/server"
readonly HANDLE_DOMAIN="rehearsal.test"
readonly WEBAUTHN_ORIGIN="https://localhost"

ACCOUNTS=3
BUILD_SERVER=true

usage() {
    sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed -e '$d' -e 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --accounts)
            [[ "${2:-}" =~ ^[1-9][0-9]?$ ]] || die "--accounts needs a number from 1 to 99."
            ACCOUNTS="$2"
            shift 2
            ;;
        --skip-server-build)
            BUILD_SERVER=false
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *) die "Unknown argument: $1 (see --help)" ;;
    esac
done

RUN_ID="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$LOGDATE_REPO_ROOT/tmp/identity-publishing-rehearsal/$RUN_ID"
SERVER_PID=""
PLC_PID=""
PG_DATA=""
PG_CTL=""

# Every address the rehearsal talks to must be on this machine. This is the check that keeps the
# public directory out of reach no matter what the environment says.
require_loopback_url() {
    [[ "$1" =~ ^http://(127\.0\.0\.1|localhost):[0-9]+$ ]] || die "Refusing to use $1: the rehearsal only talks to loopback addresses."
}

free_port() {
    python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])'
}

find_postgres_tools() {
    if command -v initdb >/dev/null 2>&1; then
        dirname "$(command -v initdb)"
        return
    fi
    local candidate
    for candidate in $(ls -d /opt/homebrew/opt/postgresql@*/bin /usr/local/opt/postgresql@*/bin /usr/lib/postgresql/*/bin 2>/dev/null | sort -t@ -k2 -V -r); do
        [[ -x "$candidate/initdb" ]] && { printf '%s\n' "$candidate"; return; }
    done
    die "PostgreSQL server tools (initdb, pg_ctl) are required. On macOS: brew install postgresql@18"
}

wait_for_http() {
    local url="$1" name="$2" pid="$3" log="$4" attempt
    for attempt in $(seq 1 120); do
        if curl -fsS -o /dev/null "$url" 2>/dev/null; then
            return 0
        fi
        kill -0 "$pid" 2>/dev/null || die "$name exited while starting; see $log"
        sleep 1
    done
    die "$name did not answer at $url within two minutes; see $log"
}

stop_process() {
    local pid="$1"
    [[ -n "$pid" ]] || return 0
    kill "$pid" 2>/dev/null || return 0
    local attempt
    for attempt in $(seq 1 30); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
}

cleanup() {
    stop_process "$SERVER_PID"
    stop_process "$PLC_PID"
    if [[ -n "$PG_CTL" && -n "$PG_DATA" && -f "$PG_DATA/postmaster.pid" ]]; then
        "$PG_CTL" -D "$PG_DATA" -m fast -w stop >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

start_postgres() {
    local tools
    tools="$(find_postgres_tools)"
    PG_CTL="$tools/pg_ctl"
    PG_DATA="$WORK_DIR/postgres"
    PG_PORT="$(free_port)"
    # PostgreSQL refuses to start on macOS without a valid locale in the environment.
    export LC_ALL=C LANG=C
    "$tools/initdb" -D "$PG_DATA" -U rehearsal --auth=trust -E UTF8 --locale=C >"$WORK_DIR/initdb.log" 2>&1 ||
        die "initdb failed; see $WORK_DIR/initdb.log"
    "$PG_CTL" -D "$PG_DATA" -l "$WORK_DIR/postgres.log" -w \
        -o "-p $PG_PORT -c listen_addresses=127.0.0.1 -c unix_socket_directories=" start >/dev/null ||
        die "PostgreSQL did not start; see $WORK_DIR/postgres.log"
    "$tools/createdb" -h 127.0.0.1 -p "$PG_PORT" -U rehearsal logdate
    "$tools/createdb" -h 127.0.0.1 -p "$PG_PORT" -U rehearsal plc
    log_info "PostgreSQL is listening on 127.0.0.1:$PG_PORT"
}

build_plc_directory() {
    [[ -f "$PLC_CACHE/packages/server/dist/bin.js" ]] && return 0
    log_info "Building the reference PLC directory at $PLC_COMMIT (once; cached in $PLC_CACHE)"
    local staging="$PLC_CACHE.partial-$RUN_ID"
    mkdir -p "$staging"
    curl -fsSL "https://codeload.github.com/$PLC_REPOSITORY/tar.gz/$PLC_COMMIT" |
        tar -xz -C "$staging" --strip-components 1
    (
        cd "$staging"
        export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
        corepack "$PLC_PNPM" install --frozen-lockfile
        corepack "$PLC_PNPM" --filter @did-plc/lib --filter @did-plc/server run build
    ) >"$WORK_DIR/plc-build.log" 2>&1 || die "Building the PLC directory failed; see $WORK_DIR/plc-build.log"
    mv "$staging" "$PLC_CACHE"
}

start_plc_directory() {
    PLC_PORT="$(free_port)"
    PLC_URL="http://127.0.0.1:$PLC_PORT"
    require_loopback_url "$PLC_URL"
    (
        cd "$PLC_CACHE"
        exec env -i PATH="$PATH" HOME="$HOME" \
            DATABASE_URL="postgres://rehearsal@127.0.0.1:$PG_PORT/plc" \
            PORT="$PLC_PORT" LOG_ENABLED=false \
            node packages/server/dist/bin.js
    ) >"$WORK_DIR/plc-directory.log" 2>&1 &
    PLC_PID=$!
    wait_for_http "$PLC_URL/_health" "The PLC directory" "$PLC_PID" "$WORK_DIR/plc-directory.log"
    log_info "Reference PLC directory is listening at $PLC_URL"
}

prepare_server() {
    SERVER_PORT="$(free_port)"
    SERVER_URL="http://127.0.0.1:$SERVER_PORT"
    KEK="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    SESSION_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    JWT="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    if [[ "$BUILD_SERVER" == "true" ]]; then
        log_info "Building the LogDate server"
        ./gradlew :server:installDist -q >"$WORK_DIR/server-build.log" 2>&1 ||
            die "Building the server failed; see $WORK_DIR/server-build.log"
    fi
    [[ -x "$SERVER_BIN" ]] || die "$SERVER_BIN is missing; run without --skip-server-build."
}

# The server's whole environment: nothing is inherited, so a developer's own ATPROTO_* or database
# settings can never leak in. Arguments: publishing flag, PDS address. Sets SERVER_ENV.
server_environment() {
    local publishing="$1" pds_url="$2"
    require_loopback_url "$PLC_URL"
    [[ "$pds_url" == *".$HANDLE_DOMAIN" ]] || die "Refusing PDS address $pds_url: rehearsal addresses end in .$HANDLE_DOMAIN"
    SERVER_ENV=(
        PATH="$PATH" HOME="$HOME" JAVA_HOME="${JAVA_HOME:-}"
        LOGDATE_ENV=development AUTO_MIGRATE=true HOST=127.0.0.1 PORT="$SERVER_PORT"
        DB_HOST=127.0.0.1 DB_PORT="$PG_PORT" DB_NAME=logdate DATABASE_USER=rehearsal DATABASE_PASSWORD=rehearsal
        JWT_SECRET="$JWT" ATPROTO_SIGNING_KEY_KEK="$KEK" ATPROTO_SESSION_SECRET="$SESSION_SECRET"
        ATPROTO_PDS_SERVICE_URL="$pds_url" ATPROTO_HANDLE_DOMAIN="$HANDLE_DOMAIN"
        WEBAUTHN_RP_ID=localhost WEBAUTHN_ORIGIN="$WEBAUTHN_ORIGIN" WEBAUTHN_STRICT_VERIFICATION=true
        ATPROTO_OAUTH_ISSUER="$WEBAUTHN_ORIGIN" ATPROTO_OAUTH_RESOURCE="$WEBAUTHN_ORIGIN"
        ATPROTO_HOSTED_DID_METHOD=plc ATPROTO_PLC_PUBLISH_ENABLED="$publishing" ATPROTO_PLC_DIRECTORY_URL="$PLC_URL"
    )
}

start_server() {
    local publishing="$1" pds_url="$2"
    stop_process "$SERVER_PID"
    server_environment "$publishing" "$pds_url"
    env -i "${SERVER_ENV[@]}" "$SERVER_BIN" >>"$WORK_DIR/server.log" 2>&1 &
    SERVER_PID=$!
    wait_for_http "$SERVER_URL/health" "The LogDate server" "$SERVER_PID" "$WORK_DIR/server.log"
    log_info "LogDate server is up at $SERVER_URL (publishing=$publishing, PDS address $pds_url)"
}

# Runs the identity publishing command and checks its exit code. Arguments: expected exit code,
# report name (or -), publishing flag, PDS address, then the command's arguments.
identity_command() {
    local expected="$1" report="$2" publishing="$3" pds_url="$4"
    shift 4
    local args=("$@") status=0
    [[ "$report" != "-" ]] && args+=(--report-file "$WORK_DIR/$report.json")
    server_environment "$publishing" "$pds_url"
    env -i "${SERVER_ENV[@]}" "$SERVER_BIN" --identity-publishing "${args[@]}" >>"$WORK_DIR/commands.log" 2>&1 || status=$?
    [[ "$status" == "$expected" ]] ||
        die "'--identity-publishing ${args[*]}' exited $status, expected $expected; see $WORK_DIR/commands.log"
    log_info "--identity-publishing $* -> exit $status as expected"
}

# Reads one number from a report's counts; 0 when absent.
report_count() {
    python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["counts"].get(sys.argv[2], 0))' "$WORK_DIR/$1.json" "$2"
}

expect_count() {
    local report="$1" key="$2" expected="$3" actual
    actual="$(report_count "$report" "$key")"
    [[ "$actual" == "$expected" ]] || die "$report.json counts $key=$actual, expected $expected"
}

# Checks every account in a JSON list against the directory and against LogDate's handle resolution.
verify_published() {
    local accounts_file="$1" pds_url="$2"
    python3 - "$accounts_file" "$PLC_URL" "$SERVER_URL" "$pds_url" <<'PY'
import json, sys, urllib.parse, urllib.request
accounts_file, plc_url, server_url, pds_url = sys.argv[1:]
for account in json.load(open(accounts_file)):
    did, handle = account["did"], account["handle"]
    with urllib.request.urlopen(f"{plc_url}/{did}", timeout=10) as response:
        document = json.load(response)
    assert document["alsoKnownAs"] == [f"at://{handle}"], f"{did} names {document['alsoKnownAs']}"
    endpoints = [s["serviceEndpoint"] for s in document["service"] if s["id"] == "#atproto_pds"]
    assert endpoints == [pds_url], f"{did} points at {endpoints}, expected {pds_url}"
    query = urllib.parse.urlencode({"handle": handle})
    with urllib.request.urlopen(f"{server_url}/xrpc/com.atproto.identity.resolveHandle?{query}", timeout=10) as response:
        resolved = json.load(response)["did"]
    assert resolved == did, f"{handle} resolves to {resolved}, expected {did}"
    print(f"  {handle} -> {did} resolves in the directory and on LogDate")
PY
}

# Checks one account, picked by handle, from a JSON list of accounts.
verify_published_one() {
    local accounts_file="$1" handle="$2" pds_url="$3" single="$WORK_DIR/one-account.json"
    python3 -c 'import json, sys; json.dump([a for a in json.load(open(sys.argv[1])) if a["handle"] == sys.argv[2]], open(sys.argv[3], "w"))' \
        "$accounts_file" "$handle" "$single"
    verify_published "$single" "$pds_url"
}

expect_not_in_directory() {
    local accounts_file="$1"
    python3 - "$accounts_file" "$PLC_URL" <<'PY'
import json, sys, urllib.error, urllib.request
accounts_file, plc_url = sys.argv[1:]
for account in json.load(open(accounts_file)):
    try:
        urllib.request.urlopen(f"{plc_url}/{account['did']}", timeout=10)
    except urllib.error.HTTPError as error:
        assert error.code == 404, f"{account['did']}: directory answered {error.code}"
        continue
    raise AssertionError(f"{account['did']} is already in the directory")
PY
}

sign_up() {
    local prefix="$1" count="$2" output="$3"
    python3 scripts/identity-publishing/rehearsal-accounts.py \
        --base "$SERVER_URL" --origin "$WEBAUTHN_ORIGIN" --prefix "$prefix" --count "$count" \
        --output "$WORK_DIR/$output" >>"$WORK_DIR/commands.log" 2>&1 ||
        die "Signing up test accounts failed; see $WORK_DIR/commands.log"
}

audit_log_lengths() {
    python3 - "$1" "$PLC_URL" <<'PY'
import json, sys, urllib.request
accounts_file, plc_url = sys.argv[1:]
lengths = []
for account in json.load(open(accounts_file)):
    with urllib.request.urlopen(f"{plc_url}/{account['did']}/log/audit", timeout=10) as response:
        lengths.append(len(json.load(response)))
print(" ".join(map(str, lengths)))
PY
}

main() {
    require_cmd java node corepack python3 curl tar
    mkdir -p "$WORK_DIR"
    log_phase "Setting up PostgreSQL, the reference PLC directory and a LogDate server on this machine"
    start_postgres
    build_plc_directory
    start_plc_directory
    prepare_server
    # The PDS address is only written into each identity, never contacted, so it can be any
    # https address. The first one plays the part of a wrong value that went out by mistake.
    local pds_url="https://old-pds.$HANDLE_DOMAIN"
    local corrected_pds_url="https://pds.$HANDLE_DOMAIN"

    log_phase "1. Publishing off: accounts sign up and get identities nobody outside LogDate can resolve"
    start_server false "$pds_url"
    sign_up existing "$ACCOUNTS" existing-accounts.json
    expect_not_in_directory "$WORK_DIR/existing-accounts.json"
    identity_command 3 01-status-before false "$pds_url" status
    expect_count 01-status-before unpublished "$ACCOUNTS"
    identity_command 2 - false "$pds_url" publish --confirm-directory "$PLC_URL"
    log_info "Publishing is refused while ATPROTO_PLC_PUBLISH_ENABLED is off"

    log_phase "2. Dry run: list what publishing would do, without sending anything"
    identity_command 3 02-dry-run false "$pds_url" publish --dry-run
    expect_count 02-dry-run would_publish_genesis "$ACCOUNTS"
    expect_not_in_directory "$WORK_DIR/existing-accounts.json"

    log_phase "3. Switch publishing on: new sign-ups are published as they happen"
    start_server true "$pds_url"
    sign_up new 1 new-accounts.json
    verify_published "$WORK_DIR/new-accounts.json" "$pds_url"
    identity_command 2 - true "$pds_url" publish --confirm-directory https://plc.directory
    log_info "A run naming any directory other than the configured one is refused"

    log_phase "4. Publish existing identities: one chosen account first, then the rest in batches"
    identity_command 0 04-one-account true "$pds_url" publish --confirm-directory "$PLC_URL" --handle "existing1.$HANDLE_DOMAIN"
    expect_count 04-one-account publish_genesis 1
    verify_published_one "$WORK_DIR/existing-accounts.json" "existing1.$HANDLE_DOMAIN" "$pds_url"
    identity_command 0 04-remaining true "$pds_url" publish --confirm-directory "$PLC_URL" --batch-size 2 --operations-per-minute 120
    expect_count 04-remaining publish_genesis "$((ACCOUNTS - 1))"
    expect_count 04-remaining now_published "$((ACCOUNTS + 1))"

    log_phase "5. Verify: every identity resolves, and publishing again changes nothing"
    verify_published "$WORK_DIR/existing-accounts.json" "$pds_url"
    identity_command 0 05-status-after true "$pds_url" status
    expect_count 05-status-after published "$((ACCOUNTS + 1))"
    local before after
    before="$(audit_log_lengths "$WORK_DIR/existing-accounts.json")"
    identity_command 0 05-publish-again true "$pds_url" publish --confirm-directory "$PLC_URL"
    after="$(audit_log_lengths "$WORK_DIR/existing-accounts.json")"
    [[ "$before" == "$after" ]] || die "Publishing again added directory entries ($before -> $after)"
    log_info "A second run sent nothing; the directory is unchanged"

    log_phase "6. Bad publish: the identities went out with a PDS address that is wrong; correct it and recover"
    start_server true "$corrected_pds_url"
    identity_command 3 06-status-mismatch true "$corrected_pds_url" status
    expect_count 06-status-mismatch mismatch "$((ACCOUNTS + 1))"
    identity_command 3 06-publish-without-reconcile true "$corrected_pds_url" publish --confirm-directory "$PLC_URL"
    identity_command 0 06-reconcile true "$corrected_pds_url" publish --confirm-directory "$PLC_URL" --reconcile
    expect_count 06-reconcile reconcile "$((ACCOUNTS + 1))"
    verify_published "$WORK_DIR/existing-accounts.json" "$corrected_pds_url"
    verify_published "$WORK_DIR/new-accounts.json" "$corrected_pds_url"

    log_phase "7. Stop: switch publishing off again"
    start_server false "$corrected_pds_url"
    identity_command 2 - false "$corrected_pds_url" publish --confirm-directory "$PLC_URL"
    identity_command 0 07-status-stopped false "$corrected_pds_url" status
    log_info "With publishing off, nothing can be sent and status still reads the directory"

    log_phase "Rehearsal passed"
    log_info "Reports and logs: $WORK_DIR"
}

# Sourcing the script (as its test does) defines the functions without running the rehearsal.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
