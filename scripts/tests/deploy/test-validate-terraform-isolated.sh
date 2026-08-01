#!/usr/bin/env bash
# Real-behavior tests for backend-free Terraform validation isolation.

set -euo pipefail

OPERATOR_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/tests/lib/assertions.sh
source "$OPERATOR_ROOT/scripts/tests/lib/assertions.sh"

TMP_DIR="$(mktemp -d)"
chmod 700 "$TMP_DIR"
SOURCE_REPO="$TMP_DIR/source-repo"
FAKE_BIN="$TMP_DIR/bin"
LOG_DIR="$TMP_DIR/logs"
STATUS_BEFORE="$(git -C "$OPERATOR_ROOT" status --short)"
OPERATOR_SOURCE_HASHES_BEFORE="$TMP_DIR/operator-source-hashes.before"
OPERATOR_SOURCE_HASHES_AFTER="$TMP_DIR/operator-source-hashes.after"
SYNTHETIC_SOURCE_HASHES_BEFORE="$TMP_DIR/synthetic-source-hashes.before"
SYNTHETIC_SOURCE_HASHES_AFTER="$TMP_DIR/synthetic-source-hashes.after"

cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

snapshot_operator_sources() {
    local output_file="$1" source_root="${2:-$OPERATOR_ROOT}"
    python3 - "$source_root" >"$output_file" <<'PY'
import hashlib
import os
import pathlib
import stat
import sys

source_root = pathlib.Path(sys.argv[1])
terraform_root = source_root / "infra" / "terraform"


def relative(path: pathlib.Path) -> str:
    return path.relative_to(source_root).as_posix()


def record(path: pathlib.Path) -> None:
    path_stat = path.lstat()
    mode = oct(stat.S_IMODE(path_stat.st_mode))
    if path.is_symlink():
        print(f"L {relative(path)} {mode} {os.readlink(path)}")
    elif path.is_dir():
        print(f"D {relative(path)} {mode} {path_stat.st_mtime_ns}")
        for child in sorted(path.iterdir(), key=lambda item: item.name):
            record(child)
    elif path.is_file():
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        print(f"F {relative(path)} {mode} {path_stat.st_size} {path_stat.st_mtime_ns} {digest.hexdigest()}")
    else:
        print(f"O {relative(path)} {mode} {path_stat.st_mtime_ns}")


def is_root_snapshot_file(path: pathlib.Path) -> bool:
    name = path.name
    return (
        name.endswith(".tf")
        or name.endswith(".tfvars")
        or name == ".terraform.lock.hcl"
        or name == ".terraform.tfstate.lock.info"
        or name == "terraform.tfstate"
        or name.startswith("terraform.tfstate.")
        or name.endswith(".tfstate")
        or ".tfstate." in name
        or name == "crash.log"
        or (name.startswith("crash.") and name.endswith(".log"))
    )


for anchor_name in (".terraform", "terraform.tfstate.d"):
    anchor = terraform_root / anchor_name
    if anchor.exists() or anchor.is_symlink():
        record(anchor)
    else:
        print(f"A {relative(anchor)} absent")

if terraform_root.is_dir():
    for child in sorted(terraform_root.iterdir(), key=lambda item: item.name):
        if child.name not in {".terraform", "terraform.tfstate.d"} and is_root_snapshot_file(child):
            record(child)
PY
}

assert_runtime_snapshot_detects_mutation() {
    local probe_root="$TMP_DIR/runtime-snapshot-probe"
    local before="$TMP_DIR/runtime-snapshot-probe.before"
    local after="$TMP_DIR/runtime-snapshot-probe.after"
    mkdir -p "$probe_root/infra/terraform"
    printf 'variable "probe" {}\n' >"$probe_root/infra/terraform/variables.tf"
    snapshot_operator_sources "$before" "$probe_root"
    mkdir -p "$probe_root/infra/terraform/.terraform/providers"
    printf 'mutation\n' >"$probe_root/infra/terraform/.terraform/providers/reviewer-marker"
    snapshot_operator_sources "$after" "$probe_root"
    cmp -s "$before" "$after" && fail "runtime snapshot did not detect a nested .terraform mutation"
    pass
}

