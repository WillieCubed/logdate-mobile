#!/usr/bin/env bash
# Prints version_code=<n> for the Play internal-track release built from a
# commit, found by the short SHA in its release name. Production promotion
# uses it, because Play (not git) assigns version codes.
#
#   PLAY_ACCESS_TOKEN=... ./scripts/resolve-play-internal-release.sh PACKAGE SHORT_SHA
set -euo pipefail

# shellcheck source=scripts/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
# shellcheck source=scripts/lib/google-api.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/google-api.sh"

[[ $# -eq 2 ]] || die "usage: $0 PACKAGE SHORT_SHA"
package="$1"
short_sha="$2"
[[ -n "${PLAY_ACCESS_TOKEN:-}" ]] || die "PLAY_ACCESS_TOKEN must hold an androidpublisher access token."
export GOOGLE_API_TOKEN="$PLAY_ACCESS_TOKEN"

edit="$(google_api POST "$PLAY_API/applications/$package/edits" '{}' | jq -r '.id')"
track="$(google_api GET "$PLAY_API/applications/$package/edits/$edit/tracks/internal")" || track=""
google_api DELETE "$PLAY_API/applications/$package/edits/$edit" >/dev/null || true
[[ -n "$track" ]] || die "Could not read the internal track for $package."

version_code="$(jq -r --arg sha "$short_sha" '
    [.releases[]? | select((.name // "") | contains($sha)) | .versionCodes[]?]
    | map(tonumber) | max // empty
' <<< "$track")"
[[ -n "$version_code" ]] \
    || die "No internal release of $package was built from $short_sha. Publish that commit to internal first."

printf 'version_code=%s\n' "$version_code"
[[ -z "${GITHUB_OUTPUT:-}" ]] || printf 'version_code=%s\n' "$version_code" >> "$GITHUB_OUTPUT"
