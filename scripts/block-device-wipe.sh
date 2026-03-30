#!/usr/bin/env bash
# Claude Code PreToolUse hook: blocks Bash commands that would uninstall the
# LogDate app or clear its data on a connected device.
#
# Exit codes:
#   0 — command is safe, allow execution
#   2 — command is blocked (Claude Code convention)
set -euo pipefail

BLOCKED_MSG="BLOCKED: This command would uninstall the LogDate app or clear its data on a connected device.
This is a protected operation. ONLY the developer may uninstall the app or clear data manually.
Safe alternatives that preserve data: 'adb install', './gradlew installDebug', './run run:android'."

input="$(cat)"

# Extract the command field from the JSON input.
command="$(echo "$input" | grep -oE '"command"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"

# Nothing to check if no command was found.
[[ -z "$command" ]] && exit 0

# adb uninstall (with any flags, serial, etc.)
if echo "$command" | grep -qiE '\badb\b.*\buninstall\b'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# pm uninstall (via adb shell, pipe, xargs, etc.)
if echo "$command" | grep -qiE '\bpm\s+uninstall\b'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# pm clear targeting the LogDate package
if echo "$command" | grep -qiE '\bpm\s+clear\b.*co\.reasonabletech'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# cmd package uninstall
if echo "$command" | grep -qiE '\bcmd\s+package\s+uninstall\b'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# rm -rf on the app data directory
if echo "$command" | grep -qiE '\brm\s+-[a-z]*r[a-z]*\s.*/data/data/co\.reasonabletech'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# Gradle uninstall tasks (any invocation path)
if echo "$command" | grep -qiE '\b(gradlew?|gradle)\b.*\buninstall'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# Catch bare task names anywhere in the command
if echo "$command" | grep -qiE '\buninstallDebug\b|\buninstallRelease\b|\buninstallAll\b'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

# Piped/xargs indirection targeting pm uninstall or pm clear
if echo "$command" | grep -qiE '(echo|printf|cat|xargs).*\bpm\s+(uninstall|clear)\b.*co\.reasonabletech'; then
    echo "$BLOCKED_MSG"
    exit 2
fi

exit 0
