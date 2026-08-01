#!/usr/bin/env bash
# Real-behavior tests for backend-free Terraform validation isolation.

set -euo pipefail

# shellcheck source=scripts/tests/lib/assertions.sh
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/validate-terraform-isolated.sh"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
LOG_DIR="$TMP_DIR/logs"
CHECKOUT_MARKER="infra/terraform/.terraform/task-1-hermetic-backend-marker"

cleanup() {
    rm -f "$CHECKOUT_MARKER"
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN" "$LOG_DIR" "$(dirname "$CHECKOUT_MARKER")"
printf 'production-backend-metadata\n' >"$CHECKOUT_MARKER"

cat >"$FAKE_BIN/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
TF_DATA_DIR="${TF_DATA_DIR:?}"
[[ -d "$TF_DATA_DIR" ]] || exit 91
[[ "$TF_DATA_DIR" != "${CHECKOUT_TERRAFORM_DIR:?}" ]] || exit 92
[[ ! -e "$TF_DATA_DIR/task-1-hermetic-backend-marker" ]] || exit 93
[[ -z "${TF_WORKSPACE:-}" ]] || exit 94
[[ -z "${TF_LOG:-}" && -z "${TF_LOG_PATH:-}" ]] || exit 94
[[ "${TF_INPUT:-}" == "0" ]] || exit 94
while IFS='=' read -r name _; do
    [[ "$name" != TF_CLI_ARGS* ]] || exit 95
    [[ "$name" != TF_VAR_* ]] || exit 96
done < <(env)

mode="$(stat -f '%Lp' "$TF_DATA_DIR" 2>/dev/null || stat -c '%a' "$TF_DATA_DIR")"
[[ "$mode" == "700" ]] || exit 98
printf '%s\n' "$TF_DATA_DIR" >>"$LOG_DIR/data-dirs.log"
{
    for arg in "$@"; do
        printf '[%s]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/terraform.log"

case "${2:-}" in
    init)
        [[ "$#" == "4" ]]
        [[ "$3" == "-backend=false" ]]
        [[ "$4" == "-input=false" ]]
        if [[ "${FAIL_TERRAFORM_INIT:-false}" == "true" ]]; then
            printf '%s\n' "${CREDENTIAL_SENTINEL:?}" >&2
            exit 31
        fi
        ;;
    validate)
        [[ "$#" == "2" ]]
        if [[ "${FAIL_TERRAFORM_VALIDATE:-false}" == "true" ]]; then
            exit 23
        fi
        ;;
    *)
        exit 99
        ;;
esac
EOF
chmod +x "$FAKE_BIN/terraform"

run_helper() {
    local output_file="$1"
    PATH="$FAKE_BIN:$PATH" \
        TEST_LOG_DIR="$LOG_DIR" \
        CHECKOUT_TERRAFORM_DIR="$PWD/infra/terraform/.terraform" \
        TF_WORKSPACE="production" \
        TF_CLI_ARGS="-lock-timeout=1s" \
        TF_CLI_ARGS_init="-backend-config=production.hcl" \
        TF_CLI_ARGS_validate="-json" \
        TF_CLI_ARGS_plan="-destroy" \
        TF_VAR_android_signing_certificates='poison' \
        TF_VAR_allow_unauthenticated='false' \
        TF_LOG='TRACE' \
        TF_LOG_PATH="$TMP_DIR/terraform-trace.log" \
        TF_INPUT='1' \
        "$SCRIPT" >"$output_file" 2>&1
}

SUCCESS_OUTPUT="$TMP_DIR/success.out"
set +e
run_helper "$SUCCESS_OUTPUT"
success_status=$?
set -e
assert_exit_code 0 "$success_status"

expected_log="[-chdir=$PWD/infra/terraform][init][-backend=false][-input=false]
[-chdir=$PWD/infra/terraform][validate]"
assert_equals() {
    local expected="$1" actual="$2"
    [[ "$expected" == "$actual" ]] || fail "expected '$expected', got '$actual'"
    pass
}
assert_equals "$expected_log" "$(cat "$LOG_DIR/terraform.log")"
assert_file_contains 'production-backend-metadata' "$CHECKOUT_MARKER"
success_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
assert_equals "1" "$(sort -u "$LOG_DIR/data-dirs.log" | wc -l | tr -d ' ')"
[[ ! -e "$success_data_dir" ]] || fail "expected successful validation to remove $success_data_dir"
pass

rm -f "$LOG_DIR/terraform.log" "$LOG_DIR/data-dirs.log"
FAILURE_OUTPUT="$TMP_DIR/failure.out"
set +e
FAIL_TERRAFORM_VALIDATE="true" run_helper "$FAILURE_OUTPUT"
failure_status=$?
set -e
assert_exit_code 23 "$failure_status"
assert_equals "$expected_log" "$(cat "$LOG_DIR/terraform.log")"
assert_file_contains 'production-backend-metadata' "$CHECKOUT_MARKER"
failure_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
assert_equals "1" "$(sort -u "$LOG_DIR/data-dirs.log" | wc -l | tr -d ' ')"
[[ ! -e "$failure_data_dir" ]] || fail "expected failed validation to remove $failure_data_dir"
pass

assert_file_not_contains 'backend-config' "$LOG_DIR/terraform.log"
assert_file_not_contains 'production.hcl' "$LOG_DIR/terraform.log"

rm -f "$LOG_DIR/terraform.log" "$LOG_DIR/data-dirs.log"
INIT_FAILURE_OUTPUT="$TMP_DIR/init-failure.out"
CREDENTIAL_SENTINEL="VALIDATOR_CREDENTIAL_SENTINEL_MUST_NOT_LEAK"
set +e
FAIL_TERRAFORM_INIT="true" CREDENTIAL_SENTINEL="$CREDENTIAL_SENTINEL" run_helper "$INIT_FAILURE_OUTPUT"
init_failure_status=$?
set -e
assert_exit_code 31 "$init_failure_status"
assert_contains 'Terraform initialization failed' "$(cat "$INIT_FAILURE_OUTPUT")"
assert_not_contains "$CREDENTIAL_SENTINEL" "$(cat "$INIT_FAILURE_OUTPUT")"
assert_equals "[-chdir=$PWD/infra/terraform][init][-backend=false][-input=false]" "$(cat "$LOG_DIR/terraform.log")"
init_failure_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
[[ ! -e "$init_failure_data_dir" ]] || fail "expected failed initialization to remove $init_failure_data_dir"
pass

print_pass_summary "isolated Terraform validation"
