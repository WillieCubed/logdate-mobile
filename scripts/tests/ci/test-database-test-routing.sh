#!/usr/bin/env bash
# Host script checks must not start Docker-backed database integration fixtures.
set -euo pipefail
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT
mkdir -p "$SANDBOX/scripts/tests/host" "$SANDBOX/scripts/tests/database"
cp scripts/tests/run-all.sh "$SANDBOX/scripts/tests/"
git -C "$SANDBOX" init -q
cat > "$SANDBOX/scripts/tests/host/test-host.sh" <<'TEST'
exit 0
TEST
cat > "$SANDBOX/scripts/tests/database/test-database.sh" <<'TEST'
echo 'Database integration fixture incorrectly ran on the host lane'
exit 42
TEST
OUTPUT="$(cd "$SANDBOX" && bash scripts/tests/run-all.sh 2>&1)" || fail "$OUTPUT"
assert_contains '1 of 1 script tests passed' "$OUTPUT"
assert_not_contains 'Database integration fixture' "$OUTPUT"
print_pass_summary "Database test routing"
