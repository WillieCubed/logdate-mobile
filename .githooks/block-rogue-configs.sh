#!/usr/bin/env bash
# block-rogue-configs.sh - Prevents AI agents from modifying critical config files
#
# Called as a Claude Code pre-tool-use hook for Edit, Write, AND Bash tools.
# Blocks modifications to root build configs, settings, and CI pipelines.
#
# For Edit/Write: extracts file_path from tool input JSON.
# For Bash: scans the command string for writes to protected paths.

set -euo pipefail

BLOCKED_MSG="STOP. Do NOT attempt to work around this block.
Tell the user: \"I need to modify [file], which is a protected config file. Please make this change yourself or explicitly approve the edit.\"
Do NOT use sed, awk, cp, mv, tee, echo, cat, or any other Bash command to bypass this protection."

# Protected file patterns (relative to repo root)
protected_patterns=(
    "build.gradle.kts"
    "settings.gradle.kts"
    "gradle.properties"
    "gradle/libs.versions.toml"
    ".github/workflows/"
    ".githooks/"
    ".claude/settings.json"
)

input="$(cat)"

# Detect tool type from input structure
file_path="$(echo "$input" | grep -oE '"file_path"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"
command="$(echo "$input" | grep -oE '"command"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"

check_path() {
    local path="$1"
    # Normalize to relative path
    if [[ -n "$repo_root" ]]; then
        path="${path#"$repo_root/"}"
    fi
    for pattern in "${protected_patterns[@]}"; do
        case "$path" in
            $pattern*)
                echo "BLOCKED: '$path' is a protected config file."
                echo ""
                echo "$BLOCKED_MSG"
                return 1
                ;;
        esac
    done
    return 0
}

# Edit/Write tool: check file_path directly
if [[ -n "$file_path" ]]; then
    check_path "$file_path" || exit 2
fi

# Bash tool: scan command for writes to protected paths
if [[ -n "$command" ]]; then
    # List of write-capable commands that could target files
    write_commands="sed -i|awk .* >|tee |cat .* >|echo .* >|printf .* >|cp |mv |chmod |chown "

    for pattern in "${protected_patterns[@]}"; do
        # Check if the command references a protected path
        if echo "$command" | grep -qF "$pattern"; then
            # Check if it's a write operation (not just reading/grepping)
            if echo "$command" | grep -qE "(sed -i|awk|tee |cat .*>|echo .*>|printf .*>|cp |mv |chmod |chown |> *|>> *)"; then
                echo "BLOCKED: Bash command targets protected path '$pattern'."
                echo ""
                echo "$BLOCKED_MSG"
                exit 2
            fi
        fi
    done
fi

exit 0