snapshot_synthetic_sources() {
    local output_file="$1"
    (
        cd "$SOURCE_REPO"
        find infra/terraform -maxdepth 1 -type f -print0 |
            sort -z |
            xargs -0 shasum -a 256
    ) >"$output_file"
}

assert_equals() {
    local expected="$1" actual="$2"
    [[ "$expected" == "$actual" ]] || fail "expected '$expected', got '$actual'"
    pass
}

assert_zero_bytes() {
    local file="$1"
    assert_equals "0" "$(wc -c <"$file" | tr -d ' ')"
}

snapshot_operator_sources "$OPERATOR_SOURCE_HASHES_BEFORE"
assert_runtime_snapshot_detects_mutation
mkdir -p "$SOURCE_REPO/infra/terraform" "$SOURCE_REPO/scripts" "$FAKE_BIN" "$LOG_DIR"
cp "$OPERATOR_ROOT/scripts/validate-terraform-isolated.sh" "$SOURCE_REPO/scripts/validate-terraform-isolated.sh"
chmod +x "$SOURCE_REPO/scripts/validate-terraform-isolated.sh"
cat >"$SOURCE_REPO/infra/terraform/versions.tf" <<'EOF'
terraform {
  required_version = ">= 1.8.0"
}
EOF
cat >"$SOURCE_REPO/infra/terraform/variables.tf" <<'EOF'
variable "example" {
  type    = string
  default = "source"
}
EOF
printf '# source-lockfile\n' >"$SOURCE_REPO/infra/terraform/.terraform.lock.hcl"
git -C "$SOURCE_REPO" init -q
git -C "$SOURCE_REPO" config user.name 'Task 1 Test'
git -C "$SOURCE_REPO" config user.email 'task-1@example.invalid'
git -C "$SOURCE_REPO" update-index --add -- \
    infra/terraform/versions.tf \
    infra/terraform/variables.tf \
    infra/terraform/.terraform.lock.hcl \
    scripts/validate-terraform-isolated.sh
VALIDATOR_TREE="$(git -C "$SOURCE_REPO" write-tree)"
VALIDATOR_COMMIT="$(printf 'test: create synthetic validator repository\n' | git -C "$SOURCE_REPO" commit-tree "$VALIDATOR_TREE")"
git -C "$SOURCE_REPO" update-ref HEAD "$VALIDATOR_COMMIT"
SOURCE_LOCK_CHECKSUM="$(shasum -a 256 "$SOURCE_REPO/infra/terraform/.terraform.lock.hcl" | awk '{print $1}')"
snapshot_synthetic_sources "$SYNTHETIC_SOURCE_HASHES_BEFORE"
[[ ! -e "$SOURCE_REPO/infra/terraform/.terraform" ]] || fail "synthetic validator source started with Terraform metadata"

cat >"$FAKE_BIN/terraform" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${TEST_LOG_DIR:?}"
SOURCE_TF_DIR="${SOURCE_TF_DIR:?}"
TF_DATA_DIR="${TF_DATA_DIR:?}"
[[ -d "$TF_DATA_DIR" ]] || exit 91
[[ "$TF_DATA_DIR" != "$SOURCE_TF_DIR/.terraform" ]] || exit 92
[[ "${TF_INPUT:-}" == "0" ]] || exit 93
[[ -z "${TF_WORKSPACE:-}${TF_LOG:-}${TF_LOG_PATH:-}" ]] || exit 94
while IFS='=' read -r name _; do
    case "$name" in
        TF_DATA_DIR | TF_INPUT) ;;
        TF_*) exit 95 ;;
    esac
done < <(env)

config_dir="${1#-chdir=}"
private_parent="$(dirname "$config_dir")"
[[ "$config_dir" != "$SOURCE_TF_DIR" ]] || exit 96
[[ "$private_parent" == "$(dirname "$TF_DATA_DIR")" ]] || exit 97
[[ "$(stat -f '%Lp' "$private_parent" 2>/dev/null || stat -c '%a' "$private_parent")" == "700" ]] || exit 99
[[ -f "$config_dir/versions.tf" && -f "$config_dir/variables.tf" ]] || exit 100
printf '%s\n' "$TF_DATA_DIR" >>"$LOG_DIR/data-dirs.log"
printf '%s\n' "$config_dir" >>"$LOG_DIR/config-dirs.log"
{
    for arg in "$@"; do
        printf '[%s]' "$arg"
    done
    printf '\n'
} >>"$LOG_DIR/terraform.log"

