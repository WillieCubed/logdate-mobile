#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PATTERNS=(
  'Text\(\s*"[^"$]+"'
  'contentDescription\s*=\s*"[^"$]+"'
  'setContentText\(\s*"[^"$]+"'
  'setContentTitle\(\s*"[^"$]+"'
  'bigText\(\s*"[^"$]+"'
)

RG_ARGS=(
  -n
  -g '!**/build/**'
  -g '!**/src/*Test/**'
  -g '!**/README.md'
  -g '*.kt'
)

MATCHES="$(rg "${RG_ARGS[@]}" $(printf " -e %q" "${PATTERNS[@]}") app client || true)"

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

if [[ -n "${FILTERED// }" ]]; then
  echo "Found hardcoded static Compose literals:"
  echo "$FILTERED"
  exit 1
fi

echo "No hardcoded static Compose literals found."
