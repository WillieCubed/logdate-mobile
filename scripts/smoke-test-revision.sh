#!/usr/bin/env bash
set -euo pipefail

# Smoke-test a deployed server revision. Hits /health, issues a bare
# passkey-signup-begin call, and can optionally complete a full passkey
# signup/signin loop through the verifier. `/health` only proves the JVM is up.
# Designed to run as a deploy-time gate; non-zero exit blocks traffic shift
# to the new revision.
#
# Inputs:
#   $1  Service URL (e.g. https://cloud-staging.logdate.app or a Cloud Run
#       revision URL like https://candidate-<sha>---logdate-server-...run.app)
#   $2  Optional WebAuthn origin for clientDataJSON (e.g.
#       https://cloud.logdate.app when probing a tagged candidate revision)
#
# Exit:
#   0 — health, signup-begin, entitlement-shape, and optional passkey probes succeeded
#   non-zero — at least one probe failed; deploy workflow should NOT promote

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <service-url> [webauthn-origin]" >&2
    exit 1
fi

URL="${1%/}"
WEBAUTHN_ORIGIN="${2:-}"
PROBE_USERNAME="smoketest_$(date +%s)_$$"
PASSKEY_VERIFY_PYTHON="${PASSKEY_VERIFY_PYTHON:-.venv/bin/python}"

health_status_is_healthy() {
    local body="$1"
    if command -v jq >/dev/null 2>&1; then
        printf '%s' "$body" | jq -e '.status == "healthy"' >/dev/null
        return
    fi
    [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"healthy\" ]]
}

probe_health() {
    local response
    response="$(curl --silent --show-error --max-time 10 \
        --write-out 'HTTP_STATUS:%{http_code}' \
        "$URL/health")"
    local status="${response##*HTTP_STATUS:}"
    local body="${response%HTTP_STATUS:*}"
    if [[ "$status" != "200" ]]; then
        echo "[FAIL] /health returned $status (body: $body)" >&2
        return 1
    fi
    if ! health_status_is_healthy "$body"; then
        echo "[FAIL] /health body missing status=healthy ($body)" >&2
        return 1
    fi
    echo "[OK] /health"
}

probe_signup_begin() {
    # We don't complete the signup — we just verify the route is wired and
    # returns a session token. The session expires in 15 minutes server-side
    # with no follow-up; no garbage is left for the user.
    local response status body
    response="$(curl --silent --show-error --max-time 15 \
        --write-out 'HTTP_STATUS:%{http_code}' \
        --header 'Content-Type: application/json' \
        --data "{\"username\":\"$PROBE_USERNAME\",\"displayName\":\"Smoke Test\"}" \
        "$URL/api/v1/auth/signup/passkey/begin")"
    status="${response##*HTTP_STATUS:}"
    body="${response%HTTP_STATUS:*}"
    if [[ "$status" != "200" ]]; then
        echo "[FAIL] signup/passkey/begin returned $status (body: $body)" >&2
        return 1
    fi
    if [[ "$body" != *'"sessionToken"'* ]]; then
        echo "[FAIL] signup/passkey/begin response missing sessionToken ($body)" >&2
        return 1
    fi
    echo "[OK] signup/passkey/begin"
}

probe_entitlement_unauth() {
    # No token. We expect 401, NOT 500. A 500 here would mean the route
    # validator is broken — the auth filter should reject without ever
    # touching the entitlement service.
    local status
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
        --max-time 10 "$URL/api/v1/auth/me/entitlement")"
    if [[ "$status" != "401" ]]; then
        echo "[FAIL] me/entitlement without token returned $status, expected 401" >&2
        return 1
    fi
    echo "[OK] me/entitlement (returns 401 without token, as expected)"
}

probe_passkey_end_to_end() {
    if [[ -z "$WEBAUTHN_ORIGIN" ]]; then
        echo "[SKIP] full passkey verification (no WebAuthn origin supplied)"
        return 0
    fi

    if [[ ! -x "$PASSKEY_VERIFY_PYTHON" ]]; then
        echo "[FAIL] passkey verifier Python not executable: $PASSKEY_VERIFY_PYTHON" >&2
        return 1
    fi

    local credential_dir credential_file
    credential_dir="$(mktemp -d "${TMPDIR:-/tmp}/logdate-passkey-smoke.XXXXXX")"
    credential_file="$credential_dir/passkey.json"

    if ! "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py \
        --base "$URL" \
        --origin "$WEBAUTHN_ORIGIN" \
        --username "$PROBE_USERNAME" \
        --display-name "Smoke Test" \
        --credential-file "$credential_file"; then
        rm -rf "$credential_dir"
        return 1
    fi

    if ! "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py \
        --base "$URL" \
        --origin "$WEBAUTHN_ORIGIN" \
        --username "$PROBE_USERNAME" \
        --credential-file "$credential_file" \
        --signin-only; then
        rm -rf "$credential_dir"
        return 1
    fi

    rm -rf "$credential_dir"

    echo "[OK] full passkey signup/signin"
}

failed=0
probe_health || failed=1
probe_signup_begin || failed=1
probe_entitlement_unauth || failed=1
probe_passkey_end_to_end || failed=1

if [[ $failed -ne 0 ]]; then
    echo "Smoke test FAILED for $URL" >&2
    exit 1
fi
echo "Smoke test passed for $URL"
