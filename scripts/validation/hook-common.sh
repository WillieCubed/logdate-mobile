#!/usr/bin/env bash
# Shared helpers for git hook scripts.

ZERO_SHA="0000000000000000000000000000000000000000"
VALID_TYPES="feat|fix|refactor|docs|style|test|chore|perf"

MODULE_TASKS_CACHE_KEYS=()
MODULE_TASKS_CACHE_VALUES=()
RESOLVED_TEST_TASKS=()
UNRESOLVED_TEST_MODULES=()

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

fetch_module_tasks() {
    local module="$1"
    ./gradlew "${module}:tasks" --all --quiet 2>/dev/null || true
}

module_tasks_cache_get_or_load() {
    local module="$1"
    local idx=""
    local fetched_tasks=""

    for idx in "${!MODULE_TASKS_CACHE_KEYS[@]}"; do
        if [[ "${MODULE_TASKS_CACHE_KEYS[$idx]}" == "$module" ]]; then
            printf '%s\n' "${MODULE_TASKS_CACHE_VALUES[$idx]}"
            return 0
        fi
    done

    fetched_tasks="$(fetch_module_tasks "$module")"
    MODULE_TASKS_CACHE_KEYS+=("$module")
    MODULE_TASKS_CACHE_VALUES+=("$fetched_tasks")
    printf '%s\n' "$fetched_tasks"
}

module_has_task() {
    local tasks_output="$1"
    local task_name="$2"

    echo "$tasks_output" | grep -Eq "^[[:space:]]*${task_name}([[:space:]-]|$)"
}

resolve_test_task_for_module() {
    local module="$1"
    local coverage_mode="${2:-balanced}"
    local module_tasks=""
    local task=""
    local -a candidate_tasks=()

    case "$coverage_mode" in
        balanced)
            candidate_tasks=("test" "testDebugUnitTest" "jvmTest" "allTests")
            ;;
        *)
            return 1
            ;;
    esac

    module_tasks="$(module_tasks_cache_get_or_load "$module")"
    for task in "${candidate_tasks[@]}"; do
        if module_has_task "$module_tasks" "$task"; then
            printf '%s:%s\n' "$module" "$task"
            return 0
        fi
    done

    return 1
}

resolve_test_tasks_for_modules() {
    local coverage_mode="$1"
    shift
    local module=""
    local resolved_task=""

    RESOLVED_TEST_TASKS=()
    UNRESOLVED_TEST_MODULES=()

    for module in "$@"; do
        [[ -z "$module" ]] && continue
        if resolved_task="$(resolve_test_task_for_module "$module" "$coverage_mode")"; then
            RESOLVED_TEST_TASKS+=("$resolved_task")
        else
            UNRESOLVED_TEST_MODULES+=("$module")
        fi
    done
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
