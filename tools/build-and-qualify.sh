#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -x ./gradlew && -f gradle/wrapper/gradle-wrapper.jar ]]; then
  runner=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  runner=(gradle)
else
  echo 'ERROR: Android Gradle unavailable. Install Gradle 8.13, JDK 17 and Android SDK 36, or run GitHub Actions.' >&2
  exit 127
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" && ! -f local.properties ]]; then
  echo 'ERROR: Android SDK path is not set. Configure ANDROID_HOME or local.properties.' >&2
  exit 127
fi
"${runner[@]}" --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug
python3 tools/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
