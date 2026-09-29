#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
validator="$root/scripts/check-play-rollout-transition.sh"
fixture="$(mktemp)"
trap 'rm -f "$fixture"' EXIT

check() {
    local expected="$1" stage="$2"
    if PLAY_TRACK_JSON_FILE="$fixture" "$validator" studio.hypertext.logdate 42 "$stage" >/dev/null 2>&1; then
        [[ "$expected" == pass ]] || { echo "Unexpectedly allowed $stage" >&2; exit 1; }
    else
        [[ "$expected" == fail ]] || { echo "Unexpectedly blocked $stage" >&2; exit 1; }
    fi
}

printf '{"releases":[{"versionCodes":["41"],"status":"completed"}]}' > "$fixture"
check pass start-5
check fail advance-25

printf '{"releases":[{"versionCodes":["42"],"status":"inProgress","userFraction":0.05}]}' > "$fixture"
check fail start-5
check pass advance-25
check fail complete-100
check pass halt

printf '{"releases":[{"versionCodes":["42"],"status":"inProgress","userFraction":0.25}]}' > "$fixture"
check pass complete-100
check pass halt

printf '{"releases":[{"versionCodes":["42"],"status":"halted","userFraction":0.05}]}' > "$fixture"
check pass resume-5
check fail resume-25
check fail advance-25

printf '{"releases":[{"versionCodes":["42"],"status":"halted","userFraction":0.25}]}' > "$fixture"
check pass resume-25
check fail resume-5

printf '{"releases":[{"versionCodes":["43"],"status":"inProgress","userFraction":0.05}]}' > "$fixture"
check fail start-5
check fail halt

echo 'Play rollout transitions passed'
