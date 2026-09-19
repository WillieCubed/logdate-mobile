#!/usr/bin/env bash
# Publish switches for publish-android-play.yml. Production promotion only
# with --enable-play-production. Repository-level, because job-level `if:` is
# evaluated before environment variables load.

play_publishing_describe() {
    if [[ "$ENABLE_PLAY_PRODUCTION" == "true" ]]; then
        printf 'Every push to main publishes to Play internal; android-v* tags promote to production'
    else
        printf 'Every push to main publishes to the Play internal track'
    fi
}

play_publishing_requires() {
    printf 'play-keystore play-first-bundle play-release-tag'
}

play_publishing_variable() {
    gh variable get "$1" 2>/dev/null || true
}

play_publishing_check() {
    local drift=0
    if [[ "$(play_publishing_variable LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED)" != "true" ]]; then
        printf 'Internal publishing is off (LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED)\n'
        drift=1
    fi
    if [[ "$ENABLE_PLAY_PRODUCTION" == "true" && \
        "$(play_publishing_variable LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED)" != "true" ]]; then
        printf 'Production promotion is off (LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED)\n'
        drift=1
    fi
    return "$drift"
}

play_publishing_apply() {
    gh variable set LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED --body true
    log_info "Internal publishing on"
    if [[ "$ENABLE_PLAY_PRODUCTION" == "true" ]]; then
        gh variable set LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED --body true
        log_info "Production promotion on"
    fi
}
