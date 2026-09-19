#!/usr/bin/env bash
# The setup runner's contract: --check never applies, apply is confirmed by a
# second check, a re-run is a no-op, manual steps block their dependants, and
# the exit code separates failure (1) from waiting on a person (3).
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
STEPS="$TMP_DIR/steps"
MARKERS="$TMP_DIR/markers"
mkdir -p "$STEPS" "$MARKERS"

cat >"$STEPS/fine.sh" <<'EOF'
fine_describe() { printf 'already fine'; }
fine_check() { return 0; }
fine_apply() { touch "$MARKERS/fine-applied"; }
EOF

cat >"$STEPS/fixable.sh" <<'EOF'
fixable_describe() { printf 'fixed by apply'; }
fixable_check() { [[ -f "$MARKERS/fixable" ]] && return 0; printf 'not fixed yet\n'; return 1; }
fixable_apply() { touch "$MARKERS/fixable"; printf 'apply ran\n' >>"$MARKERS/apply-log"; }
EOF

cat >"$STEPS/person.sh" <<'EOF'
person_describe() { printf 'needs a person'; }
person_check() { printf 'Click the button in the console\n'; return 2; }
EOF

cat >"$STEPS/dependant.sh" <<'EOF'
dependant_describe() { printf 'needs person first'; }
dependant_requires() { printf 'person'; }
dependant_check() { touch "$MARKERS/dependant-checked"; return 0; }
EOF

cat >"$STEPS/broken.sh" <<'EOF'
broken_describe() { printf 'apply fails'; }
broken_check() { printf 'drifted\n'; return 1; }
broken_apply() { return 1; }
EOF

cat >"$STEPS/stubborn.sh" <<'EOF'
stubborn_describe() { printf 'apply does not fix it'; }
stubborn_check() { printf 'still drifted\n'; return 1; }
stubborn_apply() { return 0; }
EOF

# Runs steps in a fresh shell and prints "<id>=<status>" lines, then "exit=<n>".
run_steps() {
    local check_only="$1"
    shift
    MARKERS="$MARKERS" SETUP_STEPS_DIR="$STEPS" CHECK_ONLY="$check_only" NON_INTERACTIVE=true \
        bash -c '
            set -euo pipefail
            source scripts/lib/common.sh
            source scripts/setup/runner.sh
            for id in "$@"; do run_step "$id" >/dev/null 2>&1; done
            for i in "${!RESULT_IDS[@]}"; do printf "%s=%s\n" "${RESULT_IDS[$i]}" "${RESULT_STATUS[$i]}"; done
            code=0; summary_exit_code || code=$?
            printf "exit=%s\n" "$code"
        ' _ "$@"
}

# --check reports drift without applying anything.
output="$(run_steps true fine fixable)"
assert_contains "fine=ok" "$output"
assert_contains "fixable=drift" "$output"
assert_contains "exit=1" "$output"
assert_file_missing "$MARKERS/fixable"
assert_file_missing "$MARKERS/fine-applied"

# A normal run applies drift, confirms it with a second check, and leaves ok
# steps alone.
output="$(run_steps false fine fixable)"
assert_contains "fixable=applied" "$output"
assert_contains "exit=0" "$output"
assert_file_exists "$MARKERS/fixable"
assert_file_missing "$MARKERS/fine-applied"

# Re-running is a no-op.
output="$(run_steps false fixable)"
assert_contains "fixable=ok" "$output"
[[ "$(wc -l <"$MARKERS/apply-log" | tr -d ' ')" == "1" ]] || fail "apply ran again on a re-run"
pass

# A manual step blocks its dependants without running them; waiting on a
# person is exit 3, not a failure.
output="$(run_steps false person dependant)"
assert_contains "person=manual" "$output"
assert_contains "dependant=blocked" "$output"
assert_contains "exit=3" "$output"
assert_file_missing "$MARKERS/dependant-checked"

# A failing apply, or one that leaves drift behind, is a failure even when a
# manual step is also pending.
output="$(run_steps false person broken stubborn)"
assert_contains "broken=failed" "$output"
assert_contains "stubborn=failed" "$output"
assert_contains "exit=1" "$output"

# The entrypoint rejects unknown arguments and filters steps.
set +e
bad_output="$(./scripts/setup/main.sh --bogus 2>&1)"
bad_exit=$?
set -e
assert_exit_code 1 "$bad_exit"
assert_contains "Unknown argument: --bogus" "$bad_output"

listed="$(./scripts/setup/main.sh production --list --only hooks,play-access)"
assert_contains "hooks" "$listed"
assert_contains "play-access" "$listed"
assert_not_contains "firebase-apps" "$listed"

listed="$(./scripts/setup/main.sh --list)"
assert_not_contains "play-" "$listed"
assert_not_contains "gh-environment" "$listed"

listed="$(./scripts/setup/main.sh staging --list)"
assert_contains "gh-environment" "$listed"
assert_not_contains "play-" "$listed"

print_pass_summary "setup runner"
