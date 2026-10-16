#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# POSIX extended regular expressions, so the check runs with git alone on both
# developer Macs and CI runners. Text( must start a name, so calls such as
# writeText("...") or ClipData.newPlainText("...") do not match.
PATTERNS=(
  '(^|[^[:alnum:]_])Text\([[:space:]]*"[^"$]+"'
  'contentDescription[[:space:]]*=[[:space:]]*"[^"$]+"'
  'setContentText\([[:space:]]*"[^"$]+"'
  'setContentTitle\([[:space:]]*"[^"$]+"'
  'bigText\([[:space:]]*"[^"$]+"'
)

PATHSPECS=(
  ':(glob)app/**/*.kt'
  ':(glob)client/**/*.kt'
  ':(glob,exclude)**/build/**'
  ':(glob,exclude)**/src/*Test/**'
  ':(glob,exclude)**/README.md'
)

GREP_ARGS=(--untracked --no-color -n -E)
for pattern in "${PATTERNS[@]}"; do
  GREP_ARGS+=(-e "$pattern")
done

# git grep exits 1 when nothing matches; any other non-zero status is a failed search.
GREP_STATUS=0
MATCHES="$(git grep "${GREP_ARGS[@]}" -- "${PATHSPECS[@]}")" || GREP_STATUS=$?
if (( GREP_STATUS > 1 )); then
  echo "Searching for hardcoded Compose literals failed (git grep exit $GREP_STATUS)." >&2
  exit "$GREP_STATUS"
fi

FILTERED="$(printf '%s\n' "$MATCHES" | awk -F: '
  NF >= 3 {
    code = ""
    for (i = 3; i <= NF; i++) {
      code = code $i
      if (i < NF) code = code ":"
    }
    if (code !~ /^[[:space:]]*\/\//) print $0
  }
')"

if [[ -n "$FILTERED" ]]; then
  echo "Found hardcoded static Compose literals:"
  echo "$FILTERED"
  exit 1
fi

echo "No hardcoded static Compose literals found."
