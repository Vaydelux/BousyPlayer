#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
kotlinc \
  app/src/main/java/com/vaydelux/bousyplayer/model/MediaSource.kt \
  tools/session-smoke/UriStub.kt \
  tools/model-smoke/*.kt \
  -include-runtime -d "$work/model.jar"
java -jar "$work/model.jar"
