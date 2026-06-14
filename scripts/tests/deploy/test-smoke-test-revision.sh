#!/usr/bin/env bash
# Regression tests for the Cloud Run revision smoke test.

set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/smoke-test-revision.sh"

script_contents="$(cat "$SCRIPT")"
assert_contains "health_status_is_healthy" "$script_contents"
assert_contains "jq -e '.status == \"healthy\"'" "$script_contents"
assert_contains '\"status\"[[:space:]]*:[[:space:]]*\"healthy\"' "$script_contents"
assert_contains "probe_passkey_end_to_end" "$script_contents"
assert_contains "scripts/passkey-verify/sim.py" "$script_contents"
assert_contains "--origin" "$script_contents"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

mkdir -p "$tmp_dir/bin"
cat > "$tmp_dir/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

url="${*: -1}"
case "$url" in
    */health)
        printf '{\n  "status": "healthy",\n  "version": "test"\n}HTTP_STATUS:200'
        ;;
    */api/v1/auth/signup/passkey/begin)
        printf '{"sessionToken":"test-session"}HTTP_STATUS:200'
        ;;
    */api/v1/auth/me/entitlement)
        printf '401'
        ;;
    *)
        echo "unexpected curl url: $url" >&2
        exit 99
        ;;
esac
STUB
chmod +x "$tmp_dir/bin/curl"

set +e
success_output="$(PATH="$tmp_dir/bin:$PATH" ./"$SCRIPT" "https://candidate.example.test" 2>&1)"
success_status=$?
set -e
assert_exit_code 0 "$success_status"
assert_contains "[OK] /health" "$success_output"
assert_contains "[SKIP] full passkey verification" "$success_output"
assert_contains "Smoke test passed" "$success_output"

cat > "$tmp_dir/bin/python" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

log="${PASSKEY_STUB_LOG:?}"
printf '%s\n' "$*" >> "$log"
case "$*" in
    *"--base https://candidate.example.test"*"--origin https://cloud.example.test"*"--credential-file "*)
        exit 0
        ;;
    *)
        echo "unexpected passkey verifier args: $*" >&2
        exit 98
        ;;
esac
STUB
chmod +x "$tmp_dir/bin/python"
passkey_log="$tmp_dir/passkey.log"

set +e
full_output="$(
    PASSKEY_VERIFY_PYTHON="$tmp_dir/bin/python" \
    PASSKEY_STUB_LOG="$passkey_log" \
    PATH="$tmp_dir/bin:$PATH" \
    ./"$SCRIPT" "https://candidate.example.test" "https://cloud.example.test" 2>&1
)"
full_status=$?
set -e
assert_exit_code 0 "$full_status"
assert_contains "[OK] full passkey signup/signin" "$full_output"
assert_contains "--signin-only" "$(cat "$passkey_log")"

cat > "$tmp_dir/bin/python" <<'STUB'
#!/usr/bin/env bash
exit 42
STUB
chmod +x "$tmp_dir/bin/python"

set +e
passkey_failure_output="$(
    PASSKEY_VERIFY_PYTHON="$tmp_dir/bin/python" \
    PATH="$tmp_dir/bin:$PATH" \
    ./"$SCRIPT" "https://candidate.example.test" "https://cloud.example.test" 2>&1
)"
passkey_failure_status=$?
set -e
assert_exit_code 1 "$passkey_failure_status"
assert_contains "Smoke test FAILED" "$passkey_failure_output"

cat > "$tmp_dir/bin/curl" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail

url="${*: -1}"
case "$url" in
    */health)
        printf '{\n  "status": "degraded"\n}HTTP_STATUS:200'
        ;;
    */api/v1/auth/signup/passkey/begin)
        printf '{"sessionToken":"test-session"}HTTP_STATUS:200'
        ;;
    */api/v1/auth/me/entitlement)
        printf '401'
        ;;
    *)
        echo "unexpected curl url: $url" >&2
        exit 99
        ;;
esac
STUB
chmod +x "$tmp_dir/bin/curl"

set +e
failure_output="$(PATH="$tmp_dir/bin:$PATH" ./"$SCRIPT" "https://candidate.example.test" 2>&1)"
failure_status=$?
set -e
assert_exit_code 1 "$failure_status"
assert_contains "status=healthy" "$failure_output"
assert_contains "Smoke test FAILED" "$failure_output"

print_pass_summary "smoke-test-revision"
