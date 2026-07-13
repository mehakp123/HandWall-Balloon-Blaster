#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/hand_landmarker.task"
mkdir -p "$(dirname "$DEST")"
curl --fail --location --retry 3 \
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task" \
  --output "$DEST"
SIZE=$(wc -c < "$DEST")
if [ "$SIZE" -lt 1000000 ]; then
  echo "Model download is unexpectedly small: $SIZE bytes" >&2
  exit 1
fi
echo "Downloaded MediaPipe hand model: $DEST ($SIZE bytes)"
