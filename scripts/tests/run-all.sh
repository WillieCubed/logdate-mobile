#!/usr/bin/env bash
# Runs host script checks and reports each result. Docker-backed database
# integration checks run in the Linux server/client e2e lane.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

failed=()
count=0
while IFS= read -r test; do
    count=$((count + 1))
    case "$test" in
        *.py) runner=(scripts/passkey-verify/.venv/bin/python "$test") ;;
        *) runner=(bash "$test") ;;
    esac
    if output="$("${runner[@]}" 2>&1)"; then
        printf 'ok    %s\n' "$test"
    else
        printf 'FAIL  %s\n' "$test"
        printf '%s\n' "$output" | tail -20 | sed 's/^/      /'
        failed+=("$test")
    fi
done < <(find scripts/tests -path scripts/tests/database -prune -o -type f \( -name 'test-*.sh' -o -name 'test-*.py' \) -print | sort)

printf '\n%d of %d script tests passed\n' "$((count - ${#failed[@]}))" "$count"
[[ ${#failed[@]} -eq 0 ]]
