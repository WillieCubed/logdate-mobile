#!/usr/bin/env bash
# Checks that an account handle resolves to its DID the way other AT Protocol apps resolve it.
#
# An app that meets a handle such as alice.logdate.app looks up its DID in two places:
#   1. DNS: a TXT record at _atproto.alice.logdate.app containing "did=<did>".
#   2. HTTPS: the body of https://alice.logdate.app/.well-known/atproto-did.
# LogDate answers the HTTPS method; the logdate.app website serves that path for every handle
# host and asks the LogDate server which account owns the handle. This script asks the server
# directly, then asks the handle host the way an outside app would, and requires the two to agree.
# See docs/reference/handle-resolution.md.

set -uo pipefail

usage() {
    cat >&2 <<'EOF'
Usage:
  scripts/check-handle-resolution.sh --handle <handle> --service-url <server origin> [options]

Options:
  --handle <handle>             Handle to resolve, such as name.logdate.app.
                                Defaults to $LOGDATE_RESOLUTION_HANDLE.
  --service-url <origin>        LogDate server that owns the handle, such as
                                https://cloud-staging.logdate.app. Defaults to
                                $LOGDATE_RESOLUTION_SERVICE_URL.
  --expect-did <did>            Fail unless the handle resolves to exactly this DID.
  --verify-did-document         Also require the DID document to list at://<handle>, which is
                                how apps confirm the handle belongs to the DID.
  --plc-directory-url <origin>  PLC directory for --verify-did-document (https://plc.directory).
  --well-known-origin <origin>  Send the handle-host requests to this origin with the handle in
                                the Host header instead of https://<handle>. For tests only.
EOF
}

HANDLE="${LOGDATE_RESOLUTION_HANDLE:-}"
SERVICE_URL="${LOGDATE_RESOLUTION_SERVICE_URL:-}"
EXPECT_DID=""
VERIFY_DID_DOCUMENT=false
PLC_DIRECTORY_URL="https://plc.directory"
WELL_KNOWN_ORIGIN=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --handle) HANDLE="${2:-}"; shift 2 ;;
        --service-url) SERVICE_URL="${2:-}"; shift 2 ;;
        --expect-did) EXPECT_DID="${2:-}"; shift 2 ;;
        --verify-did-document) VERIFY_DID_DOCUMENT=true; shift ;;
        --plc-directory-url) PLC_DIRECTORY_URL="${2:-}"; shift 2 ;;
        --well-known-origin) WELL_KNOWN_ORIGIN="${2:-}"; shift 2 ;;
        -h | --help) usage; exit 0 ;;
        *) printf 'Unknown argument: %s\n' "$1" >&2; usage; exit 2 ;;
    esac
done

[[ -n "$HANDLE" ]] || { printf '%s\n' '--handle is required.' >&2; usage; exit 2; }
[[ -n "$SERVICE_URL" ]] || { printf '%s\n' '--service-url is required.' >&2; usage; exit 2; }
for command_name in curl jq; do
    command -v "$command_name" >/dev/null 2>&1 || {
        printf '%s is required but not installed.\n' "$command_name" >&2
        exit 2
    }
done

HANDLE="$(printf '%s' "$HANDLE" | tr '[:upper:]' '[:lower:]')"
HANDLE="${HANDLE%.}"
[[ "$HANDLE" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]([a-z0-9-]{0,61}[a-z0-9])?$ ]] || {
    printf 'Not a valid handle: %s\n' "$HANDLE" >&2
    exit 2
}
SERVICE_URL="${SERVICE_URL%/}"
PLC_DIRECTORY_URL="${PLC_DIRECTORY_URL%/}"
PARENT_DOMAIN="${HANDLE#*.}"
DID_PATTERN='^did:[a-z]+:[A-Za-z0-9._:%-]+$'

WORK_DIR="$(mktemp -d)"
trap 'rm -r "$WORK_DIR"' EXIT
BODY_FILE="$WORK_DIR/body"
ERROR_FILE="$WORK_DIR/error"

