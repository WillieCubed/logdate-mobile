#!/usr/bin/env bash
# Behavioral regression tests for the Cloud Run revision durability proof.

set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/smoke-test-revision.sh"
RELEASE_SHA="09aa934a2605f1bb82adcf8b81ed96e7037e2010"
EXPECTED_RELEASE="logdate-server@$RELEASE_SHA"
CANONICAL_ORIGIN="https://cloud-staging.example.test"
CANDIDATE_ORIGIN="https://candidate.example.test"
HANDLE_DOMAIN="example.test"
RP_ID="example.test"
PACKAGE_NAME="co.reasonabletech.logdate"
CERT_ONE="11:98:70:B8:78:F3:AB:5F:55:C0:DF:65:C7:87:89:C0:24:59:CA:9F:F3:22:A0:89:40:AE:43:A2:9D:1D:D5:AB"
CERT_TWO="F1:3E:F5:D0:EC:93:ED:B0:8C:6C:F2:1D:8A:12:84:99:42:C2:92:D8:ED:EC:26:C0:E4:46:0C:3C:71:BC:6E:5F"
APK_ORIGIN_ONE="android:apk-key-hash:EZhwuHjzq19VwN9lx4eJwCRZyp_zIqCJQK5Dop0d1as"
APK_ORIGIN_TWO="android:apk-key-hash:8T710OyT7bCMbPIdihKEmULCktjt7CbA5EYMPHG8bl8"
PRIVATE_TOKEN="private-candidate-token-do-not-log"
HEALTH_TOKEN="internal-health-token-do-not-log"
ACCESS_TOKEN="logdate-access-token-do-not-log"
REFRESH_TOKEN="logdate-refresh-token-do-not-log"
CHALLENGE_SECRET="challenge-payload-do-not-log"
ACCOUNT_ID="11111111-1111-4111-8111-111111111111"
USER_HANDLE="$(printf '%s' "$ACCOUNT_ID" | base64 | tr -d '\n=' | tr '+/' '-_')"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

mkdir -p "$tmp_dir/bin" "$tmp_dir/http" "$tmp_dir/tmp"

contract="$tmp_dir/contract.json"
cat >"$tmp_dir/contract.unsorted.json" <<EOF
{
  "release_sha": "$RELEASE_SHA",
  "canonical_origin": "$CANONICAL_ORIGIN",
  "android_package_name": "$PACKAGE_NAME",
  "env_vars": {
    "RELEASE_VERSION": "$EXPECTED_RELEASE",
    "ATPROTO_HANDLE_DOMAIN": "$HANDLE_DOMAIN",
    "WEBAUTHN_RP_ID": "$RP_ID",
    "WEBAUTHN_ALLOWED_ORIGINS": "$CANONICAL_ORIGIN,$APK_ORIGIN_ONE,$APK_ORIGIN_TWO",
    "ANDROID_CERT_FINGERPRINTS": "$CERT_ONE,$CERT_TWO"
  },
  "android_signing": {
    "certificates": {
      "upload": {
        "fingerprint": "$CERT_ONE",
        "apk_key_hash_origin": "$APK_ORIGIN_ONE"
      },
      "play_app_signing": {
        "fingerprint": "$CERT_TWO",
        "apk_key_hash_origin": "$APK_ORIGIN_TWO"
      }
    }
  }
}
EOF
jq -S . "$tmp_dir/contract.unsorted.json" >"$contract"
chmod 600 "$contract"

invoker_token_file="$tmp_dir/invoker-token"
printf '%s\n' "$PRIVATE_TOKEN" >"$invoker_token_file"
chmod 600 "$invoker_token_file"

# Fake curl is a stateful HTTP fixture. It records only whether protected headers
# were present, never their values, and persists uploaded bytes between phases.
cat >"$tmp_dir/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

method="GET"
output_file=""
write_out=""
url=""
declare -a header_files=("")
declare -a literal_headers=("")
declare -a forms=("")

while [[ $# -gt 0 ]]; do
    case "$1" in
        --request|-X)
            method="$2"
            shift 2
            ;;
        --output|-o)
            output_file="$2"
            shift 2
            ;;
        --write-out|-w)
            write_out="$2"
            shift 2
            ;;
        --header|-H)
            if [[ "$2" == @* ]]; then
                header_files+=("${2#@}")
            else
                literal_headers+=("$2")
            fi
            shift 2
            ;;
        --form|-F)
            forms+=("$2")
            shift 2
            ;;
        --data|--data-raw|--data-binary)
            shift 2
            ;;
        --silent|--show-error|--fail-with-body|--location)
            shift
            ;;
        --max-time|--connect-timeout)
            shift 2
            ;;
        --*)
            echo "fake curl received unsupported option: $1" >&2
            exit 96
            ;;
        *)
            url="$1"
            shift
            ;;
    esac
done

[[ -n "$url" ]] || { echo "fake curl received no URL" >&2; exit 95; }

headers=""
for file in "${header_files[@]}"; do
    [[ -n "$file" ]] || continue
    [[ -f "$file" ]] || { echo "missing curl header file" >&2; exit 94; }
    headers+="$(cat "$file")"$'\n'
done
for header in "${literal_headers[@]}"; do
    [[ -n "$header" ]] || continue
    if [[ "$header" == *"${FAKE_PRIVATE_TOKEN:?}"* ||
        "$header" == *"${FAKE_ACCESS_TOKEN:?}"* ||
        "$header" == *"${FAKE_REFRESH_TOKEN:?}"* ||
        "$header" == *"${FAKE_HEALTH_TOKEN:?}"* ]]; then
        echo "secret value was passed in curl argv" >&2
        exit 97
    fi
    headers+="$header"$'\n'
done

serverless=0
health=0
authorization=0
if grep -Fqx "X-Serverless-Authorization: Bearer ${FAKE_PRIVATE_TOKEN:?}" <<<"$headers"; then
    serverless=1
fi
if grep -Fqx "X-LogDate-Health-Token: ${FAKE_HEALTH_TOKEN:?}" <<<"$headers"; then
    health=1
fi
if grep -Fqx "Authorization: Bearer ${FAKE_ACCESS_TOKEN:?}" <<<"$headers"; then
    authorization=1
fi

if [[ "${FAKE_EXPECT_SERVERLESS:-0}" == "1" && "$serverless" != "1" ]]; then
    echo "private candidate request omitted X-Serverless-Authorization" >&2
    exit 93
fi
if [[ "${FAKE_EXPECT_SERVERLESS:-0}" == "0" && "$serverless" != "0" ]]; then
    echo "canonical request unexpectedly used X-Serverless-Authorization" >&2
    exit 92
fi

path="${url#*://}"
path="/${path#*/}"
url_without_scheme="${url#*://}"
request_origin="${url%%://*}://${url_without_scheme%%/*}"
printf '%s %s serverless=%s health=%s authorization=%s\n' \
    "$method" "$path" "$serverless" "$health" "$authorization" >>"${FAKE_HTTP_LOG:?}"

scenario="${SMOKE_SCENARIO:-ok}"
status="200"
body=""
binary_file=""