case "${2:-}" in
    init)
        [[ "$#" == "4" && "$3" == "-backend=false" && "$4" == "-input=false" ]] || exit 102
        [[ ! -e "$config_dir/.terraform" ]] || exit 98
        [[ "$(cat "$config_dir/.terraform.lock.hcl")" == "# source-lockfile" ]] || exit 101
        mkdir -p "$config_dir/.terraform"
        printf 'private-lockfile-rewritten\n' >"$config_dir/.terraform.lock.hcl"
        if [[ "${FAIL_TERRAFORM_INIT:-false}" == "true" ]]; then
            printf '%s\n' "${CREDENTIAL_SENTINEL:?}" >&2
            exit 31
        fi
        ;;
    validate)
        [[ "$#" == "2" ]] || exit 103
        [[ "$(cat "$config_dir/.terraform.lock.hcl")" == "private-lockfile-rewritten" ]] || exit 104
        if [[ "${FAIL_TERRAFORM_VALIDATE:-false}" == "true" ]]; then
            printf '%s\n' "${CREDENTIAL_SENTINEL:?}" >&2
            exit 23
        fi
        ;;
    *)
        exit 105
        ;;
esac
EOF
chmod +x "$FAKE_BIN/terraform"

run_helper() {
    local stdout_file="$1" stderr_file="$2"
    (
        cd "$SOURCE_REPO"
        PATH="$FAKE_BIN:$PATH" \
            TEST_LOG_DIR="$LOG_DIR" \
            SOURCE_TF_DIR="$SOURCE_REPO/infra/terraform" \
            TF_WORKSPACE="production" \
            TF_CLI_ARGS="-lock-timeout=1s" \
            TF_CLI_ARGS_init="-backend-config=production.hcl" \
            TF_CLI_ARGS_validate="-json" \
            TF_CLI_ARGS_plan="-destroy" \
            TF_VAR_example="poison" \
            TF_LOG="TRACE" \
            TF_LOG_PATH="$TMP_DIR/terraform-trace.log" \
            TF_CLI_CONFIG_FILE="$TMP_DIR/hostile-terraform.rc" \
            TF_PLUGIN_CACHE_DIR="$TMP_DIR/hostile-plugin-cache" \
            TF_INPUT="1" \
            scripts/validate-terraform-isolated.sh >"$stdout_file" 2>"$stderr_file"
    )
}

SUCCESS_STDOUT="$TMP_DIR/success.stdout"
SUCCESS_STDERR="$TMP_DIR/success.stderr"
run_helper "$SUCCESS_STDOUT" "$SUCCESS_STDERR"
assert_zero_bytes "$SUCCESS_STDOUT"
assert_zero_bytes "$SUCCESS_STDERR"
expected_log="[init][-backend=false][-input=false]
[validate]"
actual_log="$(sed -E 's/^\[-chdir=[^]]+\]//' "$LOG_DIR/terraform.log")"
assert_equals "$expected_log" "$actual_log"
assert_equals "$SOURCE_LOCK_CHECKSUM" "$(shasum -a 256 "$SOURCE_REPO/infra/terraform/.terraform.lock.hcl" | awk '{print $1}')"
success_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
success_config_dir="$(head -n 1 "$LOG_DIR/config-dirs.log")"
[[ ! -e "$success_data_dir" && ! -e "$success_config_dir" ]] || fail "successful validation left private artifacts"
pass

