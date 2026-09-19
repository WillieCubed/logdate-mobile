#!/usr/bin/env bash
# Runs setup steps. Source it after scripts/lib/common.sh.
#
# A step is scripts/setup/steps/<id>.sh defining, with <fn> = <id> with dashes
# turned into underscores:
#
#   <fn>_describe   one line saying what the step ensures
#   <fn>_check      read-only. Prints why when not ok, then returns
#                     0  ok
#                     1  drift that <fn>_apply can fix
#                     2  needs a person; prints the exact action
#   <fn>_apply      idempotent fix for drift. Returns 0, or 2 plus the action
#                   when it has done what it can and a person must finish
#   <fn>_requires   optional: ids of steps that must be ok first
#
# Results: ok, applied, drift (--check), failed, manual, blocked.

SETUP_STEPS_DIR="${SETUP_STEPS_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/steps}"
CHECK_ONLY="${CHECK_ONLY:-false}"

RESULT_IDS=()
RESULT_STATUS=()
RESULT_NOTES=()

step_fn() {
    printf '%s' "${1//-/_}"
}

load_step() {
    local id="$1"
    local file="$SETUP_STEPS_DIR/$id.sh"
    [[ -f "$file" ]] || die "Unknown setup step '$id' ($file is missing)."
    # shellcheck source=/dev/null
    source "$file"
}

record_result() {
    RESULT_IDS+=("$1")
    RESULT_STATUS+=("$2")
    RESULT_NOTES+=("$3")
}

status_of() {
    local id="$1" i
    [[ ${#RESULT_IDS[@]} -gt 0 ]] || { printf 'absent'; return 0; }
    for i in "${!RESULT_IDS[@]}"; do
        [[ "${RESULT_IDS[$i]}" == "$id" ]] && { printf '%s' "${RESULT_STATUS[$i]}"; return 0; }
    done
    printf 'absent'
}

unmet_requirement() {
    local fn="$1" dep status
    declare -F "${fn}_requires" >/dev/null || return 1
    for dep in $("${fn}_requires"); do
        status="$(status_of "$dep")"
        case "$status" in
            ok|applied|absent) ;;
            *) printf '%s' "$dep"; return 0 ;;
        esac
    done
    return 1
}

# Sets CHECK_NOTE to the check's output and returns its code.
run_check() {
    local fn="$1" code=0
    CHECK_NOTE="$("${fn}_check" 2>&1)" || code=$?
    return "$code"
}

# Returns 0 once the check passes, 1 if the user skips.
wait_for_manual() {
    local fn="$1" reply code
    while true; do
        read -rp "  Press Enter once done to re-check, or type s to skip: " reply || return 1
        [[ "$reply" == "s" || "$reply" == "S" ]] && return 1
        code=0
        run_check "$fn" || code=$?
        [[ "$code" -eq 0 ]] && return 0
        printf '%s\n' "$CHECK_NOTE" | sed 's/^/  /'
    done
}

run_step() {
    local id="$1" fn code dep apply_note
    load_step "$id"
    fn="$(step_fn "$id")"
    log_phase "$id — $("${fn}_describe")"

    if dep="$(unmet_requirement "$fn")"; then
        log_warn "Skipped: needs '$dep' first."
        record_result "$id" blocked "needs $dep"
        return 0
    fi

    code=0
    run_check "$fn" || code=$?
    case "$code" in
        0)
            log_info "ok"
            record_result "$id" ok ""
            return 0
            ;;
        1)
            printf '%s\n' "$CHECK_NOTE" | sed 's/^/  /'
            if [[ "$CHECK_ONLY" == "true" ]]; then
                record_result "$id" drift "$(printf '%s' "$CHECK_NOTE" | head -1)"
                return 0
            fi
            ;;
        2)
            handle_manual "$id" "$fn" "$CHECK_NOTE"
            return 0
            ;;
        *)
            printf '%s\n' "$CHECK_NOTE" | sed 's/^/  /'
            record_result "$id" failed "check exited $code"
            return 0
            ;;
    esac

    # tee shows progress live; pipefail keeps apply's exit status, not tee's.
    code=0
    apply_note="$("${fn}_apply" 2>&1 | tee /dev/stderr)" || code=$?
    if [[ "$code" -eq 2 ]]; then
        handle_manual "$id" "$fn" "$(printf '%s' "$apply_note" | tail -5)"
        return 0
    fi
    if [[ "$code" -ne 0 ]]; then
        record_result "$id" failed "apply exited $code"
        return 0
    fi

    code=0
    run_check "$fn" || code=$?
    if [[ "$code" -eq 0 ]]; then
        log_info "applied"
        record_result "$id" applied ""
    elif [[ "$code" -eq 2 ]]; then
        handle_manual "$id" "$fn" "$CHECK_NOTE"
    else
        printf '%s\n' "$CHECK_NOTE" | sed 's/^/  /'
        record_result "$id" failed "still drifted after apply"
    fi
}

handle_manual() {
    local id="$1" fn="$2" note="$3"
    printf '%b  Action needed:%b\n' "$C_YELLOW" "$C_RESET"
    printf '%s\n' "$note" | sed 's/^/    /'
    if [[ "$NON_INTERACTIVE" != "true" && "$CHECK_ONLY" != "true" ]] && wait_for_manual "$fn"; then
        log_info "ok"
        record_result "$id" ok ""
        return 0
    fi
    record_result "$id" manual "$(printf '%s' "$note" | head -1)"
}

print_summary() {
    local i status symbol
    log_phase "Summary"
    [[ ${#RESULT_IDS[@]} -gt 0 ]] || return 0
    for i in "${!RESULT_IDS[@]}"; do
        status="${RESULT_STATUS[$i]}"
        case "$status" in
            ok) symbol="${C_GREEN}✓${C_RESET}" ;;
            applied) symbol="${C_GREEN}↻${C_RESET}" ;;
            manual|blocked) symbol="${C_YELLOW}✋${C_RESET}" ;;
            *) symbol="${C_RED}✗${C_RESET}" ;;
        esac
        printf '  %b %-22s %-8s %s\n' "$symbol" "${RESULT_IDS[$i]}" "$status" "${RESULT_NOTES[$i]}"
    done
}

# 0 all ok, 1 drift or failure, 3 only manual actions remain.
summary_exit_code() {
    local status waiting=false
    [[ ${#RESULT_STATUS[@]} -gt 0 ]] || return 0
    for status in "${RESULT_STATUS[@]}"; do
        case "$status" in
            ok|applied) ;;
            manual|blocked) waiting=true ;;
            *) return 1 ;;
        esac
    done
    [[ "$waiting" == "true" ]] && return 3
    return 0
}
