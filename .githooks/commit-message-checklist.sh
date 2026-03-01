#!/usr/bin/env bash
# commit-message-checklist.sh - Pre-validates commit messages before git commit runs
#
# Called as a Claude Code pre-tool-use hook for Bash tool when running git commit.
# Catches common AI agent commit message mistakes BEFORE the commit-msg hook.
#
# Only validates simple -m "message" commits. Heredoc-style commits are validated
# by the commit-msg git hook instead.

set -euo pipefail

input="$(cat)"

# Extract the bash command
command="$(echo "$input" | grep -oE '"command"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"

if [[ -z "$command" ]]; then
    exit 0
fi

# Only check git commit commands
if ! echo "$command" | grep -qE 'git commit'; then
    exit 0
fi

# Check for forbidden flags in the command itself (regardless of message format)
errors=()

if echo "$command" | grep -qE 'git add (-A|--all|\. )'; then
    errors+=("'git add -A' / 'git add .' is FORBIDDEN. Stage specific files only.")
fi

if echo "$command" | grep -qE '\-\-no-verify'; then
    errors+=("'--no-verify' is FORBIDDEN. Fix the underlying issue instead of bypassing hooks.")
fi

# Extract commit message from simple -m "message" format only
# Heredoc-style messages (cat <<'EOF') are too complex to parse here;
# the commit-msg git hook handles those.
commit_msg="$(echo "$command" | grep -oE '\-m "([^"]*)"' | head -1 | sed 's/-m "//;s/"$//' || true)"

if [[ -z "$commit_msg" ]]; then
    # Try single quotes
    commit_msg="$(echo "$command" | grep -oE "\-m '([^']*)'" | head -1 | sed "s/-m '//;s/'$//" || true)"
fi

# If we still can't extract a message (heredoc, etc.), skip message checks
# and let the commit-msg git hook handle it
if [[ -n "$commit_msg" ]]; then
    # Check for phase numbers
    if echo "$commit_msg" | grep -qiE "phase[[:space:]]+[0-9]"; then
        errors+=("Phase numbers are FORBIDDEN in commit messages. Describe WHAT changed, not which phase.")
    fi

    # Check proper noun capitalization
    title="$(echo "$commit_msg" | head -1)"
    desc="$(echo "$title" | sed -E "s/^(feat|fix|refactor|docs|style|test|chore|perf)(\([^)]+\))?!?: //")"
    lowercase_violations=""
    for term in "api" "cli" "sdk" "jwt" "oauth" "url" "html" "css" "ssr" "ssg"; do
        upper="$(echo "$term" | tr '[:lower:]' '[:upper:]')"
        if echo "$desc" | grep -qwi "$term" && ! echo "$desc" | grep -qw "$upper"; then
            lowercase_violations="$lowercase_violations $term->$upper"
        fi
    done
    for term in "kotlin:Kotlin" "android:Android" "compose:Compose" "gradle:Gradle" "koin:Koin" "ktor:Ktor" "postgresql:PostgreSQL" "redis:Redis" "websocket:WebSocket" "typescript:TypeScript" "javascript:JavaScript"; do
        lower="${term%%:*}"
        proper="${term##*:}"
        if echo "$desc" | grep -qwi "$lower" && ! echo "$desc" | grep -qw "$proper"; then
            lowercase_violations="$lowercase_violations $lower->$proper"
        fi
    done
    if [[ -n "$lowercase_violations" ]]; then
        errors+=("Capitalize proper nouns in commit titles:$lowercase_violations")
    fi
fi

if [[ ${#errors[@]} -gt 0 ]]; then
    echo "COMMIT MESSAGE PRE-CHECK FAILED:"
    echo ""
    for err in "${errors[@]}"; do
        echo "  - $err"
    done
    echo ""
    echo "Fix the commit message and try again."
    exit 2
fi

exit 0
