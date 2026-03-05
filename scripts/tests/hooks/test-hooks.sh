#!/usr/bin/env bash
# Regression tests for hook validation utilities.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

VALIDATOR="$REPO_ROOT/scripts/validation/validate-commit-message.sh"
COMMON="$REPO_ROOT/scripts/validation/hook-common.sh"

pass_count=0

assert_exit() {
    local expected="$1"
    shift
    set +e
    "$@" >/dev/null 2>&1
    local status=$?
    set -e
    if [[ $status -ne $expected ]]; then
        echo "FAIL: expected exit $expected, got $status for command: $*"
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

assert_contains() {
    local needle="$1"
    local text="$2"
    if ! echo "$text" | grep -q "$needle"; then
        echo "FAIL: expected output to contain '$needle'"
        echo "$text"
        exit 1
    fi
    pass_count=$((pass_count + 1))
}

# validate-commit-message hard checks
assert_exit 0 "$VALIDATOR" --message "chore: update docs" --context checklist
assert_exit 1 "$VALIDATOR" --message "feat(bad-scope): add playback controls" --context checklist
assert_exit 1 "$VALIDATOR" --message "invalid title" --context checklist
assert_exit 1 "$VALIDATOR" --message "fix(timeline): this title is intentionally made very long so it exceeds seventy two characters for validator checks" --context checklist
assert_exit 1 "$VALIDATOR" --message $'feat(editor): update logic\n\nphase 2 rollout' --context checklist

# validate-commit-message warning behavior (imperative mood warning in commit-msg context)
set +e
warning_output="$("$VALIDATOR" --message "feat(editor): Added playback controls" --context commit-msg 2>&1)"
warning_status=$?
set -e
if [[ $warning_status -ne 0 ]]; then
    echo "FAIL: expected warning-only message to pass"
    echo "$warning_output"
    exit 1
fi
assert_contains "WARNING: Use imperative mood in commit title." "$warning_output"

# hook-common module resolution
source "$COMMON"
resolved="$(modules_from_files $'client/feature/timeline/src/main/kotlin/Foo.kt\nclient/feature/timeline/src/main/kotlin/Bar.kt\nclient/repository/src/commonMain/kotlin/Baz.kt')"
assert_contains "^:client:feature:timeline$" "$resolved"
assert_contains "^:client:repository$" "$resolved"

echo "Hook regression tests passed ($pass_count assertions)."
