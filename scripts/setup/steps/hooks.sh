#!/usr/bin/env bash
# Git hooks that validate commit messages and staged files.

hooks_describe() {
    printf 'Git uses the repository hooks in .githooks'
}

hooks_check() {
    local current
    current="$(git config --get core.hooksPath || true)"
    [[ "$current" == ".githooks" ]] && return 0
    printf 'core.hooksPath is %s, expected .githooks\n' "${current:-unset}"
    return 1
}

hooks_apply() {
    "$LOGDATE_REPO_ROOT/scripts/setup-hooks.sh"
}
