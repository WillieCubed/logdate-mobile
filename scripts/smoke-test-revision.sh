#!/usr/bin/env bash
set -euo pipefail

PASSKEY_VERIFY_PYTHON="${PASSKEY_VERIFY_PYTHON:-.venv/bin/python}"
SEEDED_FREE_STORAGE_BYTES=1073741824
SEEDED_FREE_BACKUP_COUNT=3
REQUIRED_CAPABILITIES_JSON='["AUTH_PASSKEY","SYNC_CONTENT","SYNC_MEDIA","ATPROTO_IDENTITY","ATPROTO_OAUTH","BILLING_SUBSCRIPTIONS","MANAGED_QUOTA","CLOUD_TRANSCRIPTION"]'

usage() {
    cat >&2 <<'EOF'
Usage:
  scripts/smoke-test-revision.sh \
    --service-url <url> \
    --contract-file <sorted-json> \
    --expected-release <release> \
    --invoker-token-file <path> \
    --phase <prepare|verify-and-cleanup|health-only> \
    --state-file <path>

Legacy compatibility (removed in Task 6):
  scripts/smoke-test-revision.sh <service-url> [webauthn-origin]
EOF
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    return 1
}

private_file_mode() {
    local path="$1"
    if stat -f '%Lp' "$path" >/dev/null 2>&1; then
        stat -f '%Lp' "$path"
    else
        stat -c '%a' "$path"
    fi
}

require_private_file() {
    local path="$1"
    local label="$2"
    local allow_empty="${3:-false}"
    if [[ -L "$path" || ! -f "$path" ]]; then
        fail "$label must be a regular file"
        return 1
    fi
    if [[ "$(private_file_mode "$path")" != "600" ]]; then
        fail "$label must have mode 600"
        return 1
    fi
    if [[ "$allow_empty" != "true" && ! -s "$path" ]]; then
        fail "$label is empty"
        return 1
    fi
}

sha256_file() {
    local path="$1"
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$path" | awk '{print $1}'
    else
        sha256sum "$path" | awk '{print $1}'
    fi
}

is_https_origin() {
    [[ -x "$PASSKEY_VERIFY_PYTHON" ]] || return 1
    "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py --validate-origin "$1" \
        >/dev/null 2>&1
}

