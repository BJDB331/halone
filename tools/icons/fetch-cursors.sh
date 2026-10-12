#!/usr/bin/env bash
# Fetches the pointer images the compositor draws: the Beige theme of
# Material Bibata (MIT, after Bibata, GPL-3.0), rasterised to PNG with
# their hotspots, into the app's assets. Needs rsvg-convert. Run it again
# after changing the list below:
#
#   tools/icons/fetch-cursors.sh

set -euo pipefail

VERSION=v1.5.0
THEME=Bibata-Material-Beige
SIZE=128
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/android/app/src/main/assets/cursors"

# CSS cursor names, as the release names its folders.
CURSORS=(
  default pointer text vertical-text crosshair grab grabbing not-allowed all-scroll
  ew-resize ns-resize nwse-resize nesw-resize help wait progress zoom-in zoom-out
  context-menu copy alias cell
)

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

curl -sfL -o "$work/release.tar.gz" \
  "https://github.com/SakibShahariar/material-bibata-cursor/releases/download/$VERSION/bibata-material-dark-$VERSION.tar.gz"
tar xzf "$work/release.tar.gz" -C "$work" "bibata-material-dark-$VERSION/$THEME"
source="$work/bibata-material-dark-$VERSION/$THEME/cursors_scalable"

rm -rf "$OUT"
mkdir -p "$OUT"
manifest="{"
for name in "${CURSORS[@]}"; do
  dir="$source/$name"
  svg="$(find -L "$dir" -name '*.svg' | sort | head -1)"
  rsvg-convert -w "$SIZE" -h "$SIZE" "$svg" -o "$OUT/$name.png"
  hotspot="$(python3 -I -c '
import json, sys
entry = json.load(open(sys.argv[1]))[0]
size = entry["nominal_size"]
print("[%.4f, %.4f]" % (entry["hotspot_x"] / size, entry["hotspot_y"] / size))
' "$dir/metadata.json")"
  manifest="$manifest\"$name\": $hotspot, "
  echo "wrote $name.png, hotspot $hotspot"
done
printf '%s\n' "${manifest%, }}" > "$OUT/hotspots.json"
echo "wrote hotspots.json"
cat > "$OUT/LICENSE" <<EOT
The cursor images in this folder are not covered by Halone's MIT License.

They are the $THEME theme of Material Bibata Cursor $VERSION,
rasterised to PNG. Material Bibata recolours Bibata Cursor, so the artwork
is licensed under the GNU General Public License, version 3 or later; the
license text ships in the system apps as licenses/GPL-3.0.txt. The SVG
sources are at
https://github.com/SakibShahariar/material-bibata-cursor/releases/tag/$VERSION
and tools/icons/fetch-cursors.sh rebuilds these images from them.
EOT
echo "wrote LICENSE"
