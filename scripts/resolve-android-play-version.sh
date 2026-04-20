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

resolve_current_tag() {
    if [[ "${GITHUB_REF_TYPE:-}" == "tag" ]] && [[ "${GITHUB_REF_NAME:-}" == android-v* ]]; then
        printf '%s' "$GITHUB_REF_NAME"
        return 0
    fi

    git tag --points-at HEAD --list 'android-v*' | LC_ALL=C sort -V | tail -n 1
}

resolve_base_tag() {
    local current_tag
    current_tag="$(resolve_current_tag)"
    if [[ -n "$current_tag" ]]; then
        printf '%s' "$current_tag"
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

resolve_commit_metadata() {
    COMMIT_COUNT="$(git rev-list --count HEAD)"
    SHORT_SHA="$(git rev-parse --short=7 HEAD)"

    local hash_seed
    hash_seed="${SHORT_SHA:0:5}"
    HASH_FRAGMENT=$((16#$hash_seed % 1000))
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
    if [[ -n "$CURRENT_TAG" ]]; then
        VERSION_NAME="${CURRENT_TAG#android-v}+${SHORT_SHA}"
    else
        VERSION_NAME="${VERSION_NAME_BASE}-main.${DISTANCE}+${SHORT_SHA}"
    fi
}

resolve_version_code() {
    VERSION_CODE=$(( (COMMIT_COUNT * 1000) + HASH_FRAGMENT ))

    if (( VERSION_CODE > 2147483647 )); then
        log_error "Resolved versionCode ${VERSION_CODE} exceeds the Android int limit."
        exit 1
    fi
}

resolve_release_names() {
    INTERNAL_RELEASE_NAME="internal ${VERSION_NAME_BASE} ${SHORT_SHA}"
    if [[ -n "$CURRENT_TAG" ]]; then
        PRODUCTION_RELEASE_NAME="production ${CURRENT_TAG#android-v} ${SHORT_SHA}"
    else
        PRODUCTION_RELEASE_NAME="production ${VERSION_NAME_BASE} ${SHORT_SHA}"
    fi
}

main() {
    require_git_repo

    BASE_TAG="$(resolve_base_tag)"
    CURRENT_TAG="$(resolve_current_tag)"
    parse_semver "$BASE_TAG"
    resolve_commit_metadata
    resolve_commit_distance "$BASE_TAG"
    resolve_version_name
    resolve_version_code
    resolve_release_names

    write_output "base_tag" "$BASE_TAG"
    write_output "current_tag" "$CURRENT_TAG"
    write_output "base_version" "$VERSION_NAME_BASE"
    write_output "commit_count" "$COMMIT_COUNT"
    write_output "distance" "$DISTANCE"
    write_output "short_sha" "$SHORT_SHA"
    write_output "hash_fragment" "$HASH_FRAGMENT"
    write_output "version_name" "$VERSION_NAME"
    write_output "version_code" "$VERSION_CODE"
    write_output "internal_release_name" "$INTERNAL_RELEASE_NAME"
    write_output "production_release_name" "$PRODUCTION_RELEASE_NAME"
}

main "$@"