case "$path" in
    /health)
        if [[ "$health" == "1" ]]; then
            case "$scenario" in
                internal_db_absent)
                    body="{\"status\":\"healthy\",\"release\":\"${FAKE_EXPECTED_RELEASE}\"}"
                    ;;
                internal_db_false)
                    body="{\"status\":\"healthy\",\"release\":\"${FAKE_EXPECTED_RELEASE}\",\"db_connected\":false}"
                    ;;
                *)
                    body="{\"status\":\"healthy\",\"release\":\"${FAKE_EXPECTED_RELEASE}\",\"db_connected\":true}"
                    ;;
            esac
        elif [[ "$scenario" == "public_wrong_release" ]]; then
            body='{"status":"healthy","release":"logdate-server@wrong"}'
        else
            body="{\"status\":\"healthy\",\"release\":\"${FAKE_EXPECTED_RELEASE}\"}"
        fi
        ;;
    /api/v1/server/info)
        deployment_kind="FIRST_PARTY"
        server_origin="${FAKE_CANONICAL_ORIGIN}"
        api_base_url="${FAKE_CANONICAL_ORIGIN}/api/v1"
        handle_domain="${FAKE_HANDLE_DOMAIN}"
        rp_id="${FAKE_RP_ID}"
        capabilities='["CLOUD_TRANSCRIPTION","MANAGED_QUOTA","BILLING_SUBSCRIPTIONS","ATPROTO_OAUTH","ATPROTO_IDENTITY","SYNC_MEDIA","SYNC_CONTENT","AUTH_PASSKEY"]'
        case "$scenario" in
            descriptor_kind) deployment_kind="SELF_HOSTED" ;;
            descriptor_server_origin) server_origin="https://wrong.example.test" ;;
            descriptor_api_base) api_base_url="https://wrong.example.test/api/v1" ;;
            descriptor_handle) handle_domain="wrong.example.test" ;;
            descriptor_rp) rp_id="wrong.example.test" ;;
            descriptor_capability) capabilities='["AUTH_PASSKEY","SYNC_CONTENT"]' ;;
        esac
        body="{\"success\":true,\"data\":{\"serverOrigin\":\"$server_origin\",\"apiBaseUrl\":\"$api_base_url\",\"apiVersion\":\"v1\",\"deploymentKind\":\"$deployment_kind\",\"displayName\":\"LogDate Cloud (Staging)\",\"handleDomain\":\"$handle_domain\",\"passkey\":{\"rpId\":\"$rp_id\",\"rpName\":\"LogDate\"},\"capabilities\":$capabilities}}"
        ;;
    /.well-known/assetlinks.json)
        if [[ "$request_origin" != "${FAKE_EXPECT_ASSET_ORIGIN:?}" ]]; then
            echo "asset links requested from $request_origin instead of the RP origin" >&2
            exit 89
        fi
        package_name="${FAKE_PACKAGE_NAME}"
        relations='["delegate_permission/common.handle_all_urls","delegate_permission/common.get_login_creds"]'
        certificates="[\"${FAKE_CERT_TWO}\",\"${FAKE_CERT_ONE}\"]"
        case "$scenario" in
            assetlinks_package) package_name="app.logdate.wrong" ;;
            assetlinks_relation) relations='["delegate_permission/common.handle_all_urls"]' ;;
            assetlinks_missing_cert) certificates="[\"${FAKE_CERT_ONE}\"]" ;;
            assetlinks_extra_cert) certificates="[\"${FAKE_CERT_ONE}\",\"${FAKE_CERT_TWO}\",\"AA:BB\"]" ;;
        esac
        body="[{\"relation\":$relations,\"target\":{\"namespace\":\"android_app\",\"package_name\":\"$package_name\",\"sha256_cert_fingerprints\":$certificates}}]"
        ;;
    /api/v1/auth/signup/passkey/begin)
        body="{\"success\":true,\"data\":{\"sessionToken\":\"test-session\",\"registrationOptions\":{\"rpId\":\"${FAKE_RP_ID:?}\"}}}"
        ;;
    /api/v1/auth/signup/passkey/complete|/api/v1/auth/signin/passkey/begin|/api/v1/auth/signin/passkey/complete)
        body='{"sessionToken":"test-session","success":true,"data":{}}'
        ;;
    /api/v1/auth/me/entitlement)
        if [[ "$authorization" == "0" ]]; then
            if [[ "$scenario" == "protected_not_401" ]]; then
                status="200"
                body='{"planId":"free"}'
            else
                status="401"
                body='{"error":"unauthorized"}'
            fi
        elif [[ "$scenario" == "quota_unlimited" ]]; then
            body='{"planId":"self_host_unlimited","tier":"UNLIMITED","status":"SELF_HOST","storageBytesLimit":null,"backupCountLimit":null,"features":{}}'
        elif [[ "$scenario" == "quota_wrong_free_tier" ]]; then
            body='{"planId":"free","tier":"FREE","status":"ACTIVE","storageBytesLimit":500000000,"backupCountLimit":2,"features":{}}'
        else
            body='{"planId":"free","tier":"FREE","status":"ACTIVE","storageBytesLimit":1073741824,"backupCountLimit":3,"features":{}}'
        fi
        ;;
    /api/v1/quota)
        if [[ "$scenario" == "quota_unlimited" ]]; then
            body='{"totalBytes":9223372036854775807,"usedBytes":0,"categories":[]}'
        elif [[ "$scenario" == "quota_wrong_free_tier" ]]; then
            body='{"totalBytes":500000000,"usedBytes":0,"categories":[]}'
        elif [[ -f "${FAKE_MEDIA_STORE:?}" ]]; then
            bytes="$(wc -c <"$FAKE_MEDIA_STORE" | tr -d ' ')"
            body="{\"totalBytes\":1073741824,\"usedBytes\":$bytes,\"categories\":[]}"
        else
            body='{"totalBytes":1073741824,"usedBytes":0,"categories":[]}'
        fi
        ;;
    /api/v1/media)
        [[ "$method" == "POST" ]] || { status="405"; body='{}'; }
        data_file=""
        for form in "${forms[@]}"; do
            [[ -n "$form" ]] || continue
            if [[ "$form" == data=@* ]]; then
                data_file="${form#data=@}"
                data_file="${data_file%%;*}"
            fi
        done
        [[ -n "$data_file" && -f "$data_file" ]] || { echo "media upload omitted data file" >&2; exit 91; }
        cp "$data_file" "$FAKE_MEDIA_STORE"
        body='{"contentId":"deploy-smoke-content","mediaId":"deploy-smoke-media","downloadUrl":"/api/v1/media/deploy-smoke-media/binary","uploadedAt":1730932569000}'
        status="201"
        ;;
    /api/v1/media/deploy-smoke-media/binary)
        if [[ "$method" != "GET" ]]; then
            status="405"
            body='{}'
        elif [[ -f "${FAKE_MEDIA_DELETED:?}" && "$scenario" != "media_readable_after_delete" ]]; then
            status="404"
            body='{"error":"not found"}'
        elif [[ ! -f "$FAKE_MEDIA_STORE" ]]; then
            status="404"
            body='{"error":"not found"}'
        elif [[ "$scenario" == "media_hash_changed" ||
            "$scenario" == "refresh_intent_checkpoint_failed_once" ||
            "$scenario" == "account_intent_checkpoint_failed_once" ]]; then
            body='changed-media-bytes'
        else
            binary_file="$FAKE_MEDIA_STORE"
        fi
        ;;
    /api/v1/media/deploy-smoke-media)
        if [[ "$method" == "DELETE" ]]; then
            if [[ "$scenario" == "media_delete_failed" ]] ||
                [[ "$scenario" == "media_delete_failed_once" && ! -f "${FAKE_MEDIA_DELETE_ATTEMPT:?}" ]]; then
                : >"$FAKE_MEDIA_DELETE_ATTEMPT"
                status="500"
                body="{\"error\":\"${FAKE_CHALLENGE_SECRET}\",\"token\":\"${FAKE_ACCESS_TOKEN}\"}"
            else
                : >"$FAKE_MEDIA_DELETED"
                status="204"
            fi
        elif [[ -f "${FAKE_MEDIA_DELETED:?}" ]]; then
            status="404"
            body='{"error":"not found"}'
        else
            body='{"mediaId":"deploy-smoke-media"}'
        fi
        ;;
    /api/v1/auth/me)
        if [[ "$method" == "DELETE" ]]; then
            [[ -f "${FAKE_IDENTITY_VERIFIED:?}" ]] || {
                echo "account deletion attempted before exact identity verification" >&2
                exit 88
            }
            if [[ -f "${FAKE_ACCOUNT_DELETED:?}" ]]; then
                status="401"
                body='{"error":"unauthorized"}'
            elif [[ "$scenario" == "account_cleanup_failed" ]] ||
                [[ "$scenario" == "account_cleanup_failed_once" && ! -f "${FAKE_ACCOUNT_DELETE_ATTEMPT:?}" ]]; then
                : >"$FAKE_ACCOUNT_DELETE_ATTEMPT"
                status="500"
                body='{"error":"cleanup failed"}'
            else
                : >"${FAKE_ACCOUNT_DELETED:?}"
                status="204"
            fi
        elif [[ -f "${FAKE_ACCOUNT_DELETED:?}" ]]; then
            status="401"
            body='{"error":"unauthorized"}'
        elif [[ "$authorization" != "1" ]]; then
            status="401"
            body='{"error":"unauthorized"}'
        elif [[ "$scenario" == "identity_swapped" ]]; then
            body='{"success":true,"data":{"account":{"id":"22222222-2222-4222-8222-222222222222","username":"swapped-user"},"tokens":{"accessToken":"fresh-access","refreshToken":"fresh-refresh"}}}'
        else
            identity_username="$(jq -r '.username // empty' "${FAKE_STATE_FILE:?}")"
            : >"${FAKE_IDENTITY_VERIFIED:?}"
            body="{\"success\":true,\"data\":{\"account\":{\"id\":\"11111111-1111-4111-8111-111111111111\",\"username\":\"$identity_username\"},\"tokens\":{\"accessToken\":\"fresh-access\",\"refreshToken\":\"fresh-refresh\"}}}"
        fi
        ;;
    /api/v1/auth/logout)
        if [[ "$scenario" == "logout_failed_once" && ! -f "${FAKE_LOGOUT_ATTEMPT:?}" ]]; then
            : >"$FAKE_LOGOUT_ATTEMPT"
            status="500"
            body='{"error":"logout failed"}'
        elif [[ "$scenario" == "refresh_checkpoint_failed_once" && -f "${FAKE_LOGOUT_CALLED:?}" ]]; then
            status="401"
            body='{"error":"already revoked"}'
        else
            : >"${FAKE_LOGOUT_CALLED:?}"
            body='{"ok":true}'
        fi
        ;;
    /api/v1/auth/token/refresh)
        if [[ -f "${FAKE_LOGOUT_CALLED:?}" ]]; then
            status="401"
            body='{"error":"REFRESH_TOKEN_REVOKED"}'
        else
            status="200"
            body='{"success":true}'
        fi
        ;;
    *)
        echo "unexpected fake HTTP request: $method $url" >&2
        exit 90
        ;;
