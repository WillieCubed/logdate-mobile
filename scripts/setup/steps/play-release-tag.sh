#!/usr/bin/env bash
# resolve-android-play-version.sh needs an android-v<x.y.z> tag.

play_release_tag_describe() {
    printf 'An android-v<major>.<minor>.<patch> tag is reachable from origin/main'
}

play_release_tag_requires() {
    printf 'auth'
}

play_release_tag_reachable() {
    git describe --tags --match 'android-v[0-9]*.[0-9]*.[0-9]*' --abbrev=0 origin/main >/dev/null 2>&1
}

play_release_tag_check() {
    play_release_tag_reachable && return 0
    printf 'No android-v* tag is reachable from origin/main, so Play builds have no version\n'
    return 1
}

play_release_tag_apply() {
    local tag="android-v0.1.0"
    git fetch --quiet --tags origin main || return 1
    play_release_tag_reachable && return 0
    if ! confirm "Tag origin/main as $tag and push it?"; then
        printf 'Create the first Android release tag, then re-check:\n'
        printf '  git tag -a %s origin/main -m "Android %s" && git push origin %s\n' "$tag" "${tag#android-v}" "$tag"
        return 2
    fi
    git tag -a "$tag" origin/main -m "Android ${tag#android-v}" || return 1
    git push origin "$tag"
}
