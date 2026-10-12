#!/bin/sh
# Regenerates sample-files/, the simulator's shared storage. Needs ImageMagick and ffmpeg.
set -eu
out="$(cd "$(dirname "$0")/.." && pwd)/sample-files"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
font=/System/Library/Fonts/Supplemental/Arial.ttf
[ -f "$font" ] || font=DejaVu-Sans
rm -rf "$out"
mkdir -p "$out/Pictures/Screenshots" "$out/DCIM/Camera" "$out/Movies" "$out/Music" "$out/Downloads" "$out/Documents"

magick -size 2400x1600 gradient:'#0b1d3a-#3a7bd5' -fill '#ffffff22' -draw 'circle 1700,500 1700,800' \
  -fill white -font "$font" -pointsize 120 -gravity south -annotate +0+120 'Aurora' "$out/Pictures/aurora.jpg"
magick -size 1600x2400 plasma:'#2c5364-#203a43' -blur 0x6 -fill white -font "$font" -pointsize 110 -gravity center -annotate +0+0 'Portrait' "$out/Pictures/portrait.jpg"
magick -size 1280x1280 pattern:checkerboard -scale 400% -fill '#ff7a59' -draw 'circle 640,640 640,320' "$out/Pictures/checker.png"
magick -size 1920x1080 xc:'#111822' -fill '#5ad1ff' -font "$font" -pointsize 64 -gravity northwest -annotate +80+80 'Screenshot' "$out/Pictures/Screenshots/screenshot.png"
for n in 1 2 3; do
  magick -size 3000x2000 plasma:fractal -seed "$n" -blur 0x3 -fill white -font "$font" -pointsize 140 -gravity southeast -annotate +100+100 "IMG_000$n" "$out/DCIM/Camera/IMG_000$n.jpg"
done

# An equirectangular frame labelled at each compass point, with a grid every 30 degrees.
{
  i=0
  while [ $i -le 12 ]; do echo "line $((i * 4096 / 12)),0 $((i * 4096 / 12)),2048"; i=$((i + 1)); done
  for y in 341 683 1024 1365 1707; do echo "line 0,$y 4096,$y"; done
} > "$work/grid.mvg"
magick -size 4096x2048 gradient:'#0d2a4a-#08121f' -stroke '#5ad1ff88' -strokewidth 4 -fill none -draw "@$work/grid.mvg" \
  -stroke none -fill white -font "$font" -pointsize 160 -gravity center \
  -annotate +0+0 'FRONT' -annotate +1024+0 'RIGHT' -annotate -1024+0 'LEFT' -annotate +1940+0 'BACK' -annotate -1940+0 'BACK' \
  -pointsize 110 -annotate +0-800 'UP' -annotate +0+800 'DOWN' "$work/equirect.png"
cp "$work/equirect.png" "$out/Pictures/panorama_360.png"
ffmpeg -loglevel error -loop 1 -i "$work/equirect.png" -f lavfi -i color=c=yellow:s=96x96 -t 8 -r 30 \
  -filter_complex "[0]scale=3840:1920[bg];[bg][1]overlay=x='mod(t*480,W)':y=H*0.6:shortest=1" \
  -c:v libx264 -pix_fmt yuv420p -crf 30 -preset veryfast "$out/Movies/compass_360.mp4"

# Half side-by-side: the disc sits nearer than the frame, so each eye sees it shifted inwards.
for eye in left right; do
  if [ "$eye" = left ]; then dx=40; else dx=-40; fi
  magick -size 1920x1080 xc:'#0a0f18' -stroke '#5ad1ff' -strokewidth 8 -fill none -draw 'rectangle 160,120 1760,960' \
    -stroke none -fill '#ff7a59' -draw "circle $((960 + dx)),540 $((960 + dx)),760" \
    -fill white -font "$font" -pointsize 90 -gravity north -annotate +0+40 "$eye eye" "$work/$eye.png"
done
magick "$work/left.png" "$work/right.png" +append -resize 1920x1080! -quality 88 "$out/Pictures/depth_test_SBS.jpg"
for eye in left right; do magick "$work/$eye.png" -resize 640x480! -quality 88 "$work/$eye.jpg"; done
python3 "$(dirname "$0")/make-mpo.py" "$work/left.jpg" "$work/right.jpg" "$out/Pictures/depth_test.mpo"
magick "$work/right.jpg" "$work/left.jpg" +append -quality 88 "jpg:$out/Pictures/depth_test.jps"
ffmpeg -loglevel error -loop 1 -i "$work/left.png" -loop 1 -i "$work/right.png" -t 6 -r 30 \
  -filter_complex "[0]scale=960:1080[l];[1]scale=960:1080[r];[l][r]hstack" \
  -c:v libx264 -pix_fmt yuv420p -crf 28 -preset veryfast "$out/Movies/depth_test_SBS.mp4"

ffmpeg -loglevel error -f lavfi -i testsrc2=size=1280x720:rate=30:duration=12 -f lavfi -i sine=frequency=440:duration=12 \
  -c:v libx264 -pix_fmt yuv420p -crf 30 -preset veryfast -c:a aac -shortest "$out/Movies/test_pattern.mp4"
ffmpeg -loglevel error -f lavfi -i sine=frequency=330:duration=5 -c:a libmp3lame -q:a 6 "$out/Music/tone.mp3"

printf 'Files dropped here appear in the simulator as /Downloads.\n' > "$out/Downloads/readme.txt"
printf '# Notes\n\nThe simulator serves this folder as shared storage; set HALONE_FILES to use another.\n' > "$out/Documents/notes.md"
du -sh "$out"