esac

if [[ -n "$output_file" ]]; then
    if [[ -n "$binary_file" ]]; then
        cp "$binary_file" "$output_file"
    else
        printf '%s' "$body" >"$output_file"
    fi
else
    if [[ -n "$binary_file" ]]; then
        cat "$binary_file"
    else
        printf '%s' "$body"
    fi
fi
if [[ -n "$write_out" ]]; then
    printf '%s' "${write_out//\%\{http_code\}/$status}"
fi
STUB
chmod +x "$tmp_dir/bin/curl"

# Reject the old WORK_DIR -> STATE_FILE move. A durable handoff must write a
# same-directory temporary and atomically replace the destination itself.
cat >"$tmp_dir/bin/mv" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${FAKE_REJECT_CROSS_STATE_MOVE:-0}" == "1" && "${2:-}" == "${FAKE_STATE_FILE:?}" ]] &&
    [[ "$(dirname "$1")" != "$(dirname "$2")" ]]; then
    echo "state handoff used a cross-directory move" >&2
    exit 83
fi
exec /bin/mv "$@"
STUB
chmod +x "$tmp_dir/bin/mv"

# Fake the Python executable while preserving the verifier's CLI boundary. It
# exercises all passkey HTTP paths and writes the same secret state shape the
# real verifier is required to write.
cat >"$tmp_dir/bin/passkey-python" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == "scripts/passkey-verify/sim.py" ]] || { echo "unexpected verifier program" >&2; exit 89; }
shift
if [[ "${1:-}" == "--validate-origin" ]]; then
    candidate="$2"
    [[ "$candidate" == https://* ]] || exit 82
    authority="${candidate#https://}"
    [[ -n "$authority" && "$authority" != *[/?#@]* && "$authority" != *. ]] || exit 82
    exit 0
fi
if [[ "${1:-}" == "--certificate-fingerprint" ]]; then
    exit 0
fi
if [[ "${1:-}" == "--atomic-replace-json" ]]; then
    shift
    input_file=""
    output_file=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --input-json-file) input_file="$2"; shift 2 ;;
            --output-json-file) output_file="$2"; shift 2 ;;
            *) echo "unexpected atomic writer arg: $1" >&2; exit 88 ;;
        esac
    done
    [[ -n "$input_file" && -n "$output_file" ]] || exit 87
    input_name="$(basename "$input_file")"
    if [[ "${SMOKE_SCENARIO:-ok}" == "refresh_checkpoint_failed_once" &&
        "$input_name" == "cleanup-refreshRevoked.json" &&
        ! -f "${FAKE_REFRESH_CHECKPOINT_ATTEMPT:?}" ]]; then
        : >"$FAKE_REFRESH_CHECKPOINT_ATTEMPT"
        exit 76
    fi
    if [[ "${SMOKE_SCENARIO:-ok}" == "account_checkpoint_failed_once" &&
        "$input_name" == "cleanup-accountDeleted.json" &&
        ! -f "${FAKE_ACCOUNT_CHECKPOINT_ATTEMPT:?}" ]]; then
        : >"$FAKE_ACCOUNT_CHECKPOINT_ATTEMPT"
        exit 75
    fi
    if [[ "${SMOKE_SCENARIO:-ok}" == "refresh_intent_checkpoint_failed_once" &&
        "$input_name" == "cleanup-refreshRevocationStarted.json" &&
        ! -f "${FAKE_REFRESH_INTENT_CHECKPOINT_ATTEMPT:?}" ]]; then
        : >"$FAKE_REFRESH_INTENT_CHECKPOINT_ATTEMPT"
        exit 74
    fi
    if [[ "${SMOKE_SCENARIO:-ok}" == "account_intent_checkpoint_failed_once" &&
        "$input_name" == "cleanup-accountDeletionStarted.json" &&
        ! -f "${FAKE_ACCOUNT_INTENT_CHECKPOINT_ATTEMPT:?}" ]]; then
        : >"$FAKE_ACCOUNT_INTENT_CHECKPOINT_ATTEMPT"
        exit 73
    fi
    cp "$input_file" "$output_file"
    chmod 600 "$output_file"
    exit 0
fi
base=""
origin=""
username=""
credential_file=""
state_file=""
private_token_file=""
signin_only=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --base) base="$2"; shift 2 ;;
        --origin) origin="$2"; shift 2 ;;
        --expected-rp-id) [[ "$2" == "${FAKE_RP_ID:?}" ]] || exit 81; shift 2 ;;
        --username) username="$2"; shift 2 ;;
        --display-name) shift 2 ;;
        --credential-file) credential_file="$2"; shift 2 ;;
        --state-file) state_file="$2"; shift 2 ;;
        --credential-previous-base) shift 2 ;;
        --private-service-token-file) private_token_file="$2"; shift 2 ;;
        --signin-only) signin_only=1; shift ;;
        *) echo "unexpected verifier arg: $1" >&2; exit 88 ;;
    esac
done

[[ "$origin" == "${FAKE_CANONICAL_ORIGIN:?}" ]] || { echo "wrong WebAuthn origin" >&2; exit 87; }
[[ -n "$state_file" && -n "$credential_file" ]] || { echo "missing verifier state paths" >&2; exit 86; }

header_file="$(mktemp "${TMPDIR:-/tmp}/fake-passkey-header.XXXXXX")"
trap 'rm -f "$header_file"' EXIT
chmod 600 "$header_file"
if [[ -n "$private_token_file" ]]; then
    printf 'X-Serverless-Authorization: Bearer %s\n' "$(<"$private_token_file")" >"$header_file"
fi

