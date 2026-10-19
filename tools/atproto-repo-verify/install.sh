#!/usr/bin/env bash
# Installs a Node tool's locked packages: ./install.sh [tool-directory], defaulting to this one.
#
# Uses pnpm when it's installed, and otherwise the version the tool's package.json pins, run
# through npx. (corepack can't be used: it may fetch a different pnpm than the pin, which pnpm then
# refuses to run under.) Used by the atproto-repo Gradle build and by `./run setup`.
set -euo pipefail

tool_dir="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"

if ! command -v node >/dev/null 2>&1; then
    echo "Node.js is not installed; install Node.js 22 or newer (or run ./run setup)." >&2
    exit 1
fi

pnpm=(pnpm)
if ! command -v pnpm >/dev/null 2>&1; then
    pinned="$(node -p 'require(process.argv[1]).packageManager' "$tool_dir/package.json")"
    pnpm=(npx --yes "$pinned")
fi
"${pnpm[@]}" --dir "$tool_dir" install --frozen-lockfile --silent
