#!/usr/bin/env bash
# setup-hooks.sh - Configure git to use .githooks/ for commit validation
#
# Run once after cloning or when hooks are updated:
#   ./scripts/setup-hooks.sh

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "Configuring git hooks path..."
git config core.hooksPath .githooks

echo "Verifying hooks are executable..."
chmod +x .githooks/*

echo ""
echo "Git hooks configured successfully."
echo "Hooks directory: .githooks/"
echo "Active hooks: $(ls .githooks/ | tr '\n' ' ')"
echo ""
echo "Hooks will now validate:"
echo "  - Commit messages (format, scope, length, no phase numbers)"
echo "  - Staged files (sensitive file detection, ktlint)"
echo "  - Pre-push (compile check, tests, message re-validation)"