write_state() {
    local existing='{}'
    if [[ -s "$state_file" ]]; then
        existing="$(cat "$state_file")"
    fi
    jq -S -n \
        --argjson existing "$existing" \
        --arg username "$username" \
        --arg account_id "11111111-1111-4111-8111-111111111111" \
        --arg access_token "${FAKE_ACCESS_TOKEN:?}" \
        --arg refresh_token "${FAKE_REFRESH_TOKEN:?}" \
        --arg credential_file "$credential_file" \
        --arg credential_id "Y3JlZGVudGlhbA" \
        --arg base "$base" \
        --arg origin "${FAKE_CANONICAL_ORIGIN:?}" \
        --arg user_handle "${FAKE_USER_HANDLE:?}" \
        '$existing + {format:"logdate-deploy-smoke-v2", username:$username, accountId:$account_id, accessToken:$access_token, refreshToken:$refresh_token, credentialFile:$credential_file, credentialId:$credential_id, userHandle:$user_handle, preparedBaseUrl:($existing.preparedBaseUrl // $base), canonicalOrigin:$origin, expectedRpId:env.FAKE_RP_ID, lastAuthenticatedBaseUrl:$base, recoveryMode:false, cleanup:{identityVerified:false,mediaDeleted:($existing.cleanup.mediaDeleted // false),refreshRevocationStarted:false,refreshRevoked:false,accountDeletionStarted:($existing.cleanup.accountDeletionStarted // false),accountDeleted:($existing.cleanup.accountDeleted // false)}}' \
        >"$state_file"
    chmod 600 "$state_file"
}

if [[ "$signin_only" == "0" ]]; then
    curl --silent --show-error --output /dev/null --header "@$header_file" --request POST --data '{}' "$base/api/v1/auth/signup/passkey/begin"
    curl --silent --show-error --output /dev/null --header "@$header_file" --request POST --data '{}' "$base/api/v1/auth/signup/passkey/complete"
    jq -n -S \
        --arg base "$base" \
        --arg origin "${FAKE_CANONICAL_ORIGIN:?}" \
        --arg username "$username" \
        --arg account_id "${FAKE_ACCOUNT_ID:?}" \
        --arg user_handle "${FAKE_USER_HANDLE:?}" \
        '{format:"logdate-passkey-verify-v2",baseUrl:$base,origin:$origin,expectedRpId:env.FAKE_RP_ID,username:$username,accountId:$account_id,userHandle:$user_handle,credentialId:"Y3JlZGVudGlhbA",privateKeyPem:"private",signCount:1}' \
        >"$credential_file"
    chmod 600 "$credential_file"
    write_state
fi
[[ -f "$credential_file" ]] || { echo "credential file missing" >&2; exit 85; }
curl --silent --show-error --output /dev/null --header "@$header_file" --request POST --data '{}' "$base/api/v1/auth/signin/passkey/begin"
if [[ "${SMOKE_SCENARIO:-ok}" == "passkey_signin_failed" ]]; then
    exit 84
fi
curl --silent --show-error --output /dev/null --header "@$header_file" --request POST --data '{}' "$base/api/v1/auth/signin/passkey/complete"
write_state
STUB
chmod +x "$tmp_dir/bin/passkey-python"

cat >"$tmp_dir/bin/legacy-passkey-python" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == "scripts/passkey-verify/sim.py" ]] || exit 79
shift
expected_rp_id=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --expected-rp-id) expected_rp_id="$2"; shift 2 ;;
        --base|--origin|--username|--display-name|--credential-file) shift 2 ;;
        --signin-only) shift ;;
        *) exit 78 ;;
    esac
done
[[ "$expected_rp_id" == "${FAKE_RP_ID:?}" ]] || exit 77
printf 'pass\n' >>"${FAKE_LEGACY_PASSKEY_LOG:?}"
STUB
chmod +x "$tmp_dir/bin/legacy-passkey-python"

assert_equals() {
    local expected="$1"
    local actual="$2"
    [[ "$actual" == "$expected" ]] || fail "expected '$expected', got '$actual'"
    pass
}

assert_nonzero() {
    local actual="$1"
    [[ "$actual" != "0" ]] || fail "expected a non-zero exit"
    pass
}

assert_mode_600() {
    local file="$1"
    assert_equals "600" "$(stat -f '%Lp' "$file")"
}

assert_request_before() {
    local earlier="$1"
    local later="$2"
    local log_file="$3"
    local earlier_line later_line
    earlier_line="$(grep -nF "$earlier" "$log_file" | head -1 | cut -d: -f1)"
    later_line="$(grep -nF "$later" "$log_file" | head -1 | cut -d: -f1)"
    [[ -n "$earlier_line" && -n "$later_line" && "$earlier_line" -lt "$later_line" ]] ||
        fail "expected '$earlier' before '$later'"
    pass
}

reset_http_state() {
    : >"$tmp_dir/http/log"
    rm -f "$tmp_dir/http/media" "$tmp_dir/http/media-deleted" \
        "$tmp_dir/http/account-deleted" "$tmp_dir/http/logout-called" \
        "$tmp_dir/http/identity-verified" "$tmp_dir/http/media-delete-attempt" \
        "$tmp_dir/http/account-delete-attempt" "$tmp_dir/http/logout-attempt" \
        "$tmp_dir/http/refresh-checkpoint-attempt" "$tmp_dir/http/account-checkpoint-attempt" \
        "$tmp_dir/http/refresh-intent-checkpoint-attempt" \
        "$tmp_dir/http/account-intent-checkpoint-attempt" \
        "$tmp_dir/http/legacy-passkey-log"
}

run_smoke() {
    local scenario="$1"
    local service_url="$2"
    local phase="$3"
    local state_file="$4"
    local expect_serverless="$5"
    local token_file="$6"
    shift 6
    local expected_asset_origin="$service_url"
    if [[ "$phase" == "verify-and-cleanup" ]]; then
        expected_asset_origin="https://$RP_ID"
    fi

    SMOKE_SCENARIO="$scenario" \
    FAKE_EXPECT_SERVERLESS="$expect_serverless" \
    FAKE_HTTP_LOG="$tmp_dir/http/log" \
    FAKE_MEDIA_STORE="$tmp_dir/http/media" \
    FAKE_MEDIA_DELETED="$tmp_dir/http/media-deleted" \
    FAKE_MEDIA_DELETE_ATTEMPT="$tmp_dir/http/media-delete-attempt" \
    FAKE_ACCOUNT_DELETED="$tmp_dir/http/account-deleted" \
    FAKE_ACCOUNT_DELETE_ATTEMPT="$tmp_dir/http/account-delete-attempt" \
    FAKE_LOGOUT_CALLED="$tmp_dir/http/logout-called" \
    FAKE_LOGOUT_ATTEMPT="$tmp_dir/http/logout-attempt" \
    FAKE_REFRESH_CHECKPOINT_ATTEMPT="$tmp_dir/http/refresh-checkpoint-attempt" \
    FAKE_ACCOUNT_CHECKPOINT_ATTEMPT="$tmp_dir/http/account-checkpoint-attempt" \
    FAKE_REFRESH_INTENT_CHECKPOINT_ATTEMPT="$tmp_dir/http/refresh-intent-checkpoint-attempt" \
    FAKE_ACCOUNT_INTENT_CHECKPOINT_ATTEMPT="$tmp_dir/http/account-intent-checkpoint-attempt" \
    FAKE_IDENTITY_VERIFIED="$tmp_dir/http/identity-verified" \
    FAKE_PRIVATE_TOKEN="$PRIVATE_TOKEN" \
    FAKE_HEALTH_TOKEN="$HEALTH_TOKEN" \
    FAKE_ACCESS_TOKEN="$ACCESS_TOKEN" \
    FAKE_REFRESH_TOKEN="$REFRESH_TOKEN" \
    FAKE_CHALLENGE_SECRET="$CHALLENGE_SECRET" \
    FAKE_ACCOUNT_ID="$ACCOUNT_ID" \
    FAKE_USER_HANDLE="$USER_HANDLE" \
    FAKE_EXPECTED_RELEASE="$EXPECTED_RELEASE" \
    FAKE_CANONICAL_ORIGIN="$CANONICAL_ORIGIN" \
    FAKE_HANDLE_DOMAIN="$HANDLE_DOMAIN" \
    FAKE_RP_ID="$RP_ID" \
    FAKE_EXPECT_ASSET_ORIGIN="$expected_asset_origin" \
    FAKE_PACKAGE_NAME="$PACKAGE_NAME" \
    FAKE_CERT_ONE="$CERT_ONE" \
    FAKE_CERT_TWO="$CERT_TWO" \
    FAKE_STATE_FILE="$state_file" \
    HEALTH_INTERNAL_TOKEN="${RUN_HEALTH_INTERNAL_TOKEN-$HEALTH_TOKEN}" \
    PASSKEY_VERIFY_PYTHON="$tmp_dir/bin/passkey-python" \
    TMPDIR="$tmp_dir/tmp" \
    PATH="$tmp_dir/bin:$PATH" \
    "$SCRIPT" \
        --service-url "$service_url" \
        --contract-file "$contract" \
        --expected-release "$EXPECTED_RELEASE" \
        --invoker-token-file "$token_file" \
        --phase "$phase" \
        --state-file "$state_file" \
        "$@"
}

