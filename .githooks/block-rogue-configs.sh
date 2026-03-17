#!/usr/bin/env bash
set -euo pipefail

BLOCKED_MSG="STOP. Do NOT attempt to work around this block.
Tell the user: \"I need to modify [file], which is a protected config file. Please make this change yourself or explicitly approve the edit.\"
Do NOT use sed, awk, cp, mv, tee, echo, cat, or any other Bash command to bypass this protection."

protected_exact=(
    "build.gradle.kts"
    "settings.gradle.kts"
    "gradle.properties"
    ".claude/settings.json"
)

protected_dirs=(
    ".github/workflows/"
    ".githooks/"
)

input="$(cat)"
file_path="$(echo "$input" | grep -oE '"file_path"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"
command="$(echo "$input" | grep -oE '"command"\s*:\s*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//' || true)"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"

normalize_path() {
    local path="$1"
    [[ -n "$repo_root" ]] && path="${path#"$repo_root/"}"
    echo "$path"
}

is_protected() {
    local rel="$1"
    for exact in "${protected_exact[@]}"; do
        [[ "$rel" == "$exact" ]] && return 0
    done
    for dir in "${protected_dirs[@]}"; do
        case "$rel" in "$dir"*) return 0 ;; esac
    done
    return 1
}

if [[ -n "$file_path" ]]; then
    rel="$(normalize_path "$file_path")"
    if is_protected "$rel"; then
        echo "BLOCKED: '$rel' is a protected config file."
        echo ""
        echo "$BLOCKED_MSG"
        exit 2
    fi
fi

if [[ -n "$command" ]]; then
    if echo "$command" | grep -qE "(sed -i|awk|tee |> |>> |cp |mv |chmod |chown )"; then
        for exact in "${protected_exact[@]}"; do
            if echo "$command" | grep -qE "(^|[ '\"/])${exact}([ '\"/]|$)"; then
                echo "BLOCKED: Bash command targets protected path '$exact'."
                echo ""
                echo "$BLOCKED_MSG"
                exit 2
            fi
        done
        for dir in "${protected_dirs[@]}"; do
            if echo "$command" | grep -qF "$dir"; then
                echo "BLOCKED: Bash command targets protected directory '$dir'."
                echo ""
                echo "$BLOCKED_MSG"
                exit 2
            fi
        done
    fi
fi

exit 0
