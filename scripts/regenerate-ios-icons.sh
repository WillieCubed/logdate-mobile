#!/usr/bin/env bash
# Regenerate the flat fallback iOS app icon from artwork/logdate-app-icon.svg.
#
# The layered AppIcon.icon is used by current Xcode builds. Keep the asset
# catalog image in sync for older toolchains and static previews. Output is
# flat RGB (no alpha) so App Store Connect accepts the marketing icon.
#
# Tooling: macOS-native sips. No third-party dependencies required.

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
src_svg="$repo_root/artwork/logdate-app-icon.svg"
out_dir="$repo_root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [[ ! -f "$src_svg" ]]; then
  echo "Source SVG not found: $src_svg" >&2
  exit 1
fi

# Flatten alpha by round-tripping through JPEG. App Store rejects icons
# with an alpha channel even when fully opaque.
sips -s format jpeg "$src_svg" --out "$work_dir/flat.jpg" >/dev/null
sips -s format png "$work_dir/flat.jpg" --out "$out_dir/icon-1024.png" >/dev/null

echo "Wrote $out_dir/icon-1024.png"
