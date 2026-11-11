#!/usr/bin/env bash
# Compiles actual libmpv/LibVLC Kotlin adapters against fake library signatures.
# This is NOT Android build, JNI load, or API compatibility verification.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
kotlinc \
  app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackEngine.kt \
  app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackPolicy.kt \
  app/src/main/java/com/vaydelux/bousyplayer/engine/MpvEngine.kt \
  app/src/main/java/com/vaydelux/bousyplayer/engine/VlcEngine.kt \
  tools/native-smoke/*.kt \
  -nowarn -include-runtime -d "$work/native.jar"
java -jar "$work/native.jar"