rm -f "$LOG_DIR/terraform.log" "$LOG_DIR/data-dirs.log" "$LOG_DIR/config-dirs.log"
VALIDATE_STDOUT="$TMP_DIR/validate-failure.stdout"
VALIDATE_STDERR="$TMP_DIR/validate-failure.stderr"
CREDENTIAL_SENTINEL="VALIDATOR_CREDENTIAL_SENTINEL_MUST_NOT_LEAK"
set +e
FAIL_TERRAFORM_VALIDATE="true" CREDENTIAL_SENTINEL="$CREDENTIAL_SENTINEL" \
    run_helper "$VALIDATE_STDOUT" "$VALIDATE_STDERR"
validate_status=$?
set -e
assert_exit_code 23 "$validate_status"
assert_zero_bytes "$VALIDATE_STDOUT"
assert_contains 'Terraform validation failed' "$(cat "$VALIDATE_STDERR")"
assert_not_contains "$CREDENTIAL_SENTINEL" "$(cat "$VALIDATE_STDERR")"
assert_equals "$SOURCE_LOCK_CHECKSUM" "$(shasum -a 256 "$SOURCE_REPO/infra/terraform/.terraform.lock.hcl" | awk '{print $1}')"
validate_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
validate_config_dir="$(head -n 1 "$LOG_DIR/config-dirs.log")"
[[ ! -e "$validate_data_dir" && ! -e "$validate_config_dir" ]] || fail "failed validation left private artifacts"
pass

rm -f "$LOG_DIR/terraform.log" "$LOG_DIR/data-dirs.log" "$LOG_DIR/config-dirs.log"
INIT_STDOUT="$TMP_DIR/init-failure.stdout"
INIT_STDERR="$TMP_DIR/init-failure.stderr"
set +e
FAIL_TERRAFORM_INIT="true" CREDENTIAL_SENTINEL="$CREDENTIAL_SENTINEL" \
    run_helper "$INIT_STDOUT" "$INIT_STDERR"
init_status=$?
set -e
assert_exit_code 31 "$init_status"
assert_zero_bytes "$INIT_STDOUT"
assert_contains 'Terraform initialization failed' "$(cat "$INIT_STDERR")"
assert_not_contains "$CREDENTIAL_SENTINEL" "$(cat "$INIT_STDERR")"
assert_equals "$SOURCE_LOCK_CHECKSUM" "$(shasum -a 256 "$SOURCE_REPO/infra/terraform/.terraform.lock.hcl" | awk '{print $1}')"
init_data_dir="$(head -n 1 "$LOG_DIR/data-dirs.log")"
init_config_dir="$(head -n 1 "$LOG_DIR/config-dirs.log")"
[[ ! -e "$init_data_dir" && ! -e "$init_config_dir" ]] || fail "failed initialization left private artifacts"
pass

REAL_STDOUT="$TMP_DIR/real.stdout"
REAL_STDERR="$TMP_DIR/real.stderr"
(cd "$SOURCE_REPO" && PATH="$(dirname "$(command -v terraform)"):$PATH" scripts/validate-terraform-isolated.sh >"$REAL_STDOUT" 2>"$REAL_STDERR")
assert_zero_bytes "$REAL_STDOUT"
assert_zero_bytes "$REAL_STDERR"
assert_equals "$SOURCE_LOCK_CHECKSUM" "$(shasum -a 256 "$SOURCE_REPO/infra/terraform/.terraform.lock.hcl" | awk '{print $1}')"

snapshot_synthetic_sources "$SYNTHETIC_SOURCE_HASHES_AFTER"
cmp -s "$SYNTHETIC_SOURCE_HASHES_BEFORE" "$SYNTHETIC_SOURCE_HASHES_AFTER" || fail "validator changed synthetic source configuration"
pass
[[ ! -e "$SOURCE_REPO/infra/terraform/.terraform" && ! -e "$SOURCE_REPO/infra/terraform/terraform.tfstate" ]] ||
    fail "validator left source-checkout Terraform artifacts"
pass

snapshot_operator_sources "$OPERATOR_SOURCE_HASHES_AFTER"
cmp -s "$OPERATOR_SOURCE_HASHES_BEFORE" "$OPERATOR_SOURCE_HASHES_AFTER" || fail "validator tests changed operator Terraform sources"
pass
assert_equals "$STATUS_BEFORE" "$(git -C "$OPERATOR_ROOT" status --short)"

print_pass_summary "isolated Terraform validation"
