#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
kotlinc app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackPolicy.kt tools/PolicySmoke.kt -include-runtime -d "$work/policy.jar"
java -jar "$work/policy.jar"