FAILURES=0
pass() { printf '[PASS] %s\n' "$1"; }
failure() {
    printf '[FAIL] %s\n' "$1"
    FAILURES=$((FAILURES + 1))
}

# Fetches a URL without following redirects. The AT Protocol lets a handle host redirect, but
# LogDate's hosts answer directly, so a redirect here means a misrouted request (for example a
# sign-in wall) and this stricter check reports it.
# Sets HTTP_STATUS ("000" when no response arrived) and leaves the body in $BODY_FILE.
http_get() {
    local url="$1" host_header="${2:-}"
    local args=(--silent --show-error --max-time 15 --output "$BODY_FILE" --write-out '%{http_code}')
    [[ -n "$host_header" ]] && args+=(--header "Host: $host_header")
    : >"$BODY_FILE"
    HTTP_STATUS="$(curl "${args[@]}" "$url" 2>"$ERROR_FILE")" || true
    [[ -n "$HTTP_STATUS" ]] || HTTP_STATUS="000"
}

response_problem() {
    if [[ "$HTTP_STATUS" == "000" ]]; then
        printf 'no response: %s' "$(tr -d '\n' <"$ERROR_FILE")"
        return
    fi
    local error_code
    error_code="$(jq -r '.error // empty' "$BODY_FILE" 2>/dev/null)"
    printf 'HTTP %s%s' "$HTTP_STATUS" "${error_code:+: $error_code}"
}

# Requests https://<host>/.well-known/atproto-did, or the test origin with that Host header.
fetch_well_known() {
    local host="$1"
    if [[ -n "$WELL_KNOWN_ORIGIN" ]]; then
        http_get "${WELL_KNOWN_ORIGIN%/}/.well-known/atproto-did" "$host"
    else
        http_get "https://$host/.well-known/atproto-did"
    fi
}

# 1. The server's own answer, through the standard XRPC method every PDS exposes.
SERVER_DID=""
http_get "$SERVICE_URL/xrpc/com.atproto.identity.resolveHandle?handle=$HANDLE"
if [[ "$HTTP_STATUS" == "200" ]] && SERVER_DID="$(jq -er '.did | strings' "$BODY_FILE" 2>/dev/null)"; then
    pass "the server resolves $HANDLE to $SERVER_DID"
else
    SERVER_DID=""
    failure "the server could not resolve $HANDLE ($(response_problem))"
fi

EXPECTED_DID="${EXPECT_DID:-$SERVER_DID}"
if [[ -n "$EXPECT_DID" && -n "$SERVER_DID" && "$SERVER_DID" != "$EXPECT_DID" ]]; then
    failure "the server resolves $HANDLE to $SERVER_DID, expected $EXPECT_DID"
fi

# 2. The answer an outside app gets from the handle host.
WELL_KNOWN_URL="https://$HANDLE/.well-known/atproto-did"
fetch_well_known "$HANDLE"
WELL_KNOWN_DID="$(tr -d '[:space:]' <"$BODY_FILE")"
if [[ "$HTTP_STATUS" == 3?? ]]; then
    failure "$WELL_KNOWN_URL redirected (HTTP $HTTP_STATUS); LogDate handle hosts answer directly, so this usually means a sign-in wall or a misrouted host"
elif [[ "$HTTP_STATUS" != "200" ]]; then
    failure "$WELL_KNOWN_URL returned $(response_problem)"
elif [[ ! "$WELL_KNOWN_DID" =~ $DID_PATTERN ]]; then
    failure "$WELL_KNOWN_URL returned a body that is not a DID"
elif [[ -n "$EXPECTED_DID" && "$WELL_KNOWN_DID" != "$EXPECTED_DID" ]]; then
    if [[ -n "$EXPECT_DID" ]]; then
        failure "$WELL_KNOWN_URL returned $WELL_KNOWN_DID, but $HANDLE should resolve to $EXPECT_DID"
    else
        failure "$WELL_KNOWN_URL returned $WELL_KNOWN_DID, but the server resolves $HANDLE to $SERVER_DID"
    fi