# Mutation caught: silently accepting an incomplete new invocation.
set +e
missing_flags_output="$("$SCRIPT" --service-url "$CANDIDATE_ORIGIN" 2>&1)"
missing_flags_status=$?
set -e
assert_exit_code 1 "$missing_flags_status"
assert_contains "--contract-file is required" "$missing_flags_output"

assert_missing_required_flag() {
    local expected_message="$1"
    shift
    local output status
    set +e
    output="$("$SCRIPT" "$@" 2>&1)"
    status=$?
    set -e
    assert_exit_code 1 "$status"
    assert_contains "$expected_message" "$output"
}

assert_missing_required_flag "--service-url is required" \
    --contract-file "$contract" --expected-release "$EXPECTED_RELEASE" \
    --invoker-token-file "$invoker_token_file" --phase health-only --state-file "$tmp_dir/state-not-yet-created"
assert_missing_required_flag "--expected-release is required" \
    --service-url "$CANDIDATE_ORIGIN" --contract-file "$contract" \
    --invoker-token-file "$invoker_token_file" --phase health-only --state-file "$tmp_dir/state-not-yet-created"
assert_missing_required_flag "--invoker-token-file is required" \
    --service-url "$CANDIDATE_ORIGIN" --contract-file "$contract" --expected-release "$EXPECTED_RELEASE" \
    --phase health-only --state-file "$tmp_dir/state-not-yet-created"
assert_missing_required_flag "--phase is required" \
    --service-url "$CANDIDATE_ORIGIN" --contract-file "$contract" --expected-release "$EXPECTED_RELEASE" \
    --invoker-token-file "$invoker_token_file" --state-file "$tmp_dir/state-not-yet-created"
assert_missing_required_flag "--state-file is required" \
    --service-url "$CANDIDATE_ORIGIN" --contract-file "$contract" --expected-release "$EXPECTED_RELEASE" \
    --invoker-token-file "$invoker_token_file" --phase health-only

# Mutation caught: an RFC-incompatible origin must fail before the first HTTP request.
invalid_origin_state="$tmp_dir/invalid-origin-state.json"
: >"$invalid_origin_state"
chmod 600 "$invalid_origin_state"
set +e
invalid_origin_output="$(run_smoke ok "$CANDIDATE_ORIGIN?query=must-not-pass" health-only "$invalid_origin_state" 1 "$invoker_token_file" 2>&1)"
invalid_origin_status=$?
set -e
assert_exit_code 1 "$invalid_origin_status"
assert_contains "origin-only HTTPS URL" "$invalid_origin_output"

# Mutation caught: contract fingerprints must be Android-valid values, not merely
# non-empty strings that happen to agree with the response fixture.
invalid_fingerprint_contract="$tmp_dir/invalid-fingerprint-contract.json"
jq '
    .env_vars.ANDROID_CERT_FINGERPRINTS = "not-a-fingerprint," + .android_signing.certificates.play_app_signing.fingerprint |
    .android_signing.certificates.upload.fingerprint = "not-a-fingerprint" |
    .android_signing.certificates.upload.apk_key_hash_origin = "android:apk-key-hash:not+base64url"
' "$contract" | jq -S . >"$invalid_fingerprint_contract"
chmod 600 "$invalid_fingerprint_contract"
original_contract="$contract"
contract="$invalid_fingerprint_contract"
set +e
invalid_fingerprint_output="$(run_smoke ok "$CANDIDATE_ORIGIN" health-only "$invalid_origin_state" 1 "$invoker_token_file" 2>&1)"
invalid_fingerprint_status=$?
set -e
contract="$original_contract"
assert_exit_code 1 "$invalid_fingerprint_status"
assert_contains "contract Android certificate fingerprint is malformed" "$invalid_fingerprint_output"

# Mutation caught: removing the warning or breaking the official positional caller before Task 6.
set +e
legacy_output="$(
    FAKE_EXPECT_SERVERLESS=0 \
    FAKE_HTTP_LOG="$tmp_dir/http/log" \
    FAKE_MEDIA_STORE="$tmp_dir/http/media" \
    FAKE_MEDIA_DELETED="$tmp_dir/http/media-deleted" \
    FAKE_ACCOUNT_DELETED="$tmp_dir/http/account-deleted" \
    FAKE_LOGOUT_CALLED="$tmp_dir/http/logout-called" \
    FAKE_PRIVATE_TOKEN="$PRIVATE_TOKEN" \
    FAKE_HEALTH_TOKEN="$HEALTH_TOKEN" \
    FAKE_ACCESS_TOKEN="$ACCESS_TOKEN" \
    FAKE_REFRESH_TOKEN="$REFRESH_TOKEN" \
    FAKE_CHALLENGE_SECRET="$CHALLENGE_SECRET" \
    FAKE_EXPECTED_RELEASE="$EXPECTED_RELEASE" \
    FAKE_CANONICAL_ORIGIN="$CANONICAL_ORIGIN" \
    FAKE_PACKAGE_NAME="$PACKAGE_NAME" \
    FAKE_CERT_ONE="$CERT_ONE" \
    FAKE_CERT_TWO="$CERT_TWO" \
    FAKE_RP_ID="$RP_ID" \
    FAKE_HANDLE_DOMAIN="$HANDLE_DOMAIN" \
    FAKE_LEGACY_PASSKEY_LOG="$tmp_dir/http/legacy-passkey-log" \
    PASSKEY_VERIFY_PYTHON="$tmp_dir/bin/legacy-passkey-python" \
    SMOKE_SCENARIO=ok PATH="$tmp_dir/bin:$PATH" \
    "$SCRIPT" "$CANDIDATE_ORIGIN" "$CANONICAL_ORIGIN" 2>&1
)"
legacy_status=$?
set -e
assert_exit_code 0 "$legacy_status"
assert_contains "legacy positional smoke-test invocation is deprecated" "$legacy_output"
assert_contains "Smoke test passed" "$legacy_output"
assert_contains "full passkey signup/signin" "$legacy_output"
assert_equals "2" "$(wc -l <"$tmp_dir/http/legacy-passkey-log" | tr -d ' ')"

# Mutation caught: accepting token/state files whose group or other bits can expose secrets.
overpermitted_token="$tmp_dir/overpermitted-token"
printf '%s\n' "$PRIVATE_TOKEN" >"$overpermitted_token"
chmod 644 "$overpermitted_token"
state_file="$tmp_dir/state.json"
: >"$state_file"
chmod 600 "$state_file"
set +e
permission_output="$(run_smoke ok "$CANDIDATE_ORIGIN" health-only "$state_file" 1 "$overpermitted_token" 2>&1)"
permission_status=$?
set -e
assert_exit_code 1 "$permission_status"
assert_contains "invoker token file must have mode 600" "$permission_output"
assert_not_contains "$PRIVATE_TOKEN" "$permission_output"

empty_token="$tmp_dir/empty-token"
: >"$empty_token"
chmod 600 "$empty_token"
set +e
empty_output="$(run_smoke ok "$CANDIDATE_ORIGIN" health-only "$state_file" 1 "$empty_token" 2>&1)"
empty_status=$?
set -e
assert_exit_code 1 "$empty_status"
assert_contains "invoker token file is empty" "$empty_output"

missing_token_file="$tmp_dir/missing-invoker-token"
set +e
missing_token_output="$(run_smoke ok "$CANDIDATE_ORIGIN" health-only "$state_file" 1 "$missing_token_file" 2>&1)"
missing_token_status=$?
set -e
assert_exit_code 1 "$missing_token_status"
assert_contains "invoker token file must be a regular file" "$missing_token_output"

