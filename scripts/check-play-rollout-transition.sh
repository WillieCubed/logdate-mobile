#!/usr/bin/env bash
# Confirm that a manual rollout action targets the tested version and follows
# the expected stage. PLAY_TRACK_JSON_FILE is used only by local contract tests.
set -euo pipefail

# shellcheck source=scripts/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
# shellcheck source=scripts/lib/google-api.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/google-api.sh"

[[ $# -eq 3 ]] || die "usage: $0 PACKAGE VERSION_CODE STAGE"
package="$1"
version_code="$2"
stage="$3"
[[ "$version_code" =~ ^[0-9]+$ ]] || die "Version code must be numeric."

if [[ -n "${PLAY_TRACK_JSON_FILE:-}" ]]; then
    track="$(cat "$PLAY_TRACK_JSON_FILE")"
else
    [[ -n "${PLAY_ACCESS_TOKEN:-}" ]] || die "PLAY_ACCESS_TOKEN is required."
    export GOOGLE_API_TOKEN="$PLAY_ACCESS_TOKEN"
    edit="$(google_api POST "$PLAY_API/applications/$package/edits" '{}' | jq -er '.id')"
    trap 'google_api DELETE "$PLAY_API/applications/$package/edits/$edit" >/dev/null || true' EXIT
    track="$(google_api GET "$PLAY_API/applications/$package/edits/$edit/tracks" \
        | jq -cer '[.tracks[]? | select(.track == "production")] | if length == 0 then {releases: []} elif length == 1 then .[0] else error("duplicate production track") end')"
fi

release="$(jq -cer --arg code "$version_code" \
    '[.releases[]? | select(any(.versionCodes[]?; . == $code))] | if length == 0 then {} elif length == 1 then .[0] else error("duplicate version code") end' \
    <<< "$track")"
status="$(jq -r '.status // "absent"' <<< "$release")"
fraction="$(jq -r '.userFraction // ""' <<< "$release")"

case "$stage" in
    start-5)
        [[ "$status" == absent ]] || die "Version $version_code is already on production ($status)."
        jq -e '[.releases[]? | select(.status == "inProgress" or .status == "halted")] | length == 0' \
            <<< "$track" >/dev/null || die "Resolve the existing staged or halted release before starting another."
        ;;
    advance-25)
        [[ "$status" == inProgress && "$fraction" == 0.05 ]] \
            || die "25% requires this version to be in progress at 5%; found $status $fraction."
        ;;
    complete-100)
        [[ "$status" == inProgress && "$fraction" == 0.25 ]] \
            || die "100% requires this version to be in progress at 25%; found $status $fraction."
        ;;
    halt)
        [[ "$status" == inProgress && ( "$fraction" == 0.05 || "$fraction" == 0.25 ) ]] \
            || die "Only this version in progress at 5% or 25% can be halted; found $status $fraction."
        ;;
    resume-5)
        [[ "$status" == halted && "$fraction" == 0.05 ]] \
            || die "Resume at 5% requires this version halted at 5%; found $status $fraction."
        ;;
    resume-25)
        [[ "$status" == halted && "$fraction" == 0.25 ]] \
            || die "Resume at 25% requires this version halted at 25%; found $status $fraction."
        ;;
    *) die "Unknown rollout stage: $stage" ;;
esac

printf 'Allowed %s for production version %s.\n' "$stage" "$version_code"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf 'current_fraction=%s\n' "$fraction" >> "$GITHUB_OUTPUT"
fi