elif [[ -z "$EXPECTED_DID" ]]; then
    failure "$WELL_KNOWN_URL returned $WELL_KNOWN_DID, but there is no server answer to compare it with"
else
    pass "$HANDLE resolves to $WELL_KNOWN_DID through $WELL_KNOWN_URL"
fi

# 3. A label nobody owns must not resolve, or any label could claim an identity.
ABSENT_HOST="resolution-check-$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n').$PARENT_DOMAIN"
fetch_well_known "$ABSENT_HOST"
if [[ "$HTTP_STATUS" == "404" ]]; then
    pass "an unknown handle under $PARENT_DOMAIN returns 404"
elif [[ "$HTTP_STATUS" == "200" ]]; then
    failure "https://$ABSENT_HOST/.well-known/atproto-did returned 200 instead of 404"
else
    failure "https://$ABSENT_HOST/.well-known/atproto-did returned $(response_problem) instead of 404"
fi

# 4. LogDate publishes no DNS records per account, but a stray one would override HTTPS.
if command -v dig >/dev/null 2>&1; then
    TXT_DIDS=()
    while IFS= read -r record; do
        record="${record//\"/}"
        [[ "$record" == did=* ]] && TXT_DIDS+=("${record#did=}")
    done < <(dig +short TXT "_atproto.$HANDLE" 2>/dev/null)
    if [[ ${#TXT_DIDS[@]} -eq 0 ]]; then
        pass "_atproto.$HANDLE has no TXT record, so apps use the HTTPS answer"
    elif [[ ${#TXT_DIDS[@]} -gt 1 ]]; then
        failure "_atproto.$HANDLE has ${#TXT_DIDS[@]} did= TXT records; apps reject a handle with more than one"
    elif [[ -n "$EXPECTED_DID" && "${TXT_DIDS[0]}" != "$EXPECTED_DID" ]]; then
        failure "_atproto.$HANDLE TXT says did=${TXT_DIDS[0]}, which conflicts with $EXPECTED_DID"
    else
        pass "_atproto.$HANDLE TXT matches ${TXT_DIDS[0]}"
    fi
else
    printf '[SKIP] dig is not installed, so the DNS TXT method was not checked\n'
fi

# 5. Apps trust a handle only when the DID document names it back.
if [[ "$VERIFY_DID_DOCUMENT" == true ]]; then
    if [[ -z "$EXPECTED_DID" ]]; then
        failure "the DID document could not be checked because $HANDLE did not resolve"
    elif [[ "$EXPECTED_DID" != did:plc:* && "$EXPECTED_DID" != did:web:* ]]; then
        failure "the DID document for $EXPECTED_DID cannot be fetched; only did:plc and did:web are supported"
    else
        if [[ "$EXPECTED_DID" == did:plc:* ]]; then
            http_get "$PLC_DIRECTORY_URL/$EXPECTED_DID"
        else
            http_get "https://${EXPECTED_DID#did:web:}/.well-known/did.json"
        fi
        if [[ "$HTTP_STATUS" != "200" ]]; then
            failure "the DID document for $EXPECTED_DID is unavailable ($(response_problem))"
        elif jq -e --arg aka "at://$HANDLE" '(.alsoKnownAs // []) | index($aka) != null' "$BODY_FILE" >/dev/null 2>&1; then
            pass "the DID document for $EXPECTED_DID lists at://$HANDLE"
        else
            failure "the DID document for $EXPECTED_DID does not list at://$HANDLE"
        fi
    fi
fi

if [[ "$FAILURES" -gt 0 ]]; then
    printf '%d handle resolution check(s) failed for %s\n' "$FAILURES" "$HANDLE"
    exit 1
fi
printf 'Handle resolution works for %s\n' "$HANDLE"
