#!/usr/bin/env bash
set -euo pipefail

TAG_PATTERN='android-v[0-9]*.[0-9]*.[0-9]*'

log_error() {
    printf '[error] %s\n' "$1" >&2
}

write_output() {
    local key="$1"
    local value="$2"
    printf '%s=%s\n' "$key" "$value"
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
    fi
}

require_git_repo() {
    git rev-parse --show-toplevel >/dev/null 2>&1 || {
        log_error "This script must run inside the LogDate git repository."
        exit 1
    }
}

resolve_base_tag() {
    if [[ "${GITHUB_REF_TYPE:-}" == "tag" ]] && [[ "${GITHUB_REF_NAME:-}" == android-v* ]]; then
        printf '%s' "$GITHUB_REF_NAME"
        return 0
    fi

    git describe --tags --match "$TAG_PATTERN" --abbrev=0 2>/dev/null || {
        log_error "No android-v<major>.<minor>.<patch> tag is reachable from HEAD. Create an initial Android release tag before enabling automated Play publishing."
        exit 1
    }
}

parse_semver() {
    local tag="$1"
    if [[ ! "$tag" =~ ^android-v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        log_error "Unsupported Android release tag '$tag'. Expected format: android-v<major>.<minor>.<patch>."
        exit 1
    fi

    MAJOR=$((10#${BASH_REMATCH[1]}))
    MINOR=$((10#${BASH_REMATCH[2]}))
    PATCH=$((10#${BASH_REMATCH[3]}))
    VERSION_NAME_BASE="${MAJOR}.${MINOR}.${PATCH}"
}

resolve_commit_distance() {
    local tag="$1"
    DISTANCE="$(git rev-list --count "${tag}..HEAD")"
    if (( DISTANCE > 999 )); then
        log_error "Found ${DISTANCE} commits since ${tag}. Cut a new android-v* tag before publishing more than 999 builds from the same base version."
        exit 1
    fi
}

resolve_version_name() {
    if (( DISTANCE == 0 )); then
        VERSION_NAME="$VERSION_NAME_BASE"
    else
        VERSION_NAME="${VERSION_NAME_BASE}-main.${DISTANCE}"
    fi
}

resolve_version_code() {
    if (( MINOR > 99 || PATCH > 99 )); then
        log_error "Minor and patch components must stay <= 99 so Play versionCode packing remains monotonic."
        exit 1
    fi

    local packed_version
    packed_version=$(( ((MAJOR * 100) + MINOR) * 100 + PATCH ))
    VERSION_CODE=$(( (packed_version * 1000) + DISTANCE ))

    if (( VERSION_CODE > 2147483647 )); then
        log_error "Resolved versionCode ${VERSION_CODE} exceeds the Android int limit."
        exit 1
    fi
}

main() {
    require_git_repo

    local base_tag
    base_tag="$(resolve_base_tag)"
    parse_semver "$base_tag"
    resolve_commit_distance "$base_tag"
    resolve_version_name
    resolve_version_code

    write_output "base_tag" "$base_tag"
    write_output "distance" "$DISTANCE"
    write_output "version_name" "$VERSION_NAME"
    write_output "version_code" "$VERSION_CODE"
}

main "$@"