chmod 644 "$state_file"
set +e
state_permission_output="$(run_smoke ok "$CANDIDATE_ORIGIN" health-only "$state_file" 1 "$invoker_token_file" 2>&1)"
state_permission_status=$?
set -e
assert_exit_code 1 "$state_permission_status"
assert_contains "state file must have mode 600" "$state_permission_output"
chmod 600 "$state_file"

# Mutation caught: defaulting the app package instead of reading immutable contract identity.
missing_package_contract="$tmp_dir/missing-package.json"
jq 'del(.android_package_name)' "$contract" | jq -S . >"$missing_package_contract"
chmod 600 "$missing_package_contract"
set +e
missing_package_output="$(
    SMOKE_SCENARIO=ok HEALTH_INTERNAL_TOKEN="$HEALTH_TOKEN" \
    PASSKEY_VERIFY_PYTHON="scripts/passkey-verify/.venv/bin/python" \
    "$SCRIPT" --service-url "$CANDIDATE_ORIGIN" --contract-file "$missing_package_contract" \
        --expected-release "$EXPECTED_RELEASE" --invoker-token-file "$invoker_token_file" \
        --phase health-only --state-file "$state_file" 2>&1
)"
missing_package_status=$?
set -e
assert_exit_code 1 "$missing_package_status"
assert_contains "contract android_package_name is required" "$missing_package_output"

# Mutation caught: checking only one cert/origin rather than the full role-derived sets.
bad_contract="$tmp_dir/bad-contract.json"
jq '.env_vars.WEBAUTHN_ALLOWED_ORIGINS += ",android:apk-key-hash:unapproved"' "$contract" | jq -S . >"$bad_contract"
chmod 600 "$bad_contract"
set +e
bad_contract_output="$(
    SMOKE_SCENARIO=ok HEALTH_INTERNAL_TOKEN="$HEALTH_TOKEN" \
    PASSKEY_VERIFY_PYTHON="scripts/passkey-verify/.venv/bin/python" \
    "$SCRIPT" --service-url "$CANDIDATE_ORIGIN" --contract-file "$bad_contract" \
        --expected-release "$EXPECTED_RELEASE" --invoker-token-file "$invoker_token_file" \
        --phase health-only --state-file "$state_file" 2>&1
)"
bad_contract_status=$?
set -e
assert_exit_code 1 "$bad_contract_status"
assert_contains "contract Android origin set does not match signing certificates" "$bad_contract_output"

# Mutation caught by each scenario: wrong release, missing durable DB, wrong first-party
# descriptor identity/capabilities, incomplete DAL relations/certificates, or auth bypass.
declare -a negative_cases=(
    "public_wrong_release|public health release does not match"
    "internal_db_absent|internal health did not report db_connected=true"
    "internal_db_false|internal health did not report db_connected=true"
    "descriptor_kind|descriptor deploymentKind is not FIRST_PARTY"
    "descriptor_server_origin|descriptor serverOrigin does not match"
    "descriptor_api_base|descriptor apiBaseUrl does not match"
    "descriptor_handle|descriptor handleDomain does not match"
    "descriptor_rp|descriptor RP ID does not match"
    "descriptor_capability|descriptor is missing first-party capabilities"
    "assetlinks_package|asset links package does not match"
    "assetlinks_relation|asset links relations do not match"
    "assetlinks_missing_cert|asset links certificate set does not match"
    "assetlinks_extra_cert|asset links certificate set does not match"
    "protected_not_401|unauthenticated entitlement route did not return 401"
)
for case_spec in "${negative_cases[@]}"; do
    scenario="${case_spec%%|*}"
    expected_error="${case_spec#*|}"
    reset_http_state
    : >"$state_file"
    chmod 600 "$state_file"
    set +e
    case_output="$(run_smoke "$scenario" "$CANDIDATE_ORIGIN" health-only "$state_file" 1 "$invoker_token_file" 2>&1)"
    case_status=$?
    set -e
    assert_exit_code 1 "$case_status"
    assert_contains "$expected_error" "$case_output"
    assert_not_contains "$PRIVATE_TOKEN" "$case_output"
    assert_not_contains "$HEALTH_TOKEN" "$case_output"
done

# Prepare proves candidate identity, creates one disposable identity, verifies exact
# finite quota, uploads random bytes, and retains only a 0600 state file.
reset_http_state
rm -f "$state_file" "${state_file}.credential"
: >"$state_file"
chmod 600 "$state_file"
set +e
prepare_output="$(run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
prepare_status=$?
set -e
assert_exit_code 0 "$prepare_status"
assert_contains "Durability prepare passed" "$prepare_output"
assert_mode_600 "$state_file"
assert_equals "logdate-deploy-smoke-v2" "$(jq -r '.format' "$state_file")"
assert_equals "deploy-smoke-media" "$(jq -r '.mediaId' "$state_file")"
assert_equals "64" "$(jq -r '.mediaSha256 | length' "$state_file")"
assert_equals "true" "$(jq -r '.mediaSizeBytes > 0' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.identityVerified' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.mediaDeleted' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.refreshRevocationStarted' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.refreshRevoked' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.accountDeletionStarted' "$state_file")"
assert_equals "false" "$(jq -r '.cleanup.accountDeleted' "$state_file")"
assert_file_exists "$tmp_dir/http/media"
assert_not_contains "$PRIVATE_TOKEN" "$prepare_output"
assert_not_contains "$HEALTH_TOKEN" "$prepare_output"
assert_not_contains "$ACCESS_TOKEN" "$prepare_output"
assert_not_contains "$REFRESH_TOKEN" "$prepare_output"
assert_not_contains "$CHALLENGE_SECRET" "$prepare_output"
assert_equals "0" "$(grep -c 'serverless=0' "$tmp_dir/http/log" || true)"
assert_contains "GET /api/v1/auth/me/entitlement serverless=1 health=0 authorization=0" "$(cat "$tmp_dir/http/log")"
assert_contains "GET /api/v1/quota serverless=1 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"
assert_contains "POST /api/v1/media serverless=1 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"
assert_contains "POST /api/v1/auth/signup/passkey/begin serverless=1" "$(cat "$tmp_dir/http/log")"

# Mutation caught: persisting prepared media state must not rely on a move from
# WORK_DIR, which can cross filesystems and degrade to copy/delete.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
rm -f "${state_file}.credential"
set +e
same_parent_state_output="$(FAKE_REJECT_CROSS_STATE_MOVE=1 run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
same_parent_state_status=$?
set -e
assert_exit_code 0 "$same_parent_state_status"
assert_contains "Durability prepare passed" "$same_parent_state_output"

# Verify uses the canonical public URL without reading the candidate token, signs in
# with the same credential, checks durable bytes/usage, deletes media/account, revokes
# refresh tokens, and erases state.
missing_invoker_file="$tmp_dir/intentionally-missing-token"
: >"$tmp_dir/http/log"
set +e
verify_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
verify_status=$?
set -e
assert_exit_code 0 "$verify_status"
assert_contains "Durability verification and cleanup passed" "$verify_output"
assert_file_missing "$state_file"
assert_file_exists "$tmp_dir/http/media-deleted"
assert_file_exists "$tmp_dir/http/account-deleted"
assert_file_exists "$tmp_dir/http/logout-called"
assert_equals "0" "$(grep -c 'serverless=1' "$tmp_dir/http/log" || true)"
assert_contains "GET /api/v1/media/deploy-smoke-media/binary serverless=0 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"
assert_contains "GET /api/v1/auth/me serverless=0 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"
assert_contains "DELETE /api/v1/auth/me serverless=0 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"
assert_contains "POST /api/v1/auth/token/refresh serverless=0 health=0 authorization=0" "$(cat "$tmp_dir/http/log")"
assert_request_before "GET /api/v1/auth/me " "POST /api/v1/auth/logout " "$tmp_dir/http/log"
assert_request_before "POST /api/v1/auth/logout " "POST /api/v1/auth/token/refresh " "$tmp_dir/http/log"
assert_request_before "POST /api/v1/auth/token/refresh " "DELETE /api/v1/auth/me " "$tmp_dir/http/log"
assert_not_contains "$ACCESS_TOKEN" "$verify_output"
assert_not_contains "$REFRESH_TOKEN" "$verify_output"

