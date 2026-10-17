#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# scripts/lib/hardcoded-compose-literals.awk describes which literals count as on-screen text.
# The check needs only git and a POSIX awk, so it runs the same on developer Macs and CI runners.
SCANNER="scripts/lib/hardcoded-compose-literals.awk"

# Tests, build outputs and the shared screenshot scenes hold fixture content, not app copy. The export
# archive's README and JSON Schemas are documents written into the archive, not text shown in the app.
PATHSPECS=(
  ':(glob)app/**/*.kt'
  ':(glob)client/**/*.kt'
  ':(glob,exclude)**/build/**'
  ':(glob,exclude)**/src/*Test/**'
  ':(glob,exclude)**/src/test/**'
  ':(glob,exclude)client/screenshot-scenes/**'
  ':(glob,exclude)client/domain/src/commonMain/kotlin/app/logdate/client/domain/export/archive/**'
)

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
FILES="$WORK_DIR/files"
SIGNATURES="$WORK_DIR/signatures"

# git grep exits 1 when nothing matches; any other non-zero status is a failed search.
GREP_STATUS=0
git grep --untracked -l -z -e '"' -- "${PATHSPECS[@]}" > "$FILES" || GREP_STATUS=$?
if (( GREP_STATUS > 1 )); then
  echo "Listing Kotlin sources for the hardcoded Compose literal check failed (git grep exit $GREP_STATUS)." >&2
  exit "$GREP_STATUS"
fi

MATCHES=""
if [[ -s "$FILES" ]]; then
  SCAN_STATUS=0
  xargs -0 awk -v phase=1 -f "$SCANNER" < "$FILES" > "$SIGNATURES" || SCAN_STATUS=$?
  if (( SCAN_STATUS == 0 )); then
    MATCHES="$(xargs -0 awk -v phase=2 -f "$SCANNER" "$SIGNATURES" < "$FILES")" || SCAN_STATUS=$?
  fi
  if (( SCAN_STATUS != 0 )); then
    echo "Scanning for hardcoded Compose literals failed (exit $SCAN_STATUS)." >&2
    exit 2
  fi
fi

if [[ -n "$MATCHES" ]]; then
  echo "Found hardcoded static Compose literals:"
  echo "$MATCHES"
  echo
  echo "Move these strings into string resources: Compose resources in multiplatform modules, Android resources in Android-only ones."
  exit 1
fi

echo "No hardcoded static Compose literals found."