is_colon_hex_sha256() {
    [[ "$1" =~ ^([0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$ ]]
}

is_apk_key_hash_origin() {
    [[ "$1" =~ ^android:apk-key-hash:[A-Za-z0-9_-]{43}$ ]]
}

# ---------------------------------------------------------------------------
# Temporary compatibility path for the workflow that predates immutable
# contracts. Task 6 switches every official caller atomically and removes this.
# ---------------------------------------------------------------------------

legacy_health_status_is_healthy() {
    local body="$1"
    if command -v jq >/dev/null 2>&1; then
        printf '%s' "$body" | jq -e '.status == "healthy"' >/dev/null
        return
    fi
    [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"healthy\" ]]
}

legacy_probe_health() {
    local url="$1"
    local response status body
    response="$(curl --silent --show-error --max-time 10 \
        --write-out 'HTTP_STATUS:%{http_code}' \
        "$url/health")"
    status="${response##*HTTP_STATUS:}"
    body="${response%HTTP_STATUS:*}"
    if [[ "$status" != "200" ]]; then
        echo "[FAIL] /health returned $status (body: $body)" >&2
        return 1
    fi
    if ! legacy_health_status_is_healthy "$body"; then
        echo "[FAIL] /health body missing status=healthy ($body)" >&2
        return 1
    fi
    echo "[OK] /health"
}

legacy_probe_signup_begin() {
    local url="$1"
    local username="$2"
    local response status body
    response="$(curl --silent --show-error --max-time 15 \
        --write-out 'HTTP_STATUS:%{http_code}' \
        --header 'Content-Type: application/json' \
        --data "{\"username\":\"$username\",\"displayName\":\"Smoke Test\"}" \
        "$url/api/v1/auth/signup/passkey/begin")"
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
    if command -v jq >/dev/null 2>&1; then
        LEGACY_EXPECTED_RP_ID="$(printf '%s' "$body" | jq -r '.data.registrationOptions.rpId // empty')"
    elif [[ "$body" =~ \"rpId\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]]; then
        LEGACY_EXPECTED_RP_ID="${BASH_REMATCH[1]}"
    fi
    if [[ -z "$LEGACY_EXPECTED_RP_ID" ]]; then
        echo "[FAIL] signup/passkey/begin response missing RP ID" >&2
        return 1
    fi
    echo "[OK] signup/passkey/begin"
}

legacy_probe_entitlement_unauth() {
    local url="$1"
    local status
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
        --max-time 10 "$url/api/v1/auth/me/entitlement")"
    if [[ "$status" != "401" ]]; then
        echo "[FAIL] me/entitlement without token returned $status, expected 401" >&2
        return 1
    fi
    echo "[OK] me/entitlement (returns 401 without token, as expected)"
}

legacy_probe_passkey_end_to_end() {
    local url="$1"
    local webauthn_origin="$2"
    local username="$3"
    local expected_rp_id="$4"
    if [[ -z "$webauthn_origin" ]]; then
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
        --base "$url" \
        --origin "$webauthn_origin" \
        --expected-rp-id "$expected_rp_id" \
        --username "$username" \
        --display-name "Smoke Test" \
        --credential-file "$credential_file"; then
        rm -rf "$credential_dir"
        return 1
    fi
    if ! "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py \
        --base "$url" \
        --origin "$webauthn_origin" \
        --expected-rp-id "$expected_rp_id" \
        --username "$username" \
        --credential-file "$credential_file" \
        --signin-only; then
        rm -rf "$credential_dir"
        return 1
    fi
    rm -rf "$credential_dir"
    echo "[OK] full passkey signup/signin"
}

legacy_main() {
    if [[ $# -lt 1 ]]; then
        usage
        return 1
    fi
    echo "[WARN] legacy positional smoke-test invocation is deprecated; Task 6 must switch this caller to --contract-file" >&2
    local url="${1%/}"
    local webauthn_origin="${2:-}"
    local username
    LEGACY_EXPECTED_RP_ID=""
    username="smoketest_$(date +%s)_$$"
    local failed=0
    legacy_probe_health "$url" || failed=1
    legacy_probe_signup_begin "$url" "$username" || failed=1
    legacy_probe_entitlement_unauth "$url" || failed=1
    legacy_probe_passkey_end_to_end "$url" "$webauthn_origin" "$username" "$LEGACY_EXPECTED_RP_ID" || failed=1
    if [[ $failed -ne 0 ]]; then
        echo "Smoke test FAILED for $url" >&2
        return 1
    fi
    echo "Smoke test passed for $url"
}

if [[ $# -gt 0 && "$1" != --* ]]; then
    legacy_main "$@"
    exit $?
fi

# ---------------------------------------------------------------------------
# Immutable-contract durability proof.
# ---------------------------------------------------------------------------

SERVICE_URL=""
CONTRACT_FILE=""
EXPECTED_RELEASE=""
INVOKER_TOKEN_FILE=""
PHASE=""
STATE_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --service-url)
            [[ $# -ge 2 ]] || { fail "--service-url requires a value"; exit 1; }
            SERVICE_URL="$2"
            shift 2
            ;;
        --contract-file)
            [[ $# -ge 2 ]] || { fail "--contract-file requires a value"; exit 1; }
            CONTRACT_FILE="$2"
            shift 2
            ;;
        --expected-release)
            [[ $# -ge 2 ]] || { fail "--expected-release requires a value"; exit 1; }
            EXPECTED_RELEASE="$2"
            shift 2
            ;;
        --invoker-token-file)
            [[ $# -ge 2 ]] || { fail "--invoker-token-file requires a value"; exit 1; }
            INVOKER_TOKEN_FILE="$2"
            shift 2
            ;;
        --phase)
            [[ $# -ge 2 ]] || { fail "--phase requires a value"; exit 1; }
            PHASE="$2"
            shift 2
            ;;
        --state-file)
            [[ $# -ge 2 ]] || { fail "--state-file requires a value"; exit 1; }
            STATE_FILE="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            fail "unknown argument: $1"
            usage
            exit 1
            ;;
    esac
done

[[ -n "$SERVICE_URL" ]] || { fail "--service-url is required"; exit 1; }
[[ -n "$CONTRACT_FILE" ]] || { fail "--contract-file is required"; exit 1; }
[[ -n "$EXPECTED_RELEASE" ]] || { fail "--expected-release is required"; exit 1; }
[[ -n "$INVOKER_TOKEN_FILE" ]] || { fail "--invoker-token-file is required"; exit 1; }
[[ -n "$PHASE" ]] || { fail "--phase is required"; exit 1; }
[[ -n "$STATE_FILE" ]] || { fail "--state-file is required"; exit 1; }

case "$PHASE" in
    prepare|verify-and-cleanup|health-only) ;;
    *)
        fail "--phase must be prepare, verify-and-cleanup, or health-only"
        exit 1
        ;;
esac

SERVICE_URL="${SERVICE_URL%/}"
if ! is_https_origin "$SERVICE_URL"; then
    fail "--service-url must be an origin-only HTTPS URL"
    exit 1
fi
if [[ -L "$CONTRACT_FILE" || ! -f "$CONTRACT_FILE" ]]; then
    fail "contract file must be a regular file"
    exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
    fail "jq is required"
    exit 1
fi

state_parent="$(dirname "$STATE_FILE")"
if [[ ! -d "$state_parent" ]]; then
    fail "state file parent directory does not exist"
    exit 1
fi
STATE_FILE="$(cd "$state_parent" && pwd -P)/$(basename "$STATE_FILE")"
require_private_file "$STATE_FILE" "state file" true || exit 1

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/logdate-revision-smoke.XXXXXX")"
chmod 700 "$WORK_DIR"
PRIVATE_HEADER_FILE=""
HEALTH_HEADER_FILE=""
AUTH_HEADER_FILE=""
CREDENTIAL_FILE="${STATE_FILE}.credential"
CLEANUP_ARMED=0
USE_PRIVATE_HEADERS=0

write_header_file() {
    local path="$1"
    local name="$2"
    local value="$3"
    if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
        fail "refusing malformed $name header value"
        return 1
    fi
    : >"$path"
    chmod 600 "$path"
    printf '%s: %s\n' "$name" "$value" >"$path"
}

http_request() {
    local method="$1"
    local url="$2"
    local output_file="$3"
    local extra_header_file="${4:-}"
    shift 4
    local -a args=(
        --silent
        --show-error
        --max-time 30
        --request "$method"
        --output "$output_file"
        --write-out '%{http_code}'
    )
    if [[ "$USE_PRIVATE_HEADERS" == "1" ]]; then
        args+=(--header "@$PRIVATE_HEADER_FILE")
    fi
    if [[ -n "$extra_header_file" ]]; then
        args+=(--header "@$extra_header_file")
    fi
    curl "${args[@]}" "$@" "$url"
}

is_token_safe() {
    [[ "$1" =~ ^[A-Za-z0-9._~-]+$ ]]
}

remove_local_state() {
    rm -f -- "$STATE_FILE" "$CREDENTIAL_FILE"
}

best_effort_remote_cleanup() {
    resume_cleanup >/dev/null 2>&1
}

finish() {
    local status=$?
    trap - EXIT
    set +e
    if [[ "$status" -ne 0 && "$CLEANUP_ARMED" == "1" ]]; then
        best_effort_remote_cleanup
    fi
    rm -rf -- "$WORK_DIR"
    exit "$status"
}
trap finish EXIT

sorted_contract="$WORK_DIR/contract.sorted.json"
if ! jq -S . "$CONTRACT_FILE" >"$sorted_contract" 2>/dev/null; then
    fail "contract file is not valid JSON"
    exit 1
fi
if ! cmp -s "$CONTRACT_FILE" "$sorted_contract"; then
    fail "contract file must be sorted JSON"
    exit 1
fi
CONTRACT_SHA256="$(sha256_file "$CONTRACT_FILE")"

if ! jq -e '
    (.canonical_origin | type == "string" and length > 0) and
    (.release_sha | type == "string" and test("^[0-9a-f]{40}$")) and
    (.env_vars | type == "object") and
    (.env_vars.RELEASE_VERSION | type == "string" and length > 0) and
    (.env_vars.ATPROTO_HANDLE_DOMAIN | type == "string" and length > 0) and
    (.env_vars.WEBAUTHN_RP_ID | type == "string" and length > 0) and
    (.env_vars.WEBAUTHN_ALLOWED_ORIGINS | type == "string" and length > 0) and
    (.env_vars.ANDROID_CERT_FINGERPRINTS | type == "string" and length > 0) and
    (.android_signing.certificates | type == "object" and length > 0) and
    all(.android_signing.certificates[];
        (.fingerprint | type == "string" and length > 0) and
        (.apk_key_hash_origin | type == "string" and startswith("android:apk-key-hash:")))
' "$CONTRACT_FILE" >/dev/null; then
    fail "contract is missing required smoke-test identity fields"
    exit 1
fi

ANDROID_PACKAGE_NAME="$(jq -r '.android_package_name // empty' "$CONTRACT_FILE")"
if [[ -z "$ANDROID_PACKAGE_NAME" ]]; then
    fail "contract android_package_name is required"
    exit 1
fi
if [[ ! "$ANDROID_PACKAGE_NAME" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
    fail "contract android_package_name is malformed"
    exit 1
fi

CANONICAL_ORIGIN="$(jq -r '.canonical_origin' "$CONTRACT_FILE")"
if ! is_https_origin "$CANONICAL_ORIGIN"; then
    fail "contract canonical_origin must be an origin-only HTTPS URL"
    exit 1
fi
RELEASE_SHA="$(jq -r '.release_sha' "$CONTRACT_FILE")"
CONTRACT_RELEASE="$(jq -r '.env_vars.RELEASE_VERSION' "$CONTRACT_FILE")"
if [[ "$EXPECTED_RELEASE" != "$CONTRACT_RELEASE" ]]; then
    fail "--expected-release does not match the immutable contract"
    exit 1
fi
if [[ "$EXPECTED_RELEASE" != "logdate-server@$RELEASE_SHA" ]]; then
    fail "--expected-release does not match the immutable contract"
    exit 1
fi

HANDLE_DOMAIN="$(jq -r '.env_vars.ATPROTO_HANDLE_DOMAIN' "$CONTRACT_FILE")"
RP_ID="$(jq -r '.env_vars.WEBAUTHN_RP_ID' "$CONTRACT_FILE")"
if [[ ! "$RP_ID" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]$ || "$RP_ID" == *..* ]]; then
    fail "contract RP ID is not a valid hostname"
    exit 1
fi

while IFS=$'\t' read -r certificate apk_key_hash_origin; do
    if ! is_colon_hex_sha256 "$certificate"; then
        fail "contract Android certificate fingerprint is malformed"
        exit 1
    fi
    if ! is_apk_key_hash_origin "$apk_key_hash_origin"; then
        fail "contract Android apk-key-hash origin is malformed"
        exit 1
    fi
    if ! "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py \
        --certificate-fingerprint "$certificate" \
        --apk-key-hash-origin "$apk_key_hash_origin" >/dev/null 2>&1; then
        fail "contract Android apk-key-hash origin does not match its certificate fingerprint"
        exit 1
    fi
done < <(jq -r '.android_signing.certificates[] | [.fingerprint, .apk_key_hash_origin] | @tsv' "$CONTRACT_FILE")

if ! jq -e --arg canonical "$CANONICAL_ORIGIN" '
    def csv: split(",") | map(gsub("^[[:space:]]+|[[:space:]]+$"; "")) | map(select(length > 0));
    (.android_signing.certificates | to_entries | map(.value.fingerprint)) as $expected |
    (.env_vars.ANDROID_CERT_FINGERPRINTS | csv) as $actual |
    ($expected | length) == ($expected | unique | length) and
    ($actual | length) == ($actual | unique | length) and
    ($expected | sort) == ($actual | sort)
' "$CONTRACT_FILE" >/dev/null; then
    fail "contract certificate set does not match signing certificates"
    exit 1
fi
if ! jq -e --arg canonical "$CANONICAL_ORIGIN" '
    def csv: split(",") | map(gsub("^[[:space:]]+|[[:space:]]+$"; "")) | map(select(length > 0));
    ([.android_signing.certificates[] | .apk_key_hash_origin] + [$canonical]) as $expected |
    (.env_vars.WEBAUTHN_ALLOWED_ORIGINS | csv) as $actual |
    ($expected | length) == ($expected | unique | length) and
    ($actual | length) == ($actual | unique | length) and
    ($expected | sort) == ($actual | sort)
' "$CONTRACT_FILE" >/dev/null; then
    fail "contract Android origin set does not match signing certificates"
    exit 1
fi

EXPECTED_CERTIFICATES_SORTED="$(jq -r '.android_signing.certificates[].fingerprint' "$CONTRACT_FILE" | LC_ALL=C sort)"

if [[ "$PHASE" == "prepare" || "$PHASE" == "health-only" ]]; then
    require_private_file "$INVOKER_TOKEN_FILE" "invoker token file" || exit 1
    invoker_token="$(<"$INVOKER_TOKEN_FILE")"
    if [[ -z "$invoker_token" || "$invoker_token" == *[[:space:]]* ]]; then
        fail "invoker token file is empty or malformed"
        exit 1
    fi
    write_header_file "$WORK_DIR/private-header" "X-Serverless-Authorization" "Bearer $invoker_token"
    PRIVATE_HEADER_FILE="$WORK_DIR/private-header"
    USE_PRIVATE_HEADERS=1
elif [[ "$SERVICE_URL" != "$CANONICAL_ORIGIN" ]]; then
    fail "verify-and-cleanup must use the contract canonical origin"
    exit 1
fi

probe_public_health() {
    local body="$WORK_DIR/public-health.json"
    local status
    status="$(http_request GET "$SERVICE_URL/health" "$body" "")"
    if [[ "$status" != "200" ]]; then
        fail "public health did not return 200"
        return 1
    fi
    if ! jq -e '.status == "healthy"' "$body" >/dev/null 2>&1; then
        fail "public health status is not healthy"
        return 1
    fi
    if ! jq -e --arg expected "$EXPECTED_RELEASE" '.release == $expected' "$body" >/dev/null 2>&1; then
        fail "public health release does not match the expected release"
        return 1
    fi
}

configure_health_header() {
    if [[ -z "${HEALTH_INTERNAL_TOKEN:-}" ]]; then
        fail "HEALTH_INTERNAL_TOKEN must be provided through the environment"
        return 1
    fi
    write_header_file "$WORK_DIR/health-header" "X-LogDate-Health-Token" "$HEALTH_INTERNAL_TOKEN"
    HEALTH_HEADER_FILE="$WORK_DIR/health-header"
}

probe_internal_health() {
    local body="$WORK_DIR/internal-health.json"
    local status
    status="$(http_request GET "$SERVICE_URL/health" "$body" "$HEALTH_HEADER_FILE")"
    if [[ "$status" != "200" ]]; then
        fail "internal health did not return 200"
        return 1
    fi
    if ! jq -e --arg expected "$EXPECTED_RELEASE" \
        '.status == "healthy" and .release == $expected and .db_connected == true' \
        "$body" >/dev/null 2>&1; then
        fail "internal health did not report db_connected=true for the expected release"
        return 1
    fi
}

probe_descriptor() {
    local body="$WORK_DIR/descriptor.json"
    local status
    status="$(http_request GET "$SERVICE_URL/api/v1/server/info" "$body" "")"
    [[ "$status" == "200" ]] || { fail "server descriptor did not return 200"; return 1; }
    jq -e '.success == true and .data.deploymentKind == "FIRST_PARTY"' "$body" >/dev/null 2>&1 || {
        fail "descriptor deploymentKind is not FIRST_PARTY"
        return 1
    }
    jq -e --arg expected "$CANONICAL_ORIGIN" '.data.serverOrigin == $expected' "$body" >/dev/null 2>&1 || {
        fail "descriptor serverOrigin does not match the contract"
        return 1
    }
    jq -e --arg expected "$CANONICAL_ORIGIN/api/v1" '.data.apiBaseUrl == $expected' "$body" >/dev/null 2>&1 || {
        fail "descriptor apiBaseUrl does not match the contract"
        return 1
    }
    jq -e --arg expected "$HANDLE_DOMAIN" '.data.handleDomain == $expected' "$body" >/dev/null 2>&1 || {
        fail "descriptor handleDomain does not match the contract"
        return 1
    }
    jq -e --arg expected "$RP_ID" '.data.passkey.rpId == $expected' "$body" >/dev/null 2>&1 || {
        fail "descriptor RP ID does not match the contract"
        return 1
    }
    jq -e --argjson required "$REQUIRED_CAPABILITIES_JSON" \
        '(.data.capabilities | type == "array") and (($required - .data.capabilities) | length == 0)' \
        "$body" >/dev/null 2>&1 || {
        fail "descriptor is missing first-party capabilities"
        return 1
    }
}

probe_asset_links() {
    local body="$WORK_DIR/assetlinks.json"
    local status actual_certificates asset_links_origin
    asset_links_origin="$SERVICE_URL"
    if [[ "$PHASE" == "verify-and-cleanup" ]]; then
        asset_links_origin="https://$RP_ID"
    fi
    status="$(http_request GET "$asset_links_origin/.well-known/assetlinks.json" "$body" "")"
    [[ "$status" == "200" ]] || { fail "asset links did not return 200"; return 1; }
    jq -e --arg package "$ANDROID_PACKAGE_NAME" \
        'length == 1 and .[0].target.namespace == "android_app" and .[0].target.package_name == $package' \
        "$body" >/dev/null 2>&1 || {
        fail "asset links package does not match the contract"
        return 1
    }
    jq -e '
        (.[0].relation | sort) == ([
            "delegate_permission/common.get_login_creds",
            "delegate_permission/common.handle_all_urls"
        ] | sort)
    ' "$body" >/dev/null 2>&1 || {
        fail "asset links relations do not match"
        return 1
    }
    actual_certificates="$(jq -r '.[0].target.sha256_cert_fingerprints[]' "$body" 2>/dev/null | LC_ALL=C sort || true)"
    if [[ "$actual_certificates" != "$EXPECTED_CERTIFICATES_SORTED" ]]; then
        fail "asset links certificate set does not match the contract"
        return 1
    fi
}

probe_protected_route() {
    local body="$WORK_DIR/unauth-entitlement.json"
    local status
    status="$(http_request GET "$SERVICE_URL/api/v1/auth/me/entitlement" "$body" "")"
    if [[ "$status" != "401" ]]; then
        fail "unauthenticated entitlement route did not return 401"
        return 1
    fi
}

probe_google_auth_configuration() {
    local body="$WORK_DIR/google-auth-invalid-token.json"
    local status
    status="$(http_request POST "$SERVICE_URL/api/v1/auth/signin/google" "$body" "" \
        --header 'Content-Type: application/json' \
        --data '{"idToken":"invalid-deployment-smoke-token"}')"
    if [[ "$status" != "401" ]] ||
        ! jq -e '.error.code == "GOOGLE_TOKEN_INVALID"' "$body" >/dev/null 2>&1; then
        fail "Google sign-in configuration probe did not reject an invalid token"
        return 1
    fi
}

run_identity_probes() {
    probe_public_health
    probe_internal_health
    probe_descriptor
    probe_asset_links
    probe_protected_route
    probe_google_auth_configuration
}

make_auth_header() {
    local access_token
    access_token="$(jq -r '.accessToken // empty' "$STATE_FILE")"
    if [[ -z "$access_token" ]] || ! is_token_safe "$access_token"; then
        fail "state file access token is missing or malformed"
        return 1
    fi
    AUTH_HEADER_FILE="$WORK_DIR/auth-header"
    write_header_file "$AUTH_HEADER_FILE" "Authorization" "Bearer $access_token"
}

probe_seeded_free_entitlement() {
    local body="$WORK_DIR/entitlement.json"
    local status
    status="$(http_request GET "$SERVICE_URL/api/v1/auth/me/entitlement" "$body" "$AUTH_HEADER_FILE")"
    if [[ "$status" != "200" ]] || ! jq -e \
        --argjson bytes "$SEEDED_FREE_STORAGE_BYTES" \
        --argjson backups "$SEEDED_FREE_BACKUP_COUNT" '
            .planId == "free" and
            .tier == "FREE" and
            .status == "ACTIVE" and
            .storageBytesLimit == $bytes and
            .backupCountLimit == $backups
        ' "$body" >/dev/null 2>&1; then
        fail "authenticated entitlement is not the seeded free tier"
        return 1
    fi
}

probe_quota_usage() {
    local minimum_used="$1"
    local body="$WORK_DIR/quota.json"
    local status
    status="$(http_request GET "$SERVICE_URL/api/v1/quota" "$body" "$AUTH_HEADER_FILE")"
    if [[ "$status" != "200" ]] || ! jq -e \
        --argjson total "$SEEDED_FREE_STORAGE_BYTES" \
        --argjson minimum "$minimum_used" '
            (.totalBytes == $total) and
            (.usedBytes | type == "number") and
            (.usedBytes >= $minimum) and
            (.usedBytes <= .totalBytes)
        ' "$body" >/dev/null 2>&1; then
        fail "authenticated quota is unlimited, malformed, or not the seeded free tier"
        return 1
    fi
}

invoke_passkey_prepare() {
    local username="$1"
    [[ -x "$PASSKEY_VERIFY_PYTHON" ]] || { fail "passkey verifier Python is not executable"; return 1; }
    local -a args=(
        scripts/passkey-verify/sim.py
        --base "$SERVICE_URL"
        --origin "$CANONICAL_ORIGIN"
        --expected-rp-id "$RP_ID"
        --username "$username"
        --display-name "Deployment Smoke Test"
        --credential-file "$CREDENTIAL_FILE"
        --state-file "$STATE_FILE"
    )
    if [[ "$USE_PRIVATE_HEADERS" == "1" ]]; then
        args+=(--private-service-token-file "$INVOKER_TOKEN_FILE")
    fi
    "$PASSKEY_VERIFY_PYTHON" "${args[@]}"
}

invoke_passkey_signin() {
    local username="$1"
    [[ -x "$PASSKEY_VERIFY_PYTHON" ]] || { fail "passkey verifier Python is not executable"; return 1; }
    local -a args=(
        scripts/passkey-verify/sim.py
        --base "$SERVICE_URL"
        --origin "$CANONICAL_ORIGIN"
        --expected-rp-id "$RP_ID"
        --username "$username"
        --credential-file "$CREDENTIAL_FILE"
        --state-file "$STATE_FILE"
        --credential-previous-base "$(jq -r '.preparedBaseUrl' "$STATE_FILE")"
        --signin-only
    )
    if [[ "$USE_PRIVATE_HEADERS" == "1" ]]; then
        args+=(--private-service-token-file "$INVOKER_TOKEN_FILE")
    fi
    "$PASSKEY_VERIFY_PYTHON" "${args[@]}"
}

validate_deploy_state() {
    require_private_file "$STATE_FILE" "state file" || return 1
    require_private_file "$CREDENTIAL_FILE" "credential file" || return 1
    jq -e --arg credential "$CREDENTIAL_FILE" --arg rp_id "$RP_ID" '
        .format == "logdate-deploy-smoke-v2" and
        (.username | type == "string" and length > 0) and
        (.accountId | type == "string" and length > 0) and
        (
            (.recoveryMode == true and
                ((.accessToken // "") | type == "string") and
                ((.refreshToken // "") | type == "string")) or
            (.recoveryMode != true and
                (.accessToken | type == "string" and length > 0) and
                (.refreshToken | type == "string" and length > 0))
        ) and
        (.credentialId | type == "string" and length > 0) and
        (.userHandle | type == "string" and length > 0) and
        (.preparedBaseUrl | type == "string" and length > 0) and
        (.canonicalOrigin | type == "string" and length > 0) and
        .expectedRpId == $rp_id and
        (.cleanup | type == "object") and
        (.cleanup.identityVerified | type == "boolean") and
        (.cleanup.mediaDeleted | type == "boolean") and
        (.cleanup.refreshRevocationStarted | type == "boolean") and
        (.cleanup.refreshRevoked | type == "boolean") and
        (.cleanup.accountDeletionStarted | type == "boolean") and
        (.cleanup.accountDeleted | type == "boolean") and
        .credentialFile == $credential
    ' "$STATE_FILE" >/dev/null 2>&1 || {
        fail "state file is missing required account or credential fields"
        return 1
    }
}

replace_private_state() {
    local source="$1"
    require_private_file "$source" "updated state file" || return 1
    "$PASSKEY_VERIFY_PYTHON" scripts/passkey-verify/sim.py \
        --atomic-replace-json \
        --input-json-file "$source" \
        --output-json-file "$STATE_FILE"
}

update_cleanup_phase() {
    local phase="$1"
    local value="$2"
    local updated_state="$WORK_DIR/cleanup-$phase.json"
    jq -S --arg phase "$phase" --argjson value "$value" \
        '.cleanup[$phase] = $value' "$STATE_FILE" >"$updated_state"
    chmod 600 "$updated_state"
    replace_private_state "$updated_state"
}

set_cleanup_identity() {
    local verified="$1"
    local updated_state="$WORK_DIR/cleanup-identity.json"
    jq -S --argjson verified "$verified" --arg origin "$SERVICE_URL" '
        .cleanup.identityVerified = $verified |
        .cleanup.identityVerifiedOrigin = (if $verified then $origin else null end)
    ' "$STATE_FILE" >"$updated_state"
    chmod 600 "$updated_state"
    replace_private_state "$updated_state"
}

verify_reauthenticated_identity() {
    local body="$WORK_DIR/reauthenticated-identity.json"
    local status account_id username
    account_id="$(jq -r '.accountId' "$STATE_FILE")"
    username="$(jq -r '.username' "$STATE_FILE")"
    status="$(http_request GET "$SERVICE_URL/api/v1/auth/me" "$body" "$AUTH_HEADER_FILE")"
    if [[ "$status" != "200" ]] || ! jq -e \
        --arg account_id "$account_id" \
        --arg username "$username" '
            .success == true and
            .data.account.id == $account_id and
            .data.account.username == $username
        ' "$body" >/dev/null 2>&1; then
        fail "reauthenticated identity does not match prepared state"
        return 1
    fi
    set_cleanup_identity true
}

resume_cleanup() {
    [[ -s "$STATE_FILE" ]] || return 1
    if ! jq -e --arg origin "$SERVICE_URL" '
        .cleanup.identityVerified == true and
        .cleanup.identityVerifiedOrigin == $origin
    ' "$STATE_FILE" >/dev/null 2>&1; then
        fail "cleanup identity has not been verified for this service origin"
        return 1
    fi

    local access_token refresh_token media_id body status logout_request
    access_token="$(jq -r '.accessToken // empty' "$STATE_FILE")"
    refresh_token="$(jq -r '.refreshToken // empty' "$STATE_FILE")"
    media_id="$(jq -r '.mediaId // empty' "$STATE_FILE")"
    if [[ -z "$access_token" ]] || ! is_token_safe "$access_token"; then
        fail "state file access token is missing or malformed"
        return 1
    fi
    AUTH_HEADER_FILE="$WORK_DIR/cleanup-auth-header"
    write_header_file "$AUTH_HEADER_FILE" "Authorization" "Bearer $access_token"

    if ! jq -e '.cleanup.mediaDeleted == true' "$STATE_FILE" >/dev/null; then
        if [[ -n "$media_id" ]]; then
            if [[ ! "$media_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
                fail "state file media ID is malformed"
                return 1
            fi
            body="$WORK_DIR/media-delete.json"
            status="$(http_request DELETE "$SERVICE_URL/api/v1/media/$media_id" "$body" "$AUTH_HEADER_FILE")"
            if [[ "$status" != "204" && "$status" != "404" ]]; then
                fail "media deletion did not return 204 or 404"
                return 1
            fi
            body="$WORK_DIR/media-after-delete.json"
            status="$(http_request GET "$SERVICE_URL/api/v1/media/$media_id/binary" "$body" "$AUTH_HEADER_FILE")"
            if [[ "$status" != "404" ]]; then
                fail "media remained readable after deletion"
                return 1
            fi
        fi
        update_cleanup_phase mediaDeleted true
    fi

    if ! jq -e '.cleanup.refreshRevoked == true' "$STATE_FILE" >/dev/null; then
        local refresh_revocation_started="false"
        refresh_revocation_started="$(jq -r '.cleanup.refreshRevocationStarted' "$STATE_FILE")"
        if [[ "$refresh_revocation_started" != "true" ]]; then
            update_cleanup_phase refreshRevocationStarted true || return 1
        fi
        if [[ -z "$refresh_token" ]] || ! is_token_safe "$refresh_token"; then
            fail "state file refresh token is missing or malformed"
            return 1
        fi
        logout_request="$WORK_DIR/logout-request.json"
        printf '{"refreshToken":"%s"}' "$refresh_token" >"$logout_request"
        chmod 600 "$logout_request"
        body="$WORK_DIR/logout-response.json"
        status="$(http_request POST "$SERVICE_URL/api/v1/auth/logout" "$body" "" \
            --header 'Content-Type: application/json' --data-binary "@$logout_request")"
        if [[ "$status" != "200" ]] &&
            ! [[ "$refresh_revocation_started" == "true" && "$status" == "401" ]]; then
            fail "token revocation did not return 200"
            return 1
        fi
        body="$WORK_DIR/refresh-after-logout.json"
        status="$(http_request POST "$SERVICE_URL/api/v1/auth/token/refresh" "$body" "" \
            --header 'Content-Type: application/json' --data-binary "@$logout_request")"
        if [[ "$status" != "401" ]]; then
            fail "revoked refresh token remained usable"
            return 1
        fi
        update_cleanup_phase refreshRevoked true
    fi

    if ! jq -e '.cleanup.accountDeleted == true' "$STATE_FILE" >/dev/null; then
        local account_deletion_started="false"
        account_deletion_started="$(jq -r '.cleanup.accountDeletionStarted' "$STATE_FILE")"
        if [[ "$account_deletion_started" != "true" ]]; then
            update_cleanup_phase accountDeletionStarted true || return 1
            account_deletion_started="true"
        fi
        body="$WORK_DIR/account-delete.json"
        status="$(http_request DELETE "$SERVICE_URL/api/v1/auth/me" "$body" "$AUTH_HEADER_FILE")"
        if [[ "$status" != "204" && "$status" != "404" ]] &&
            ! [[ "$account_deletion_started" == "true" && "$status" == "401" ]]; then
            fail "account deletion did not return 204 or 404 (status=$status response=$(tr '\\n' ' ' <\"$body\" | cut -c1-400))"
            return 1
        fi
        body="$WORK_DIR/account-after-delete.json"
        status="$(http_request GET "$SERVICE_URL/api/v1/auth/me" "$body" "$AUTH_HEADER_FILE")"
        if [[ "$status" != "401" && "$status" != "404" ]]; then
            fail "deleted account remained readable"
            return 1
        fi
        update_cleanup_phase accountDeleted true
    fi

    remove_local_state
}

validate_prepared_endpoint_binding() {
    local prepared_base credential_base credential_origin credential_username credential_account credential_id credential_rp
    prepared_base="$(jq -r '.preparedBaseUrl // empty' "$STATE_FILE")"
    credential_base="$(jq -r '.baseUrl // empty' "$CREDENTIAL_FILE")"
    credential_origin="$(jq -r '.origin // empty' "$CREDENTIAL_FILE")"
    credential_username="$(jq -r '.username // empty' "$CREDENTIAL_FILE")"
    credential_account="$(jq -r '.accountId // empty' "$CREDENTIAL_FILE")"
    credential_id="$(jq -r '.credentialId // empty' "$CREDENTIAL_FILE")"
    credential_rp="$(jq -r '.expectedRpId // empty' "$CREDENTIAL_FILE")"
    if ! is_https_origin "$prepared_base" || [[ "$prepared_base" != "$credential_base" ]]; then
        fail "prepared candidate origin does not match credential state"
        return 1
    fi
    if [[ "$credential_origin" != "$CANONICAL_ORIGIN" ||
        "$credential_username" != "$(jq -r '.username' "$STATE_FILE")" ||
        "$credential_account" != "$(jq -r '.accountId' "$STATE_FILE")" ||
        "$credential_id" != "$(jq -r '.credentialId' "$STATE_FILE")" ||
        "$credential_rp" != "$RP_ID" ||
        "$(jq -r '.expectedRpId' "$STATE_FILE")" != "$RP_ID" ]]; then
        fail "prepared credential identity does not match deployment state"
        return 1
    fi
}

validate_prepared_state_binding() {
    jq -e \
        --arg release "$EXPECTED_RELEASE" \
        --arg canonical "$CANONICAL_ORIGIN" \
        --arg contract_sha "$CONTRACT_SHA256" '
            .expectedRelease == $release and
            .canonicalOrigin == $canonical and
            .contractSha256 == $contract_sha
        ' "$STATE_FILE" >/dev/null 2>&1 || {
        fail "prepared state does not match the current contract and release"
        return 1
    }
}

upload_media_fixture() {
    local media_file="$WORK_DIR/media.bin"
    dd if=/dev/urandom of="$media_file" bs=1024 count=1 2>/dev/null
    chmod 600 "$media_file"
    local media_size media_sha body status media_id updated_state
    media_size="$(wc -c <"$media_file" | tr -d ' ')"
    media_sha="$(sha256_file "$media_file")"
    body="$WORK_DIR/media-upload.json"
    status="$(http_request POST "$SERVICE_URL/api/v1/media" "$body" "$AUTH_HEADER_FILE" \
        --form 'contentId=deploy-smoke-content' \
        --form 'fileName=deploy-smoke.bin' \
        --form 'mimeType=application/octet-stream' \
        --form "sizeBytes=$media_size" \
        --form 'deviceId=deploy-smoke-device' \
        --form "data=@$media_file;type=application/octet-stream")"
    if [[ "$status" != "201" ]]; then
        fail "media upload did not return 201"
        return 1
    fi
    media_id="$(jq -r '.mediaId // empty' "$body")"
    if [[ -z "$media_id" || ! "$media_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
        fail "media upload response omitted a safe media ID"
        return 1
    fi
    updated_state="$WORK_DIR/state-with-media.json"
    jq -S \
        --arg media_id "$media_id" \
        --arg media_sha "$media_sha" \
        --argjson media_size "$media_size" \
        --arg release "$EXPECTED_RELEASE" \
        --arg canonical "$CANONICAL_ORIGIN" \
        --arg contract_sha "$CONTRACT_SHA256" \
        '. + {
            mediaId:$media_id,
            mediaSha256:$media_sha,
            mediaSizeBytes:$media_size,
            expectedRelease:$release,
            canonicalOrigin:$canonical,
            contractSha256:$contract_sha
        }' \
        "$STATE_FILE" >"$updated_state"
    chmod 600 "$updated_state"
    replace_private_state "$updated_state"
}

strict_cleanup() {
    resume_cleanup
}

case "$PHASE" in
    health-only)
        configure_health_header
        run_identity_probes
        echo "Health and first-party identity proof passed for $SERVICE_URL"
        ;;
    prepare)
        if [[ -s "$STATE_FILE" ]]; then
            if jq -e '
                .format == "logdate-deploy-smoke-v2" and
                (.recoveryMode == true or ((.mediaId // "") | length == 0))
            ' \
                "$STATE_FILE" >/dev/null 2>&1; then
                validate_deploy_state
                validate_prepared_endpoint_binding
                if ! jq -e --arg origin "$SERVICE_URL" '
                    .cleanup.identityVerified == true and
                    .cleanup.identityVerifiedOrigin == $origin
                ' "$STATE_FILE" >/dev/null 2>&1; then
                    username="$(jq -r '.username' "$STATE_FILE")"
                    invoke_passkey_signin "$username"
                    validate_deploy_state
                    make_auth_header
                    verify_reauthenticated_identity
                fi
                CLEANUP_ARMED=0
                strict_cleanup
                echo "Recovered and cleaned disposable account for $SERVICE_URL"
                exit 0
            fi
            fail "prepare state file must be empty or a recoverable verifier state"
            exit 1
        fi
        if [[ -e "$CREDENTIAL_FILE" ]]; then
            fail "prepare credential file already exists"
            exit 1
        fi
        configure_health_header
        run_identity_probes
        username="smoketest_${RELEASE_SHA:0:8}_$(date +%s)_${RANDOM}"
        invoke_passkey_prepare "$username"
        validate_deploy_state
        make_auth_header
        verify_reauthenticated_identity
        CLEANUP_ARMED=1
        probe_seeded_free_entitlement
        upload_media_fixture
        probe_quota_usage "$(jq -r '.mediaSizeBytes' "$STATE_FILE")"
        set_cleanup_identity false
        CLEANUP_ARMED=0
        echo "Durability prepare passed for $SERVICE_URL"
        ;;
    verify-and-cleanup)
        validate_deploy_state
        validate_prepared_endpoint_binding
        if jq -e --arg origin "$SERVICE_URL" '
            .cleanup.identityVerified == true and
            .cleanup.identityVerifiedOrigin == $origin
        ' "$STATE_FILE" >/dev/null 2>&1; then
            CLEANUP_ARMED=0
            strict_cleanup
        elif jq -e '.recoveryMode == true' "$STATE_FILE" >/dev/null 2>&1; then
            username="$(jq -r '.username' "$STATE_FILE")"
            invoke_passkey_signin "$username"
            validate_deploy_state
            make_auth_header
            verify_reauthenticated_identity
            CLEANUP_ARMED=1
            CLEANUP_ARMED=0
            strict_cleanup
        else
            validate_prepared_state_binding
            configure_health_header
            run_identity_probes
            if ! jq -e '
                (.mediaId | type == "string" and length > 0) and
                (.mediaSha256 | type == "string" and test("^[0-9a-f]{64}$")) and
                (.mediaSizeBytes | type == "number" and . > 0)
            ' "$STATE_FILE" >/dev/null 2>&1; then
                fail "state file is missing prepared media proof"
                exit 1
            fi
            username="$(jq -r '.username' "$STATE_FILE")"
            invoke_passkey_signin "$username"
            validate_deploy_state
            make_auth_header
            verify_reauthenticated_identity
            CLEANUP_ARMED=1
            probe_seeded_free_entitlement
            media_id="$(jq -r '.mediaId' "$STATE_FILE")"
            expected_media_sha="$(jq -r '.mediaSha256' "$STATE_FILE")"
            media_size="$(jq -r '.mediaSizeBytes' "$STATE_FILE")"
            downloaded_media="$WORK_DIR/downloaded-media.bin"
            download_status="$(http_request GET "$SERVICE_URL/api/v1/media/$media_id/binary" "$downloaded_media" "$AUTH_HEADER_FILE")"
            [[ "$download_status" == "200" ]] || { fail "prepared media download did not return 200"; exit 1; }
            actual_media_sha="$(sha256_file "$downloaded_media")"
            if [[ "$actual_media_sha" != "$expected_media_sha" ]]; then
                fail "downloaded media SHA-256 does not match prepared media"
                exit 1
            fi
            probe_quota_usage "$media_size"
            CLEANUP_ARMED=0
            strict_cleanup
        fi
        CLEANUP_ARMED=0
        echo "Durability verification and cleanup passed for $SERVICE_URL"
        ;;
esac