# A local prerequisite failure before canonical reauthentication cannot authorize
# destructive cleanup; state is retained for a later turnkey retry.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
set +e
preflight_cleanup_output="$(RUN_HEALTH_INTERNAL_TOKEN='' run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
preflight_cleanup_status=$?
set -e
assert_exit_code 1 "$preflight_cleanup_status"
assert_contains "HEALTH_INTERNAL_TOKEN must be provided" "$preflight_cleanup_output"
assert_file_missing "$tmp_dir/http/media-deleted"
assert_file_missing "$tmp_dir/http/account-deleted"
assert_file_exists "$state_file"
assert_file_exists "${state_file}.credential"
preflight_retry_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
assert_contains "Durability verification and cleanup passed" "$preflight_retry_output"
assert_file_missing "$state_file"

# A candidate-side failure after signup must clean the disposable account through
# that private candidate and remove local state.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
set +e
quota_failure_output="$(run_smoke quota_unlimited "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
quota_failure_status=$?
set -e
assert_exit_code 1 "$quota_failure_status"
assert_contains "authenticated entitlement is not the seeded free tier" "$quota_failure_output"
assert_file_exists "$tmp_dir/http/account-deleted"
assert_file_exists "$tmp_dir/http/logout-called"
assert_file_missing "$state_file"
assert_contains "DELETE /api/v1/auth/me serverless=1 health=0 authorization=1" "$(cat "$tmp_dir/http/log")"

# Signup writes provisional recovery material, but a verifier failure before
# canonical reauthentication must not spend its unproven bearer token.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
set +e
partial_signin_output="$(run_smoke passkey_signin_failed "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
partial_signin_status=$?
set -e
assert_nonzero "$partial_signin_status"
assert_file_missing "$tmp_dir/http/account-deleted"
assert_file_missing "$tmp_dir/http/logout-called"
assert_file_exists "$state_file"
assert_file_exists "${state_file}.credential"
assert_not_contains "$ACCESS_TOKEN" "$partial_signin_output"
assert_not_contains "$REFRESH_TOKEN" "$partial_signin_output"
jq -S 'del(.accessToken, .refreshToken) | .recoveryMode = true' \
    "$state_file" >"$tmp_dir/recovery-state.json"
chmod 600 "$tmp_dir/recovery-state.json"
mv "$tmp_dir/recovery-state.json" "$state_file"
partial_retry_output="$(run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
assert_contains "Recovered and cleaned disposable account" "$partial_retry_output"
assert_file_missing "$state_file"

# A post-promotion hash mismatch must fail closed and still clean through the
# canonical domain before rollback.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
: >"$tmp_dir/http/log"
set +e
hash_failure_output="$(run_smoke media_hash_changed "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
hash_failure_status=$?
set -e
assert_exit_code 1 "$hash_failure_status"
assert_contains "downloaded media SHA-256 does not match prepared media" "$hash_failure_output"
assert_file_exists "$tmp_dir/http/media-deleted"
assert_file_exists "$tmp_dir/http/account-deleted"
assert_file_missing "$state_file"
assert_equals "0" "$(grep -c 'serverless=1' "$tmp_dir/http/log" || true)"

# A non-unlimited value is still wrong unless it is the exact migration-seeded
# free tier shared by entitlement and quota responses.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
set +e
wrong_free_output="$(run_smoke quota_wrong_free_tier "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" 2>&1)"
wrong_free_status=$?
set -e
assert_exit_code 1 "$wrong_free_status"
assert_contains "authenticated entitlement is not the seeded free tier" "$wrong_free_output"
assert_file_exists "$tmp_dir/http/account-deleted"
assert_file_missing "$state_file"

# Prepared proof cannot be replayed across a different release or rendered
# runtime contract.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
jq -S '.expectedRelease = "logdate-server@stale"' "$state_file" >"$tmp_dir/stale-state.json"
chmod 600 "$tmp_dir/stale-state.json"
mv "$tmp_dir/stale-state.json" "$state_file"
set +e
stale_state_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
stale_state_status=$?
set -e
assert_exit_code 1 "$stale_state_status"
assert_contains "prepared state does not match the current contract and release" "$stale_state_output"
assert_file_missing "$tmp_dir/http/account-deleted"
assert_file_exists "$state_file"
assert_file_exists "${state_file}.credential"
rm -f "$state_file" "${state_file}.credential"

# A replacement candidate origin in otherwise valid prepared state must fail
# before any bearer or refresh token is sent, and retain recovery material.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
jq -S '.preparedBaseUrl = "https://replacement.example.test"' "$state_file" >"$tmp_dir/replacement-state.json"
chmod 600 "$tmp_dir/replacement-state.json"
mv "$tmp_dir/replacement-state.json" "$state_file"
: >"$tmp_dir/http/log"
set +e
replacement_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
replacement_status=$?
set -e
assert_exit_code 1 "$replacement_status"
assert_contains "prepared candidate origin does not match credential state" "$replacement_output"
assert_file_exists "$state_file"
assert_file_exists "${state_file}.credential"
assert_equals "0" "$(wc -l <"$tmp_dir/http/log" | tr -d ' ')"
rm -f "$state_file" "${state_file}.credential"

# A canonical identity-probe failure happens before reauthentication and must
# retain recovery material without issuing destructive requests.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
: >"$tmp_dir/http/log"
set +e
descriptor_failure_output="$(run_smoke descriptor_kind "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
descriptor_failure_status=$?
set -e
assert_exit_code 1 "$descriptor_failure_status"
assert_contains "descriptor deploymentKind is not FIRST_PARTY" "$descriptor_failure_output"
assert_file_missing "$tmp_dir/http/media-deleted"
assert_file_missing "$tmp_dir/http/account-deleted"
assert_file_exists "$state_file"
assert_file_exists "${state_file}.credential"
descriptor_retry_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
assert_contains "Durability verification and cleanup passed" "$descriptor_retry_output"
assert_file_missing "$state_file"

# A stale token retained in state cannot authorize deletion. Canonical passkey
# reauthentication and exact /auth/me identity binding are mandatory first.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
jq -S '.accessToken = "stale-access-token"' "$state_file" >"$tmp_dir/stale-token-state.json"
chmod 600 "$tmp_dir/stale-token-state.json"
mv "$tmp_dir/stale-token-state.json" "$state_file"
: >"$tmp_dir/http/log"
set +e
stale_token_output="$(run_smoke passkey_signin_failed "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
stale_token_status=$?
set -e
assert_nonzero "$stale_token_status"
assert_not_contains "$ACCESS_TOKEN" "$stale_token_output"
assert_not_contains "$REFRESH_TOKEN" "$stale_token_output"
assert_file_missing "$tmp_dir/http/account-deleted"
assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"
assert_file_exists "$state_file"
stale_token_retry_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
assert_contains "Durability verification and cleanup passed" "$stale_token_retry_output"
assert_file_missing "$state_file"

# Even a freshly issued token is insufficient when /auth/me resolves to a
# different account or username than the credential and retained state.
reset_http_state
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
: >"$tmp_dir/http/log"
set +e
swapped_identity_output="$(run_smoke identity_swapped "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
swapped_identity_status=$?
set -e
assert_exit_code 1 "$swapped_identity_status"
assert_contains "reauthenticated identity does not match prepared state" "$swapped_identity_output"
assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"
assert_file_exists "$state_file"
swapped_retry_output="$(run_smoke ok "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
assert_contains "Durability verification and cleanup passed" "$swapped_retry_output"
assert_file_missing "$state_file"

