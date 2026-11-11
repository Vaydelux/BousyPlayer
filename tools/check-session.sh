#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
kotlinc \
  app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackPolicy.kt \
  app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt \
  tools/session-smoke/*.kt \
  -nowarn -include-runtime -d "$work/session.jar"
java -jar "$work/session.jar"
