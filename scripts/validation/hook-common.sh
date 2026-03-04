#!/usr/bin/env bash
# Shared helpers for git hook scripts.

ZERO_SHA="0000000000000000000000000000000000000000"
VALID_TYPES="feat|fix|refactor|docs|style|test|chore|perf"

uniq_non_empty_lines() {
    awk 'NF && !seen[$0]++'
}

modules_from_files() {
    local files="$1"
    local modules=""
    local file=""
    local dir=""
    local module=""

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        dir="$(dirname "$file")"
        while [[ "$dir" != "." && "$dir" != "/" ]]; do
            if [[ -f "$dir/build.gradle.kts" ]]; then
                module=":$(echo "$dir" | tr '/' ':')"
                modules="${modules}${module}"$'\n'
                break
            fi
            dir="$(dirname "$dir")"
        done
    done <<< "$files"

    echo "$modules" | uniq_non_empty_lines
}

collect_unpushed_commits() {
    local local_shas="$1"
    local all_commits=""
    local sha=""
    local commits=""

    while IFS= read -r sha; do
        [[ -z "$sha" ]] && continue
        commits="$(git rev-list "$sha" --not --remotes 2>/dev/null || true)"
        if [[ -n "$commits" ]]; then
            all_commits="${all_commits}${commits}"$'\n'
        fi
    done <<< "$local_shas"

    echo "$all_commits" | uniq_non_empty_lines
}

collect_changed_files_from_commits() {
    local commits="$1"
    local changed=""
    local sha=""
    local files=""

    while IFS= read -r sha; do
        [[ -z "$sha" ]] && continue
        files="$(git diff-tree --no-commit-id --name-only -r --diff-filter=ACMR "$sha" 2>/dev/null || true)"
        if [[ -n "$files" ]]; then
            changed="${changed}${files}"$'\n'
        fi
    done <<< "$commits"

    echo "$changed" | uniq_non_empty_lines
}

print_error_report() {
    local title="$1"
    local footer="${2:-}"
    local err=""

    if [[ ${#errors[@]} -eq 0 ]]; then
        return
    fi

    echo ""
    echo "=========================================="
    echo "  $title"
    echo "=========================================="
    echo ""
    for err in "${errors[@]}"; do
        echo "  $err"
    done
    if [[ -n "$footer" ]]; then
        echo ""
        echo "  $footer"
    fi
    echo "=========================================="
    echo ""
}
