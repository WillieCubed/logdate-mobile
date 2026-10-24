#!/usr/bin/env bash
# Installs a Node tool's locked packages: ./install.sh [tool-directory], defaulting to this one.
#
# Uses the installed pnpm when it is the version the tool's package.json pins, and otherwise runs
# the pinned version through npx. (A corepack-managed pnpm of another version refuses to run the
# project and cannot switch versions itself.) Used by the atproto-repo Gradle build and by
# `./run setup`.
set -euo pipefail

tool_dir="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"

if ! command -v node >/dev/null 2>&1; then
    echo "Node.js is not installed; install Node.js 22 or newer (or run ./run setup)." >&2
    exit 1
fi

pinned="$(node -p 'require(process.argv[1]).packageManager' "$tool_dir/package.json")"
pnpm=(npx --yes "$pinned")
if command -v pnpm >/dev/null 2>&1 && [[ "pnpm@$(pnpm --version 2>/dev/null)" == "$pinned" ]]; then
    pnpm=(pnpm)
fi
"${pnpm[@]}" --dir "$tool_dir" install --frozen-lockfile --silent
