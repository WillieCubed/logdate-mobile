#!/usr/bin/env bash
set -euo pipefail

# Commit 10: chore(infra): agent safety guardrails and gitignore

git add \
  .gitignore \
  .gemini/styleguide.md \
  .github/copilot-instructions.md \
  scripts/block-device-wipe.sh

git commit -m "$(cat <<'EOF'
chore(infra): establish device safety guardrails for coding agents

Prevents coding agents (Claude Code, Copilot, Gemini) from
running adb uninstall or pm clear against the developer's
personal device. Each agent gets platform-appropriate
instructions; Claude Code also gets a PreToolUse hook script
that blocks dangerous commands at runtime.

Also ignores *.log build artifacts in .gitignore.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"

echo "Commit done."
