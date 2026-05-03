#!/usr/bin/env bash
# Regenerate the iOS app icon from scripts/ios-icon-source.svg.
#
# The source SVG is the Android adaptive icon converted to plain SVG
# with the foreground inset 25% inside the background, matching how
# Android renders the adaptive icon at full square. Since iOS 14 the
# AppIcon asset catalog accepts a single 1024x1024 universal image —
# Xcode synthesizes every other size at build time. So we only emit
# icon-1024.png. Output is flat RGB (no alpha) so App Store Connect
# accepts the marketing icon.
#
# Tooling: macOS-native qlmanage (SVG → PNG via Quick Look's WebKit
# renderer) and sips (resize + format conversion). No third-party
# dependencies required.

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
src_svg="$repo_root/scripts/ios-icon-source.svg"
out_dir="$repo_root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [[ ! -f "$src_svg" ]]; then
  echo "Source SVG not found: $src_svg" >&2
  exit 1
fi

# qlmanage writes <input>.png next to the input, so stage the SVG inside work_dir.
cp "$src_svg" "$work_dir/icon.svg"
qlmanage -t -s 1024 -o "$work_dir" "$work_dir/icon.svg" >/dev/null

# Flatten alpha by round-tripping through JPEG. App Store rejects icons
# with an alpha channel even when fully opaque.
sips -s format jpeg "$work_dir/icon.svg.png" --out "$work_dir/flat.jpg" >/dev/null
sips -s format png "$work_dir/flat.jpg" --out "$out_dir/icon-1024.png" >/dev/null

echo "Wrote $out_dir/icon-1024.png"
