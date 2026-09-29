#!/usr/bin/env bash
# Confirms that an exact tested version code is on the Play internal track in
# a release built from the candidate commit, found by short SHA in its name.
#
#   PLAY_ACCESS_TOKEN=... ./scripts/resolve-play-internal-release.sh PACKAGE SHORT_SHA VERSION_CODE
set -euo pipefail

# shellcheck source=scripts/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
# shellcheck source=scripts/lib/google-api.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/google-api.sh"

[[ $# -eq 3 ]] || die "usage: $0 PACKAGE SHORT_SHA VERSION_CODE"
package="$1"
short_sha="$2"
expected_version_code="$3"
[[ "$expected_version_code" =~ ^[0-9]+$ ]] || die "Version code must be numeric."
[[ -n "${PLAY_ACCESS_TOKEN:-}" ]] || die "PLAY_ACCESS_TOKEN must hold an androidpublisher access token."
export GOOGLE_API_TOKEN="$PLAY_ACCESS_TOKEN"

edit="$(google_api POST "$PLAY_API/applications/$package/edits" '{}' | jq -r '.id')"
track="$(google_api GET "$PLAY_API/applications/$package/edits/$edit/tracks/internal")" || track=""
google_api DELETE "$PLAY_API/applications/$package/edits/$edit" >/dev/null || true
[[ -n "$track" ]] || die "Could not read the internal track for $package."

version_code="$(jq -r --arg sha "$short_sha" --arg code "$expected_version_code" '
    [.releases[]? | select((.name // "") | contains($sha)) | .versionCodes[]? | select(. == $code)]
    | if length == 1 then .[0] else empty end
' <<< "$track")"
[[ -n "$version_code" ]] \
    || die "No internal release of $package matches commit $short_sha and tested version $expected_version_code."

printf 'version_code=%s\n' "$version_code"
[[ -z "${GITHUB_OUTPUT:-}" ]] || printf 'version_code=%s\n' "$version_code" >> "$GITHUB_OUTPUT"
