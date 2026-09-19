#!/usr/bin/env bash
# ./run setup — one idempotent entrypoint for machine setup and deployment prep.
# See docs/reference/project-setup.md for the step catalogue.
set -euo pipefail

SETUP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/lib/common.sh
source "$SETUP_DIR/../lib/common.sh"
# shellcheck source=scripts/setup/runner.sh
source "$SETUP_DIR/runner.sh"

LOCAL_STEPS=(tools auth hooks local-properties firebase-configs test-deps)
ENVIRONMENT_STEPS=(gh-environment signing-keys firebase-apps firebase-ci-config)
PLAY_STEPS=(play-service-account play-access play-keystore play-release-tag play-first-bundle play-publishing)

TARGET="local"
ONLY=""
SKIP=""
LIST=false
ENABLE_PLAY_PRODUCTION=false

print_usage() {
    cat <<'EOF'
Usage: ./run setup [local|staging|production] [options]

  local (default)   set up this machine and worktree
  staging           local, then prepare the staging environment
  production        local, then prepare production, including Google Play

Options:
  --check                    report drift without changing anything
  --only a,b                 run only these steps
  --skip a,b                 skip these steps
  --list                     list the steps for the target and exit
  --non-interactive          never prompt or wait; manual steps are reported
  --enable-play-production   also turn on promotion to the Play production track

Exit status: 0 all ok, 1 drift or failure, 3 waiting on a manual action.
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            local|staging|production) TARGET="$1"; shift ;;
            --check) CHECK_ONLY=true; shift ;;
            --only) ONLY="${2:?--only needs a value}"; shift 2 ;;
            --skip) SKIP="${2:?--skip needs a value}"; shift 2 ;;
            --list) LIST=true; shift ;;
            --non-interactive) NON_INTERACTIVE=true; shift ;;
            --enable-play-production) ENABLE_PLAY_PRODUCTION=true; shift ;;
            -h|--help) print_usage; exit 0 ;;
            *) die "Unknown argument: $1 (see ./run setup --help)" ;;
        esac
    done
    export CHECK_ONLY NON_INTERACTIVE ENABLE_PLAY_PRODUCTION
}

load_environment() {
    [[ "$TARGET" == "local" ]] && return 0
    local file="$SETUP_DIR/environments/$TARGET.conf"
    [[ -f "$file" ]] || die "No environment definition at $file."
    set -a
    # shellcheck source=/dev/null
    source "$file"
    set +a
}

selected_steps() {
    local steps=("${LOCAL_STEPS[@]}") id
    if [[ "$TARGET" != "local" ]]; then
        steps+=("${ENVIRONMENT_STEPS[@]}")
        [[ "${PLAY_PUBLISHING:-false}" == "true" ]] && steps+=("${PLAY_STEPS[@]}")
    fi
    for id in "${steps[@]}"; do
        [[ -n "$ONLY" && ",$ONLY," != *",$id,"* ]] && continue
        [[ ",$SKIP," == *",$id,"* ]] && continue
        printf '%s\n' "$id"
    done
}

list_steps() {
    local id
    while IFS= read -r id; do
        load_step "$id"
        printf '  %-22s %s\n' "$id" "$("$(step_fn "$id")_describe")"
    done < <(selected_steps)
}

main() {
    parse_args "$@"
    load_environment
    export SETUP_TARGET="$TARGET"
    cd "$LOGDATE_REPO_ROOT"

    if [[ "$LIST" == "true" ]]; then
        list_steps
        return 0
    fi

    # An array, so step prompts read the terminal rather than this list.
    local id steps=()
    while IFS= read -r id; do
        steps+=("$id")
    done < <(selected_steps)
    [[ ${#steps[@]} -gt 0 ]] || die "No steps selected."
    for id in "${steps[@]}"; do
        run_step "$id"
    done

    print_summary
    local code=0
    summary_exit_code || code=$?
    return "$code"
}

main "$@"