# Each cleanup phase is persisted. A transient media, logout, or account failure
# retains recovery material, and a second invocation resumes to eventual cleanup.
for retry_spec in \
    "media_delete_failed_once|false|false|false" \
    "logout_failed_once|true|false|false" \
    "account_cleanup_failed_once|true|true|false"; do
    scenario="${retry_spec%%|*}"
    phase_values="${retry_spec#*|}"
    media_phase="${phase_values%%|*}"
    phase_values="${phase_values#*|}"
    refresh_phase="${phase_values%%|*}"
    account_phase="${phase_values##*|}"
    reset_http_state
    : >"$state_file"
    chmod 600 "$state_file"
    run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
    : >"$tmp_dir/http/log"
    set +e
    retry_first_output="$(run_smoke "$scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    retry_first_status=$?
    set -e
    assert_exit_code 1 "$retry_first_status"
    assert_not_contains "$ACCESS_TOKEN" "$retry_first_output"
    assert_not_contains "$REFRESH_TOKEN" "$retry_first_output"
    assert_file_exists "$state_file"
    assert_equals "$media_phase" "$(jq -r '.cleanup.mediaDeleted' "$state_file")"
    assert_equals "$refresh_phase" "$(jq -r '.cleanup.refreshRevoked' "$state_file")"
    assert_equals "$account_phase" "$(jq -r '.cleanup.accountDeleted' "$state_file")"
    if [[ "$scenario" == "media_delete_failed_once" ]]; then
        assert_not_contains "POST /api/v1/auth/logout" "$(cat "$tmp_dir/http/log")"
        assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"
    elif [[ "$scenario" == "logout_failed_once" ]]; then
        assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"
    fi
    set +e
    retry_second_output="$(run_smoke "$scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    retry_second_status=$?
    set -e
    assert_exit_code 0 "$retry_second_status"
    assert_contains "Durability verification and cleanup passed" "$retry_second_output"
    assert_file_missing "$state_file"
    assert_file_exists "$tmp_dir/http/media-deleted"
    assert_file_exists "$tmp_dir/http/logout-called"
    assert_file_exists "$tmp_dir/http/account-deleted"
done

# EXIT-trap cleanup runs under `set +e`, so a failed durable intent checkpoint
# must still stop before its remote side effect. A retry can then persist the
# intent and safely continue cleanup.
for intent_scenario in refresh_intent_checkpoint_failed_once account_intent_checkpoint_failed_once; do
    reset_http_state
    rm -f "$state_file" "${state_file}.credential"
    : >"$state_file"
    chmod 600 "$state_file"
    run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
    : >"$tmp_dir/http/log"
    set +e
    intent_first_output="$(run_smoke "$intent_scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    intent_first_status=$?
    set -e
    assert_exit_code 1 "$intent_first_status"
    assert_file_exists "$state_file"
    assert_not_contains "$ACCESS_TOKEN" "$intent_first_output"
    assert_not_contains "$REFRESH_TOKEN" "$intent_first_output"
    if [[ "$intent_scenario" == "refresh_intent_checkpoint_failed_once" ]]; then
        assert_equals "false" "$(jq -r '.cleanup.refreshRevocationStarted' "$state_file")"
        assert_file_missing "$tmp_dir/http/logout-called"
        assert_file_missing "$tmp_dir/http/account-deleted"
    else
        assert_equals "true" "$(jq -r '.cleanup.refreshRevoked' "$state_file")"
        assert_equals "false" "$(jq -r '.cleanup.accountDeletionStarted' "$state_file")"
        assert_file_exists "$tmp_dir/http/logout-called"
        assert_file_missing "$tmp_dir/http/account-deleted"
    fi
    intent_retry_output="$(run_smoke "$intent_scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    assert_contains "Durability verification and cleanup passed" "$intent_retry_output"
    assert_file_missing "$state_file"
done

# A remote side effect can succeed immediately before its local checkpoint
# write fails. Persisted intent makes the next invocation accept idempotent
# already-revoked/already-deleted responses and finish cleanup.
for checkpoint_scenario in refresh_checkpoint_failed_once account_checkpoint_failed_once; do
    reset_http_state
    rm -f "$state_file" "${state_file}.credential"
    : >"$state_file"
    chmod 600 "$state_file"
    run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
    : >"$tmp_dir/http/log"
    set +e
    checkpoint_first_output="$(run_smoke "$checkpoint_scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    checkpoint_first_status=$?
    set -e
    assert_nonzero "$checkpoint_first_status"
    assert_file_exists "$state_file"
    assert_not_contains "$ACCESS_TOKEN" "$checkpoint_first_output"
    assert_not_contains "$REFRESH_TOKEN" "$checkpoint_first_output"
    if [[ "$checkpoint_scenario" == "refresh_checkpoint_failed_once" ]]; then
        assert_equals "true" "$(jq -r '.cleanup.refreshRevocationStarted' "$state_file")"
        assert_equals "false" "$(jq -r '.cleanup.refreshRevoked' "$state_file")"
        assert_file_exists "$tmp_dir/http/logout-called"
        assert_file_missing "$tmp_dir/http/account-deleted"
    else
        assert_equals "true" "$(jq -r '.cleanup.accountDeletionStarted' "$state_file")"
        assert_equals "false" "$(jq -r '.cleanup.accountDeleted' "$state_file")"
        assert_file_exists "$tmp_dir/http/account-deleted"
    fi
    checkpoint_retry_output="$(run_smoke "$checkpoint_scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    assert_contains "Durability verification and cleanup passed" "$checkpoint_retry_output"
    assert_file_missing "$state_file"
done

# Deletion and cleanup are gates, not best-effort success messages. Error bodies
# containing credential-shaped data must never reach output.
for failure_spec in \
    "media_delete_failed|media deletion did not return 204" \
    "media_readable_after_delete|media remained readable after deletion" \
    "account_cleanup_failed|account deletion did not return 204"; do
    scenario="${failure_spec%%|*}"
    expected_error="${failure_spec#*|}"
    reset_http_state
    rm -f "$state_file" "${state_file}.credential"
    : >"$state_file"
    chmod 600 "$state_file"
    run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
    set +e
    cleanup_failure_output="$(run_smoke "$scenario" "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
    cleanup_failure_status=$?
    set -e
    assert_exit_code 1 "$cleanup_failure_status"
    assert_contains "$expected_error" "$cleanup_failure_output"
    assert_not_contains "$ACCESS_TOKEN" "$cleanup_failure_output"
    assert_not_contains "$REFRESH_TOKEN" "$cleanup_failure_output"
    assert_not_contains "$CHALLENGE_SECRET" "$cleanup_failure_output"
    if [[ "$scenario" == "media_delete_failed" || "$scenario" == "account_cleanup_failed" ]]; then
        assert_file_exists "$state_file"
        assert_file_exists "${state_file}.credential"
    fi
done

# Resume also handles the legacy partial order where an already-proven account
# was deleted before logout failed. It revokes the token without attempting a
# second destructive account request, then removes the retained state.
reset_http_state
rm -f "$state_file" "${state_file}.credential"
: >"$state_file"
chmod 600 "$state_file"
run_smoke ok "$CANDIDATE_ORIGIN" prepare "$state_file" 1 "$invoker_token_file" >/dev/null
jq -S --arg origin "$CANONICAL_ORIGIN" '
    .cleanup = {
        identityVerified:true,
        identityVerifiedOrigin:$origin,
        mediaDeleted:true,
        refreshRevocationStarted:true,
        refreshRevoked:false,
        accountDeletionStarted:true,
        accountDeleted:true
    }
' "$state_file" >"$tmp_dir/account-deleted-state.json"
chmod 600 "$tmp_dir/account-deleted-state.json"
mv "$tmp_dir/account-deleted-state.json" "$state_file"
: >"$tmp_dir/http/media-deleted"
: >"$tmp_dir/http/account-deleted"
: >"$tmp_dir/http/identity-verified"
: >"$tmp_dir/http/log"
set +e
legacy_logout_output="$(run_smoke logout_failed_once "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
legacy_logout_status=$?
set -e
assert_exit_code 1 "$legacy_logout_status"
assert_contains "token revocation did not return 200" "$legacy_logout_output"
assert_file_exists "$state_file"
assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"
legacy_logout_retry_output="$(run_smoke logout_failed_once "$CANONICAL_ORIGIN" verify-and-cleanup "$state_file" 0 "$missing_invoker_file" 2>&1)"
assert_contains "Durability verification and cleanup passed" "$legacy_logout_retry_output"
assert_file_missing "$state_file"
assert_not_contains "DELETE /api/v1/auth/me" "$(cat "$tmp_dir/http/log")"

# Every invocation removes its private working directory even on failure.
assert_equals "0" "$(find "$tmp_dir/tmp" -maxdepth 1 -type d -name 'logdate-revision-smoke.*' | wc -l | tr -d ' ')"

print_pass_summary "smoke-test-revision"
