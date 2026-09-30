#!/usr/bin/env bash
# Render the flat macOS icon from the canonical LogDate artwork.

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
source_svg="$repo_root/artwork/logdate-app-icon.svg"
output_icns="$repo_root/app/compose-main/packaging/LogDate.icns"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [[ ! -f "$source_svg" ]]; then
  echo "Missing icon source: $source_svg" >&2
  exit 1
fi

# Clip the flat source to the macOS silhouette, retaining transparent corners.
{
  cat <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs><clipPath id="desktop-shape"><rect width="1024" height="1024" rx="112"/></clipPath></defs>
  <g clip-path="url(#desktop-shape)">
SVG
  sed '1d;$d' "$source_svg"
  cat <<'SVG'
  </g>
</svg>
SVG
} > "$work_dir/desktop-icon.svg"

sips -s format png "$work_dir/desktop-icon.svg" --out "$work_dir/icon-1024.png" >/dev/null
iconset="$work_dir/LogDate.iconset"
mkdir -p "$iconset" "$(dirname "$output_icns")"
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$work_dir/icon-1024.png" --out "$iconset/icon_${size}x${size}.png" >/dev/null
  double_size=$((size * 2))
  sips -z "$double_size" "$double_size" "$work_dir/icon-1024.png" \
    --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o "$output_icns"

echo "Wrote $output_icns"
